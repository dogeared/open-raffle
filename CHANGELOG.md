# Changelog

All notable changes to Open Raffle are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
