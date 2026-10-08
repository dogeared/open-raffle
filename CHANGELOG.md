# Changelog

All notable changes to Open Raffle are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.7.2] - 2026-10-08

### Fixed
- **Uploaded photos looked washed out.** The JDK's JPEG decoder ignores the colour profile
  phone photos carry (Display P3) and mangles Adobe and CMYK JPEGs; pictures are now
  decoded with TwelveMonkeys' JPEG reader, which applies the profile the way the camera
  meant. Saved JPEGs use quality 0.9 instead of the default 0.75.
- Portrait photos no longer come out sideways: the EXIF orientation is read before the
  metadata is dropped and baked into the pixels.

## [1.7.1] - 2026-10-08

### Fixed
- **Uploading several pictures at once ran the server out of memory** (six phone photos
  on a 512 MB instance). Uploads are now streamed to temporary files instead of being held
  in memory, pictures are processed one at a time while the rest of a batch waits its
  turn, and a big photo is decoded at a reduced resolution (every n-th pixel) rather than
  at full size before being shrunk, which cuts a 24-megapixel photo's decoding from about
  100 MB of heap to a few. `raffle.uploads.concurrent` sets how many are processed at once
  (default 1).

## [1.7.0] - 2026-10-08

### Added
- **Organizers' own prize pictures, kept in Google Drive.** Under **Settings**, an
  organizer or admin connects a Google Drive folder (OAuth consent; the app creates its own
  folder, with a subfolder per event, and can only see files it created) and can see the
  connection's state, check it, reconnect or disconnect. Once connected, anyone running an event can upload up to 10
  pictures per prize from the prize editor, order them (the first is the primary picture,
  shown as the thumbnail) and remove them. The large view has a strip of all the prize's
  pictures underneath; resting the pointer on one, or tapping it, shows it large. The
  large view keeps one fixed footprint whatever the picture's shape (each is letterboxed
  inside), so the popup never moves under the pointer. The BoardGameGeek box image, when
  there is one, comes last in the strip. A new prize is saved on its first upload.
- Pictures are cached locally and fetched from Drive again when a cached copy is missing;
  if Drive cannot supply a picture (deleted there, or the connection is unhealthy) the
  prize's BoardGameGeek image is shown instead, in the browser as well as on the server.
- Uploads are hardened per the OWASP File Upload Cheat Sheet: allow-listed names and
  types, image signatures and decoding verified, dimensions checked from the header, 10 MB
  per file, 60 uploads per user per 10 minutes, re-encoding that strips metadata and
  hidden payloads, app-minted file names outside the web root, and access checks on every
  change. The README lists the measures.
- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` configure the Google OAuth client;
  `render.yaml` prompts for them.

### Security
- PostgreSQL JDBC driver 42.7.13 → 42.7.14 (SNYK-JAVA-ORGPOSTGRESQL-20571163,
  SNYK-JAVA-ORGPOSTGRESQL-20571178).

### Roadmap
- An admin wrap-up for a finished event: ZIP of all its pictures to download, then delete
  the event and its picture folder (see the README).

## [1.6.2] - 2026-10-07

### Added
- **BGG rating on the picture.** The full-size picture carries BoardGameGeek's community
  rating in its lower right corner, in the colours BGG's own game pages use for their
  rating box (deep green for 9–10, green for 8, blue for 7, slate blue for 5–6, red for
  3–4, dark red for 1–2; grey when too few people have rated the game for it to be ranked)
  and linking to the game's BGG page. The rating is cached with the prize when the game is
  linked and refreshed once it is a month old; ratings move slowly.
- **Hover to peek.** Resting the pointer on a thumbnail shows the full-size picture, which
  stays up while the pointer is on the thumbnail or on the picture itself and goes away
  when it leaves; clicking keeps it open with a Close button, as before.

- **Ratings keep themselves current.** A background job (a few times a day, pausing between
  calls) fetches the rating of every linked prize whose rating is missing or a month old,
  so games linked before ratings existed, or while BGG was down, get theirs without an
  edit.
- BoardGameGeek's "Powered by BGG" badge sits under the lookup field in the prize editor,
  as BGG's API terms require; the image ships with the app.

### Changed
- Thumbnails on the organizer's prizes grid are the same size as on the public prize list.

## [1.6.1] - 2026-10-07

### Changed
- Prize pictures are clickable everywhere they appear (prizes grid, editor, public prize
  list, wishlist): the thumbnail opens the full-size picture in a dialog, as large as the
  window allows. Close it with the button, Escape or a click outside.

## [1.6.0] - 2026-10-07

### Added
- **BoardGameGeek lookup in the prize editor.** A type-ahead field searches BGG as you
  type (games and expansions, exact and prefix matches first, like BGG's own search box).
  Picking a game fills in an empty prize name, saves the BGG id with the prize, and
  downloads the game's box image once into `RAFFLE_IMAGES_DIR`. Pictures show as
  thumbnails on the prizes grid (not on phones), the public prize list and participants'
  wishlists, and in the editor. Unlinking the game removes the picture; deleting the prize
  does too. Needs `BGG_API_KEY` (a token for an application registered at
  boardgamegeek.com/applications); without it the field is disabled and says so.
- Pictures are served at `/images/<name>` without login. Names are minted by the app and
  validated on every request, downloads are checked to really be JPEG, PNG, GIF or WebP
  and capped at 8 MB, and BGG's XML is parsed with DOCTYPEs and external entities off.
- The pictures directory is a cache: a picture whose file has gone missing (Render resets
  the filesystem on every deploy, and the blueprint attaches no disk, keeping deploys
  zero-downtime and free of disk charges) is fetched from BoardGameGeek again the first
  time it is viewed. When BGG's full-size image is unusable, its thumbnail is used.
- `render.yaml` prompts for `BGG_API_KEY`.

### Roadmap
- Per-event external picture storage (a connected Google Drive folder, with a connect /
  break / reconnect flow and a fallback to the BGG API when a picture is missing or the
  connection is unhealthy).
- 1.7.0: organizers upload their own prize photos, hardened per the OWASP File Upload
  Cheat Sheet (see the README).

## [1.5.1] - 2026-10-07

### Changed
- The QR code beside the Prizes heading is bigger, so an organizer can turn the screen
  toward someone and have them scan it straight away. On a phone the page's buttons drop
  under the heading instead of running off the screen.
- Clicking that QR code opens a **printable poster** instead of a dialog: a Letter-sized
  page with the event name on top (on one line, sized to fit), the QR code in the middle
  and "Scan for available prizes!" underneath, everything centered. Print it from the
  browser, or download the QR code as a PNG.

## [1.5.0] - 2026-10-07

### Added
- **Public prize list.** Every active event has a login-free page at `/e/<event-name>`
  showing its prizes the way participants see them on the wishlist page, with the same
  pagination but nothing to pick; prizes already handed out carry a quiet "Claimed" tag.
  The URL uses the event's name, not its id: lowercased, words joined by single dashes,
  accents folded and other characters dropped, so "Carnage & Fun 29" lives at
  `/e/carnage-fun-29`. Deleted events are not found there. A **Public list** button on
  the Prizes page opens it in a new tab, and a QR code beside the Prizes heading opens a
  large, downloadable one to put on a screen or a sign at the event.

### Changed
- An event name must have at least one letter or digit, and two active events cannot
  share a public link (`Carnage & Fun 29` and `Carnage Fun 29`), so every event's public
  page resolves to exactly one raffle.

### Security
- Embedded Tomcat 11.0.25 → 11.0.26 (SNYK-JAVA-ORGAPACHETOMCATEMBED-20552494, an
  authentication bypass in the WebSocket module; the app does not use WebSockets, but the
  dependency is updated all the same).

## [1.4.3] - 2026-10-04

### Fixed
- On the draw page the Look up button sat low, level with the helper text under the ticket
  field. It now sits beside the input box.

## [1.4.2] - 2026-10-04

### Changed
- The Reports page sorts by who took the prize, A to Z, when it opens. The Prize and
  Claimed by column headers switch between ascending and descending, and the order applies
  to the whole report, not just the page on screen. The other column breaks ties, then the
  most recent claim.

## [1.4.1] - 2026-10-04

### Changed
- Phone numbers are shown as "(555) 123-4567" wherever they appear (the draw result, the
  Reports page, the participant editor) when they are US or Canadian numbers: ten digits with
  no country code, or led by 1 or +1. Other countries' numbers, and anything that cannot be
  read as a North American number, are shown exactly as entered.

## [1.4.0] - 2026-10-04

### Added
- **Reports.** A new Reports page, between Participants and Draw in the event menu, lists
  every prize that has been claimed: the prize, who took it, their tickets and phone, and
  when. Most recent claim first, paginated like the other grids (10 per page, up to 100).

### Changed
- The prizes grid no longer has a Status column; who took what lives on the Reports page,
  which frees room for prize names and descriptions.

### Fixed
- On the draw page, a lookup result taller than the window pushed the footer into the
  middle of the content (or beside it) instead of staying at the bottom. The result now
  scrolls above the footer.
- The footer was wider than a phone screen by its own padding.

## [1.3.2] - 2026-10-04

### Changed
- Ticket numbers are drawn as small raffle tickets wherever a participant's tickets are
  shown: the wishlist, the participants grid, the QR code dialog and the draw result. With
  prefixed numbers (`12-01 – 12-04`) the dash inside a number no longer reads like the dash
  between two numbers. Screen readers hear "12-01 to 12-04".
- The wishlist, the QR code dialog and the draw result list the tickets one range per
  line, so several prefixed ranges no longer wrap into a jumble. In the grid a range never
  breaks across lines.
- The QR code dialog speaks to the participant ("Your tickets:"), and the scan hint sits
  above the QR code.

## [1.3.1] - 2026-10-04

### Changed
- The heart in the "made with ❤️ by dogeared" footer beats when you hover over the footer,
  a nod to the Stormpath footer. It stays still for visitors who prefer reduced motion.

## [1.3.0] - 2026-10-04

### Added
- Organizers and admins can take a prize off a participant's list: from the picks dialog on
  the participants page, and from the winner's list on the draw page. Removing a prize the
  participant had claimed releases the claim too.

### Changed
- Opening an event lands on **Prizes** (`/events/{id}`); participants moved to
  `/events/{id}/participants`.
- The wishlist column on the participants page stays on one line with an ellipsis, on every
  screen size — the full list is in the dialog, and organizers work on laptops and tablets.

## [1.1.3] - 2026-10-04

### Changed
- **Grids on phones.** Long values in the participants, prizes and events grids wrap onto
  more lines instead of being cut off with "…", and on narrow screens the lower-priority
  columns (participant count and wishlist, prize description, event organizers) give way so
  the rest fits the width without a sideways scroll. Those details remain a tap away in the
  editor and wishlist dialogs.

## [1.2.0] - 2026-10-04

### Changed
- **Grids on phones.** Cells wrap long values (names, ticket lists, wishlists, organizer
  lists) instead of truncating them, and on screens narrower than 640px the lower-priority
  columns (participant count and wishlist, prize description, event organizers) are hidden
  so the rest fits without sideways scrolling. The hidden details remain a tap away in the
  name, wishlist and edit dialogs; wider screens still show every column.

## [1.1.2] - 2026-10-02

### Fixed
- Ticket-range errors in the participant editor (mismatched prefixes, reversed order, a
  missing end) are shown on a line under the row with both fields outlined, so the rows
  stay aligned instead of staggering.

## [1.1.1] - 2026-10-02

### Fixed
- The ticket-range rows in the participant editor no longer stagger: the format hint is
  shown once under "Tickets", the two fields share the row, and the remove button lines up
  with the inputs.

## [1.1.0] - 2026-10-02

### Added
- **Ticket numbers with prefixes.** Rolls are numbered in different formats, and ticket
  ranges are now entered exactly as printed: plain numbers (`1 – 100`) or dashed prefixes
  (`987-001 – 987-100`, `4563-100-300 – 4563-100-1000`). Everything up to the last dash is
  the prefix and the digits after it the sequence; both ends of a range share the prefix,
  and ranges with different prefixes never overlap (`4563-100` and `4564-100` are different
  tickets). The draw page takes the drawn ticket as printed, dashes included. Zero-padding
  is kept, so `987-001` stays `987-001`.

## [1.0.1] - 2026-10-02

### Fixed
- **Styling lost in 1.0.0.** Vaadin 25 stopped loading the Lumo utility classes with the
  theme, which silently dropped most spacing, card backgrounds, flex layouts and the centred
  footer across the app. They are loaded explicitly again.
- The landing page is a centred column with proper side padding, and the "Participants
  don't log in" note sits under the log-in button in small print.

## [1.0.0] - 2026-10-02

First stable release: events with organizers, prizes, participants with multiple ticket
ranges, QR-code wishlists and the draw page, on a dependency set with no known
vulnerabilities (`snyk test` and `snyk code test` both report 0 issues).

### Security
- **Spring Boot 4.0.8 and Vaadin 25.3.0** (from 3.5.16 and 24.8.17). Spring Boot 3.5's
  open-source support has ended and the fixes for its transitive dependencies only exist in
  the 4.x line; `snyk test` went from 90 open issues (7 critical) to 0. Tomcat, Jackson and
  Logback are pinned above what Boot 4.0.8 manages for the same reason.
- A git pre-push hook (`.githooks/pre-push`) runs `snyk test` and `snyk code test`; see the
  README for enabling it.

### Added
- **Organizer picker.** The event editor offers everyone who has logged in as an organizer
  (name and email) in a multi-select, instead of a box for typing emails. Someone who has not
  logged in yet can still be added by typing their email. The event list shows organizer names
  where known.
- A Keycloak login theme (`keycloak/themes/open-raffle`) that restyles the sign-in pages to
  match the app; the bundled realm export selects it. The README explains how to install and
  enable it on a central Keycloak.

### Changed
- The coverage badge reads from a gist via shields.io instead of a `badges` branch, so CI no
  longer pushes to the repository after each merge.
- Vaadin 25 applies access rules to parent layouts too, so the main layout is now limited to
  the organizer and admin roles like the views inside it.
- Test support moved to Spring Boot 4's per-technology modules (`spring-boot-data-jpa-test`,
  `spring-boot-jdbc-test`) and Karibu Testing 2.7.

## [0.4.0] - 2026-10-02

### Added
- **Multiple ticket ranges per participant.** People come back to buy more tickets: the
  participant editor has an "Add another range" button and a remove button per range. The
  overlap check covers every range of every participant in the event (and a participant's own
  ranges may not overlap each other), the draw page finds a winner from any of their ranges,
  and the participants grid, draw page and wishlist page show all ranges, e.g. "1 – 10, 30 – 35".

### Changed
- Participants are listed alphabetically (they were ordered by ticket number).
- Existing participants' single range is converted on the first start-up after upgrading.

## [0.3.2] - 2026-10-02

### Added
- Server-side tests for every view (Karibu Testing): the landing page, event list and
  editor, participants, prizes with pagination, the draw page's claims and on-the-spot prizes,
  the participant wishlist including auto-save, and the layout's navigation.

### Changed
- Inside an event, the side navigation lists Prizes before Participants.
- The README shows a test-coverage badge, generated by CI from the JaCoCo report on every
  push to `main`.

## [0.3.1] - 2026-10-02

### Added
- On the participants page, a participant's wishlist summary is clickable and opens a dialog
  with their full ranked list, noting prizes that have already been claimed.
- The prizes list shows a row number again, purely for readability, counting across pages.

## [0.3.0] - 2026-10-02

### Added
- **Pagination.** The prizes and participants grids and the "Available prizes" list on the
  wishlist page show 10 items per page by default, with a chooser for 10, 25, 50 or 100 and
  previous/next controls. A participant's own ranked picks are never paginated.

## [0.2.2] - 2026-10-02

### Changed
- **Participants.** Phone number is required when an organizer creates or edits a participant
  (existing participants without one keep working). The phone is no longer shown in the
  participants grid, since that screen is turned towards participants for QR scanning; it is
  still on the draw page. A participant's name is clickable and opens the editor, like the
  pencil button. The "last ticket" field highlights its prefilled value on focus so typing
  replaces it.
- **Prizes** are listed alphabetically; the ordering arrows and position column are gone.
  Participants still rank their own picks on the wishlist page.
- **Wishlist page.** The "Anything else?" notes field is gone. Changes are saved automatically
  every ten seconds (the Save button remains) and the page shows when it was last saved.

### Fixed
- The footer on the draw page sat in the middle of the page.

## [0.2.1] - 2026-10-02

### Fixed
- **Grids collapsed to a single row.** The footer wrapper introduced in 0.2.0 gave the
  routed view no real height, so full-size grids (prizes, participants, events) shrank to one
  visible row with an easy-to-miss scrollbar: a newly added prize was saved but appeared to
  vanish. Views now fill the space above the footer.

## [0.2.0] - 2026-10-02

### Added
- **Events.** Raffles are now events: each has a unique name, and participants, prizes and
  the draw live inside one. Admins create, edit, soft-delete and reinstate events from the
  new event list and assign organizers to them by email.
- **`ORGANIZER` role.** Organizers log in and pick one of the events they are listed on
  (going straight in when there is only one); everything inside an event works as before.
  `ADMIN` keeps all of that on every event; the app grants `ORGANIZER` to every `ADMIN`.
- **Landing page** at `/` with a description of the app and a log-in button for organizers.
- **Global footer** — "made with ❤️ by dogeared · version x.y.z" — on every page, including
  participants' wishlist pages; the version also appears at `GET /actuator/info`.
- A participant's wishlist link says "This raffle is over" once its event is deleted.

### Changed
- Organizer pages moved under `/events/{id}`, `/events/{id}/prizes` and `/events/{id}/draw`;
  the old `/`, `/prizes` and `/draw` routes are gone. QR-code links (`/p/{token}`) are
  unchanged.
- Participants and prizes that predate events are attached to a "Default event" on the
  first start-up after upgrading.

## [0.1.2] - 2026-10-02

### Fixed
- **Organizers were denied access with a stock Keycloak client.** Keycloak's default
  realm-roles mapper puts `realm_access.roles` only in the access token, which the app never
  read, so a user with the `ADMIN` role still saw "Could not navigate to ''" after logging
  in. Realm roles are now read from the access token as well as the ID token and userinfo,
  so no mapper changes are needed in Keycloak.

## [0.1.1] - 2026-10-01

### Added
- **Render deployment.** `render.yaml` Blueprint that provisions the app and its Postgres
  database and prompts for the connection details of an existing, centrally managed Keycloak
  (`KEYCLOAK_ISSUER`, `RAFFLE_PUBLIC_URL`). The README documents
  what the Keycloak client needs.
- `GET /actuator/health` without authentication, for platform health checks.
- `PORT` is honoured for the HTTP port, and the database can be configured as
  `DB_HOST`/`DB_PORT`/`DB_NAME` as well as a full `DB_URL`.

### Changed
- **The Keycloak client is public.** Login uses the authorization code flow with PKCE and no
  client secret; `KEYCLOAK_CLIENT_SECRET` is gone. The bundled realm export's client is now
  `publicClient: true` (re-import it, or switch the existing client's *Client authentication*
  off).
- Realm roles are read from the userinfo response as well as the ID token, so a realm whose
  roles mapper does not add them to the ID token still grants organizer access.
- The JVM sizes its heap from the container memory limit (`-XX:MaxRAMPercentage=75`).

## [0.1.0] - 2026-10-01

### Added
- **Participants.** Organizers register each participant with a name, an optional phone
  number, and the contiguous range of physical ticket numbers they hold. Overlapping ranges
  are rejected.
- **QR code wishlists.** Every participant gets a QR code (shown in the app and downloadable
  as PNG) that opens a login-free page where they rank the prizes they'd like, using up/down
  arrows, and leave optional notes. The link's base URL comes from `RAFFLE_PUBLIC_URL`, or from
  the current request when unset.
- **Prizes.** Organizers manage the prize list with the same arrow-based ordering participants
  use. New prizes go to the bottom of the list.
- **Draw page.** Type a drawn ticket number to see who holds it, their phone number, and their
  ranked preferences. Tick a prize to record the claim; prizes already taken by earlier winners
  are struck through. Prizes not on the winner's list can be given out too, and brand-new prizes
  can be added and handed over on the spot; both join the winner's preference list.
- **Keycloak login.** Organizer pages require an OpenID Connect login (authorization code with
  PKCE) against a Keycloak realm whose `ADMIN` role maps to the app's admin role. A ready-made
  realm export with an `organizer` user is included.
- **Docker.** A multi-stage `Dockerfile` builds the Vaadin production bundle; `docker-compose.yml`
  provides Postgres for local development.
- **CI.** GitHub Actions runs the test suite on every push and pull request.

[1.1.3]: https://github.com/dogeared/open-raffle/compare/v1.1.2...v1.1.3
[1.3.2]: https://github.com/dogeared/open-raffle/compare/v1.3.1...v1.3.2
[1.3.1]: https://github.com/dogeared/open-raffle/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/dogeared/open-raffle/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/dogeared/open-raffle/compare/v1.1.2...v1.2.0
[1.1.2]: https://github.com/dogeared/open-raffle/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/dogeared/open-raffle/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/dogeared/open-raffle/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/dogeared/open-raffle/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/dogeared/open-raffle/compare/v0.4.0...v1.0.0
[0.4.0]: https://github.com/dogeared/open-raffle/compare/v0.3.2...v0.4.0
[0.3.2]: https://github.com/dogeared/open-raffle/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/dogeared/open-raffle/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/dogeared/open-raffle/compare/v0.2.2...v0.3.0
[0.2.2]: https://github.com/dogeared/open-raffle/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/dogeared/open-raffle/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/dogeared/open-raffle/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/dogeared/open-raffle/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/dogeared/open-raffle/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/dogeared/open-raffle/releases/tag/v0.1.0
