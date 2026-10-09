package org.openraffle.ui.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.openraffle.bgg.BggClient;
import org.openraffle.bgg.BggItem;
import org.openraffle.domain.Event;
import org.openraffle.domain.Prize;
import org.openraffle.domain.PrizePicture;
import org.openraffle.drive.DriveException;
import org.openraffle.drive.DriveService;
import org.openraffle.image.InvalidImageException;
import org.openraffle.image.PictureUploadHandler;
import org.openraffle.image.UploadedImage;
import org.openraffle.security.SecurityConfig;
import org.openraffle.service.EventService;
import org.openraffle.service.PrizeService;
import org.openraffle.service.QrCodeService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openraffle.ui.MainLayout;
import org.openraffle.ui.Paginator;
import org.openraffle.ui.PrizeThumbnail;
import org.openraffle.ui.ResponsiveColumns;

import java.util.List;


@Route(value = "events/:eventId", layout = MainLayout.class)
@PageTitle("Prizes | Open Raffle")
@RolesAllowed({SecurityConfig.ROLE_ORGANIZER, SecurityConfig.ROLE_ADMIN})
public class PrizesView extends VerticalLayout implements BeforeEnterObserver {

    private final PrizeService prizeService;
    private final EventService eventService;
    private final Grid<Prize> grid = new Grid<>(Prize.class, false);
    private final Paginator<Prize> pages = new Paginator<>(this::showPage);
    private List<Prize> currentPage = List.of();
    private Event event;
    /** Opens the event's login-free prize list, /e/<slug>, in a new tab. */
    private final Anchor publicList = new Anchor();

    private final QrCodeService qrCodeService;
    /**
     * The public list's QR code beside the heading, big enough for an organizer to turn the
     * screen toward someone and have them scan it; click it for a printable poster.
     */
    private final Image qr = new Image();

    private final BggClient bgg;
    private final DriveService drive;

    public PrizesView(PrizeService prizeService, EventService eventService, QrCodeService qrCodeService, BggClient bgg, DriveService drive) {
        this.prizeService = prizeService;
        this.eventService = eventService;
        this.qrCodeService = qrCodeService;
        this.bgg = bgg;
        this.drive = drive;
        setSizeFull();

        Button add = new Button("Add prize", VaadinIcon.PLUS.create(), e -> openEditor(newPrize()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        publicList.setTarget("_blank");
        publicList.getElement().setAttribute("title", "The login-free prize list to share with everyone");
        Button publicListButton = new Button("Public list", VaadinIcon.EXTERNAL_LINK.create());
        publicListButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        publicList.add(publicListButton);
        qr.setWidth("8rem");
        qr.setHeight("8rem");
        qr.getStyle().set("cursor", "pointer");
        qr.getElement().setAttribute("title", "Open a printable poster of this QR code");
        qr.getElement().setAttribute("role", "button");
        qr.getElement().setAttribute("tabindex", "0");
        qr.addClickListener(e -> UI.getCurrent().navigate(PosterView.class, PosterView.paramsFor(event)));
        HorizontalLayout title = new HorizontalLayout(new H2("Prizes"), qr);
        title.setAlignItems(Alignment.CENTER);
        HorizontalLayout toolbar = new HorizontalLayout(title, publicList, add);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(title);
        toolbar.setWidthFull();
        // On a phone the buttons drop under the heading and QR code instead of overflowing.
        toolbar.setWrap(true);

        // Alphabetical; participants rank prizes themselves on their wishlist page. The row
        // number is for readability only and keeps counting across pages. Fixed width: an
        // auto-sized column measured while the grid was collapsed once truncated it to "1…".
        grid.addColumn(prize -> pages.getPage() * pages.getPageSize() + currentPage.indexOf(prize) + 1)
                .setHeader("#").setKey("number").setWidth("4em").setFlexGrow(0);
        Grid.Column<Prize> picture = grid.addComponentColumn(prize -> {
            Image thumbnail = PrizeThumbnail.of(prize, "3rem");
            return thumbnail == null ? new Span() : thumbnail;
        }).setHeader("").setKey("picture").setWidth("4em").setFlexGrow(0);
        grid.addColumn(Prize::getName).setHeader("Name").setKey("name").setWidth("6em").setFlexGrow(2);
        Grid.Column<Prize> description = grid.addColumn(Prize::getDescription)
                .setHeader("Description").setKey("description").setWidth("8em").setFlexGrow(3);
        grid.addComponentColumn(prize -> {
            Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditor(prize));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(prize));
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(edit, delete);
            actions.setSpacing(false);
            return actions;
        }).setHeader("").setKey("actions").setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        ResponsiveColumns.hideOnNarrowScreens(grid, List.of(picture, description));
        grid.setSizeFull();

        add(toolbar, grid, pages);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent enter) {
        EventScopedView.resolve(enter, eventService).ifPresent(e -> {
            event = e;
            publicList.setHref("e/" + event.getSlug());
            qr.setSrc(qrPng(256));
            qr.setAlt("QR code for the public prize list of " + event.getName());
            refresh();
        });
    }

    private StreamResource qrPng(int sizePx) {
        Event target = event;
        return new StreamResource("prizes-" + target.getSlug() + ".png",
                () -> new ByteArrayInputStream(qrCodeService.pngFor(target, sizePx)));
    }

    /**
     * Type-ahead over BoardGameGeek, like the search box on BGG itself: two or more
     * characters bring up matching games and expansions, newest matches first. Picking one
     * fills an empty Name. Without an API token the field explains how to turn it on.
     */
    private ComboBox<BggItem> bggField(TextField name) {
        ComboBox<BggItem> game = new ComboBox<>("BoardGameGeek");
        game.setItemLabelGenerator(BggItem::label);
        game.setPlaceholder("Search BoardGameGeek…");
        game.setClearButtonVisible(true);
        game.setPageSize(20);
        game.setWidthFull();
        if (!bgg.isEnabled()) {
            game.setEnabled(false);
            game.setHelperText("Set BGG_API_KEY to look games up on BoardGameGeek.");
            return game;
        }
        game.setHelperText("Start typing a game's name; the box image is saved with the prize.");
        game.setItems(query -> {
            // Vaadin insists the offset and limit are read on every call, even an empty one.
            int offset = query.getOffset();
            int limit = query.getLimit();
            String filter = query.getFilter().orElse("").trim();
            if (filter.length() < 2) {
                return java.util.stream.Stream.empty();
            }
            return bgg.search(filter).stream().skip(offset).limit(limit);
        });
        game.addValueChangeListener(e -> {
            if (e.getValue() != null && e.isFromClient() && name.isEmpty()) {
                name.setValue(e.getValue().name());
            }
        });
        return game;
    }

    /**
     * The editor's pictures section: the prize's own pictures in order (the first is the
     * primary one) with move and remove controls, and an upload box when Google Drive is
     * connected. A new prize is saved on the first upload so the pictures have something to
     * belong to.
     */
    public class PicturesEditor extends Div {
        private final Prize[] prize;
        private final BeanValidationBinder<Prize> binder;
        private final Div list = new Div();
        private final Div uploadSlot = new Div();
        /** One instance for the dialog's life: rebuilding it would cut off uploads still in flight. */
        private final Upload upload;
        private final Span dropLabel = new Span();
        private final Span note = new Span();

        PicturesEditor(Prize[] prize, BeanValidationBinder<Prize> binder) {
            this.prize = prize;
            this.binder = binder;
            addClassName("prize-pictures");
            H4 heading = new H4("Pictures");
            heading.addClassNames(LumoUtility.Margin.Bottom.XSMALL);
            list.addClassName("prize-pictures-list");
            note.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
            // Streamed to a temporary file, never held in memory: six phone photos at once would not fit.
            upload = new Upload(new PictureUploadHandler((meta, file) -> receive(meta.fileName(), meta.contentType(), file.toPath())));
            upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/gif", ".jpg", ".jpeg", ".png", ".gif");
            upload.setMaxFileSize((int) UploadedImage.MAX_BYTES);
            upload.setDropAllowed(true);
            upload.setWidthFull();
            Button pick = new Button("Add pictures", VaadinIcon.UPLOAD.create());
            upload.setUploadButton(pick);
            upload.setDropLabel(dropLabel);
            upload.addFileRejectedListener(e -> toast(e.getErrorMessage(), NotificationVariant.LUMO_ERROR));
            upload.addFailedListener(e -> toast("Upload failed: " + e.getReason().getMessage(), NotificationVariant.LUMO_ERROR));
            upload.addAllFinishedListener(e -> upload.clearFileList());
            add(heading, list, uploadSlot);
            render();
        }

        /** For tests: an upload already in memory. */
        public void receive(String fileName, String contentType, byte[] data) {
            try {
                Path temp = Files.createTempFile("open-raffle-upload-", ".bin");
                Files.write(temp, data);
                receive(fileName, contentType, temp);
            } catch (IOException e) {
                toast("The picture could not be read.", NotificationVariant.LUMO_ERROR);
            }
        }

        /**
         * Takes an upload streamed to {@code file} (deleted here afterwards), checks and
         * stores it, and refreshes. Big batches wait their turn in the service, one at a time.
         */
        public void receive(String fileName, String contentType, Path file) {
            UI ui = UI.getCurrent();
            Runnable work = () -> {
                try {
                    if (!ensureSaved()) {
                        return;
                    }
                    prizeService.addPicture(prize[0], file, contentType, fileName);
                    reload();
                    toast("Picture added", NotificationVariant.LUMO_SUCCESS);
                } catch (InvalidImageException | DriveException ex) {
                    toast(ex.getMessage(), NotificationVariant.LUMO_ERROR);
                } catch (RuntimeException ex) {
                    toast("The picture could not be added: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
                } finally {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        // a stray temp file is the OS's problem, not the organizer's
                    }
                }
                render();
            };
            if (ui == null) {
                work.run();
            } else {
                ui.access(work::run);
            }
        }

        /** A new prize must exist before pictures can belong to it; the Name must be valid. */
        private boolean ensureSaved() {
            if (prize[0].getId() != null) {
                return true;
            }
            try {
                binder.writeBean(prize[0]);
            } catch (ValidationException e) {
                toast("Enter a name for the prize before adding pictures.", NotificationVariant.LUMO_ERROR);
                return false;
            }
            prize[0] = prizeService.save(prize[0]);
            return true;
        }

        private void reload() {
            prizeService.findById(prize[0].getId()).ifPresent(fresh -> prize[0] = fresh);
        }

        void render() {
            list.removeAll();
            List<PrizePicture> current = prize[0].getPictures();
            if (current.isEmpty()) {
                Span none = new Span(prize[0].hasBggImage()
                        ? "No pictures of your own yet; the BoardGameGeek box image is shown."
                        : "No pictures yet.");
                none.addClassNames(LumoUtility.TextColor.TERTIARY, LumoUtility.FontSize.SMALL);
                list.add(none);
            }
            for (int i = 0; i < current.size(); i++) {
                list.add(row(current.get(i), i, current.size()));
            }
            DriveService.Status status = drive.status();
            int room = Prize.MAX_PICTURES - current.size();
            if (!status.canUpload()) {
                note.setText(switch (status.state()) {
                    case NOT_CONFIGURED -> "Uploading pictures needs Google Drive, which is not set up on this server.";
                    case NOT_CONNECTED -> "Connect Google Drive under Settings to upload pictures.";
                    case UNHEALTHY -> "Google Drive is not working (" + status.connection().getLastError() + "); see Settings.";
                    case CONNECTED -> "";
                });
                show(note);
            } else if (room <= 0) {
                note.setText("This prize has the most pictures it can have (" + Prize.MAX_PICTURES + ").");
                show(note);
            } else {
                // The same Upload stays in place while a batch streams in; only its limits change.
                upload.setMaxFiles(room);
                dropLabel.setText("or drop JPEG, PNG or GIF files here (up to 10 MB each, " + room + " more)");
                show(upload);
            }
        }

        private void show(com.vaadin.flow.component.Component what) {
            if (what.getParent().filter(p -> p == uploadSlot).isEmpty()) {
                uploadSlot.removeAll();
                uploadSlot.add(what);
            }
        }

        private Div row(PrizePicture picture, int index, int count) {
            Div row = new Div();
            row.addClassName("prize-pictures-row");
            Image thumb = new Image(picture.getUrl(), prize[0].getName() + ", picture " + (index + 1));
            Span label = new Span(index == 0 ? "Primary picture" : "Picture " + (index + 1));
            if (index == 0) {
                label.getElement().getThemeList().addAll(java.util.List.of("badge", "primary"));
            }
            Div text = new Div(label);
            text.addClassName("grow");
            Button up = iconButton(VaadinIcon.ARROW_UP, "Move up", () -> move(picture, -1));
            up.setEnabled(index > 0);
            Button down = iconButton(VaadinIcon.ARROW_DOWN, "Move down", () -> move(picture, 1));
            down.setEnabled(index < count - 1);
            Button remove = iconButton(VaadinIcon.CLOSE_SMALL, "Remove picture", () -> {
                prizeService.removePicture(prize[0], picture);
                reload();
                render();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            row.add(thumb, text, up, down, remove);
            return row;
        }

        private void move(PrizePicture picture, int delta) {
            prizeService.movePicture(prize[0], picture, delta);
            reload();
            render();
        }

        private Button iconButton(VaadinIcon icon, String tooltip, Runnable action) {
            Button button = new Button(icon.create(), e -> action.run());
            button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            button.setTooltipText(tooltip);
            button.setAriaLabel(tooltip);
            return button;
        }
    }

    private static void toast(String text, NotificationVariant variant) {
        Notification.show(text, 5000, Notification.Position.BOTTOM_CENTER).addThemeVariants(variant);
    }

    /** BGG's API terms ask for their badge wherever their data is used; it sits under the lookup. */
    static Anchor poweredByBgg() {
        Image badge = new Image("img/bgg-powered-by.png", "Powered by BoardGameGeek");
        badge.setHeight("1.75rem");
        badge.addClassName("bgg-powered-by");
        Anchor link = new Anchor("https://boardgamegeek.com", badge);
        link.setTarget("_blank");
        link.getElement().setAttribute("title", "Game data and images from BoardGameGeek");
        link.addClassName("bgg-powered-by-link");
        return link;
    }

    private Prize newPrize() {
        Prize prize = new Prize();
        prize.setEvent(event);
        return prize;
    }

    private void showPage(List<Prize> prizes) {
        currentPage = prizes;
        grid.setItems(prizes);
    }

    private void refresh() {
        pages.setItems(prizeService.findAll(event));
    }

    private void openEditor(Prize initial) {
        Dialog dialog = new Dialog(initial.getId() == null ? "New prize" : "Edit prize");
        // Uploading a picture saves a brand-new prize first, so the edited instance can change.
        Prize[] prize = {initial};

        TextField name = new TextField("Name");
        TextArea description = new TextArea("Description");
        ComboBox<BggItem> game = bggField(name);

        BeanValidationBinder<Prize> binder = new BeanValidationBinder<>(Prize.class);
        binder.forField(name).asRequired("Name is required").bind(Prize::getName, Prize::setName);
        binder.forField(description).bind(Prize::getDescription, Prize::setDescription);
        binder.forField(game).bind(
                p -> p.getBggId() == null ? null : new BggItem(p.getBggId(), p.getBggName() == null ? "BGG #" + p.getBggId() : p.getBggName(), null),
                (p, item) -> {
                    p.setBggId(item == null ? null : item.id());
                    p.setBggName(item == null ? null : item.name());
                });
        binder.readBean(prize[0]);

        FormLayout form = new FormLayout(game, poweredByBgg(), name, description);
        form.setColspan(game, 2);
        form.setColspan(name, 2);
        form.setColspan(description, 2);
        dialog.add(form);
        PicturesEditor pictures = new PicturesEditor(prize, binder);
        dialog.add(pictures);
        dialog.setWidth("min(95vw, 640px)");

        Button save = new Button("Save", e -> {
            try {
                binder.writeBean(prize[0]);
                prizeService.save(prize[0]);
                dialog.close();
                refresh();
            } catch (ValidationException ex) {
                // field-level errors already shown by the binder
            }
        });
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                refresh(); // pictures may have changed even if Save was not pressed
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
    }

    private void confirmDelete(Prize prize) {
        ConfirmDialog confirm = new ConfirmDialog("Delete prize?",
                "\"" + prize.getName() + "\" will be removed from every participant's wishlist.",
                "Delete", e -> {
                    prizeService.delete(prize);
                    refresh();
                    Notification.show("Prize deleted");
                }, "Cancel", e -> {
                });
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }
}
