# Open Raffle

[![Tests](https://github.com/dogeared/open-raffle/actions/workflows/tests.yml/badge.svg)](https://github.com/dogeared/open-raffle/actions/workflows/tests.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/dogeared/4eda955304a98d793584a2f69c52a7ba/raw/open-raffle-coverage.json)](https://github.com/dogeared/open-raffle/actions/workflows/tests.yml)
[![Version](https://img.shields.io/github/v/tag/dogeared/open-raffle?label=version&sort=semver)](https://github.com/dogeared/open-raffle/tags)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Run a physical-ticket raffle without the paper chaos. Organizers record who holds which
ticket numbers, hand each participant a QR code, and participants rank the prizes they'd
like from their phone. When a ticket is drawn, the draw page shows the winner, their
ranked preferences, and which of those prizes are still available.

Built with Spring Boot and Vaadin, secured with OpenID Connect via Keycloak, and packaged
for Docker.

## How it works

1. **Events** — an admin creates an event (a raffle) and picks the organizers who may run
   it. The picker lists everyone who has logged in as an organizer; someone who hasn't yet
   can be added by typing the email of their Keycloak account. Everything below lives inside
   an event.
2. **Prizes** — organizers enter the prizes and order them with up/down arrows.
3. **Participants** — each participant gets a name, a phone number, and the ranges of ticket
   numbers they bought — several, if they come back for more. Tickets are entered exactly as
   printed on the roll: plain numbers (`1 – 100`) or dashed prefixes (`987-001 – 987-100`,
   `4563-100-300 – 4563-100-1000`); everything up to the last dash is the prefix, so
   `4563-100` and `4564-100` are different tickets. Ranges that overlap another
   participant's (or each other) are rejected.
4. **QR code** — the app shows (and can download) a QR code per participant. It opens a
   login-free page, identified by an unguessable token, where they rank the prizes they
   want.
   There is also a login-free **public prize list** per event at `/e/<event-name>`, where
   the name is lowercased with words joined by dashes (`Carnage & Fun 29` →
   `/e/carnage-fun-29`): the same list participants see, without the picking. The
   **Public list** button on the Prizes page opens it, and the QR code beside the
   heading opens a printable Letter-sized poster of it for a sign at the event.
5. **Draw** — type the drawn ticket number as printed. The winner's preferences appear with a checkbox
   per prize; tick the one they take. Prizes already claimed by earlier winners are struck
   through. Prizes not on their list can be given out too, and new prizes can be added and
   handed over on the spot.

Two Keycloak realm roles control access:

| Role | Can |
| --- | --- |
| `ORGANIZER` | Open the events they are listed on and run them: prizes, participants, reports, draw |
| `ADMIN` | Everything an organizer can, on every event, plus create, edit, delete and reinstate events and assign organizers |

The public landing page at `/`, the participants' wishlist pages at `/p/<token>` and the
public prize lists at `/e/<event-name>` need no login; after logging in, organizers pick an
event and admins see the event list.

## Running locally

Prerequisites: Java 21, Maven, Docker.

Keycloak is expected to run as a shared instance outside this project (see
`keycloak/open-raffle-realm.json` for the realm to import; it includes an `organizer` /
`organizer` user with the `ADMIN` role). Point it at `http://localhost:8180`, or override
`KEYCLOAK_ISSUER` below.

```sh
docker compose up -d                                   # Postgres
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Open http://localhost:8081 and log in. The bundled realm has `organizer` / `organizer`
(`ADMIN`) and `helper` / `helper` (`ORGANIZER`; add `helper@example.com` to an event to see
the organizer experience). Keep the app and the browser reaching Keycloak at the same URL:
OIDC validates the token issuer against the configured issuer.

## Configuration

All settings are environment variables with local-dev defaults (see
`src/main/resources/application.properties`).

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port (injected by Render and similar hosts) |
| `RAFFLE_PUBLIC_URL` | *(derived from each request)* | Base URL embedded in QR codes, e.g. `https://raffle.example.com`. Set this in production; phones must be able to open it. |
| `BGG_API_KEY` | *(empty: lookup hidden)* | BoardGameGeek API token for the prize editor's game lookup; see [BoardGameGeek lookup](#boardgamegeek-lookup). |
| `RAFFLE_IMAGES_DIR` | `./data/images` | Where prize pictures are cached. A picture whose file is missing (e.g. after a deploy on a host without persistent disks) is fetched again when first viewed: organizers' uploads from Google Drive, box art from BoardGameGeek. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | *(empty: uploads off)* | Google OAuth web client for connecting a Drive folder; see [Organizers' pictures (Google Drive)](#organizers-pictures-google-drive). |
| `DB_URL` | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` | Full JDBC URL; or set the parts below |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `raffle` | Database location, as managed Postgres providers hand it out |
| `DB_USER` / `DB_PASSWORD` | `raffle` / `raffle` | Database credentials |
| `KEYCLOAK_ISSUER` | `http://localhost:8180/realms/open-raffle` | OIDC issuer URL of the realm |
| `KEYCLOAK_CLIENT_ID` | `open-raffle-app` | Public client in that realm (authorization code + PKCE, no secret) |

Behind a reverse proxy, the app trusts `X-Forwarded-Proto`, `X-Forwarded-Host` and
`X-Forwarded-Port` (`server.forward-headers-strategy=framework`), so OIDC redirect URIs and
request-derived QR links use the public scheme and host. Add the public URL to the Keycloak
client's redirect URIs.

## Keycloak login theme

`keycloak/themes/open-raffle/` is a Keycloak *login* theme — a CSS layer over the stock
`keycloak` theme, so every login-flow page (sign in, forgot password, errors, OTP) gets the
app's look: the same purple primary, system font and rounded white card. It has no
templates of its own, which keeps it working across Keycloak upgrades.

**Install it** by making the folder visible to Keycloak as `/opt/keycloak/themes/open-raffle`:

- Docker Compose — bind-mount it (this is what `../local-keycloak` does for development):
  ```yaml
  volumes:
    - ./path/to/open-raffle/keycloak/themes/open-raffle:/opt/keycloak/themes/open-raffle:ro
  ```
- A custom image — copy it in:
  ```dockerfile
  COPY keycloak/themes/open-raffle /opt/keycloak/themes/open-raffle
  ```
- Any other install — copy the folder into Keycloak's `themes/` directory.

Keycloak reads the `themes/` directory on start-up, so restart it after installing. Themes
are cached in production mode; while editing the CSS, start Keycloak with
`KC_SPI_THEME_CACHE_THEMES=false KC_SPI_THEME_CACHE_TEMPLATES=false KC_SPI_THEME_STATIC_MAX_AGE=-1`
(the `local-keycloak` compose sets these) so changes show on reload.

**Enable it** for the realm in the admin console: **Realm settings → Themes → Login theme →
`open-raffle`** → Save. The bundled realm export already selects it, together with the
realm's display name (`🎟️ Open Raffle`), which the theme shows as the page header; set
**Realm settings → General → HTML Display name** on an existing realm to match.

## Production build

```sh
docker build -t open-raffle .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db:5432/raffle -e DB_USER=raffle -e DB_PASSWORD=... \
  -e KEYCLOAK_ISSUER=https://auth.example.com/realms/open-raffle \
  -e RAFFLE_PUBLIC_URL=https://raffle.example.com \
  open-raffle
```

The multi-stage `Dockerfile` builds the Vaadin production bundle, so no Node.js is needed
at runtime. The schema is created and migrated by Hibernate (`ddl-auto=update`).
`GET /actuator/health` answers without authentication for load-balancer health checks, and
`GET /actuator/info` reports the running version (also shown in every page's footer).

## Deploying to Render

`render.yaml` is a [Render Blueprint](https://render.com/docs/infrastructure-as-code) for the
app and its Postgres database. Keycloak is not part of it: the app connects to an existing,
centrally managed Keycloak, the same way local development uses a shared instance.

**1. Prepare the client in your Keycloak.** `keycloak/open-raffle-realm.json` is a complete
working example; in an existing realm you need:

- a **public** OpenID Connect client (`open-raffle-app` unless you change
  `KEYCLOAK_CLIENT_ID`) — client authentication off, no secret — with Standard flow
  enabled, PKCE method `S256`, and
  - Valid redirect URIs: `https://<app host>/login/oauth2/code/keycloak`
  - Valid post logout redirect URIs: `https://<app host>/*`
  - Web origins: `https://<app host>`
- realm roles `ORGANIZER` and `ADMIN` (Realm roles → Create role, not client roles).
  Assign them under Users → the user → Role mapping → Assign role, switching the dialog's
  filter from **Filter by clients** (the default, which hides realm roles) to **Filter by
  realm roles**. `ADMIN` implies `ORGANIZER` inside the app, so no composite role is
  needed. Keycloak's default `roles` client scope already puts realm roles in the access
  token, which is enough: the app reads `realm_access.roles` from the access token, the ID
  token and the userinfo response.
- Organizers are matched to events by the **email** of their Keycloak account, so give
  each organizer user an email.
- If the Keycloak instance serves **more than one hostname** (several apps, several
  realms), pin this realm to the hostname the app uses: **Realm settings → General →
  Frontend URL** → e.g. `https://auth.example.com`. Without it the token issuer follows
  whatever host a request arrived on, and a backchannel request that reaches Keycloak
  under another name produces a token the app rejects — which shows up as a login
  redirect loop.

**Optional: the Open Raffle login theme.** `keycloak/themes/open-raffle` restyles Keycloak's
login pages to match the app (see [Keycloak login theme](#keycloak-login-theme)).

**2. Create the Blueprint.** In the Render dashboard choose **New → Blueprint** and pick this
repository. You are prompted for the values marked `sync: false`:

| Variable | Example |
| --- | --- |
| `RAFFLE_PUBLIC_URL` | `https://open-raffle.onrender.com` (or your custom domain) |
| `KEYCLOAK_ISSUER` | `https://auth.example.com/realms/open-raffle` |

The database variables are wired automatically. The app sits behind Render's TLS proxy and
trusts its `X-Forwarded-*` headers, so OIDC redirects use the public `https://` URL.

The plans in `render.yaml` are the smallest paid tiers so nothing sleeps during an event;
change them to `free` to try it out.

## Tests

```sh
mvn test
```

The suite covers the services, QR URL resolution and Keycloak role mapping, and every Vaadin
view: the view tests use [Karibu Testing](https://github.com/mvysny/karibu-testing) to
instantiate the real views in the Spring context and click through them server-side, with no
browser. Tests use an in-memory H2 database and a stub OIDC client registration, so neither
Docker nor Keycloak is needed to run them; they run on every push and pull request via
GitHub Actions. `mvn test` also writes a JaCoCo coverage report to
`target/site/jacoco/index.html`; on every push to `main`, CI publishes the percentage to a
gist that the README badge reads.

## Security scanning

A pre-push hook runs [Snyk](https://snyk.io)'s dependency scan (`snyk test`) and static
analysis (`snyk code test`) and blocks the push if either finds anything. Git does not
install hooks from a clone, so enable it once:

```sh
brew install snyk-cli && snyk auth      # once per machine
git config core.hooksPath .githooks     # once per clone
```

`git push --no-verify` skips it for one push. The hook is a no-op when the `snyk` CLI is
not installed, so it never blocks a machine that lacks it.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE)

## BoardGameGeek lookup

The prize editor can look games up on [BoardGameGeek](https://boardgamegeek.com): start
typing in the **BoardGameGeek** field and pick the game, like the search box on BGG itself.
Picking one fills in the prize name (if empty), stores the BGG id with the prize and
downloads the game's box image once into `RAFFLE_IMAGES_DIR`; the picture then shows on the
prizes grid, the public prize list and participants' wishlists without further API calls.
Hover or click a thumbnail for the full-size picture, which carries BGG's community rating
in BGG's colours. Ratings are cached with the prize and refreshed by a background job a few
times a day once they are a month old (or were never fetched); `raffle.bgg.refresh=false`
turns the job off. BGG's terms require their "Powered by BGG" badge wherever their data is
used; it ships with the app (`img/bgg-powered-by.png`) and sits under the lookup field.
Unlinking the game removes the picture.

BGG's XML API requires a registered application and a bearer token:

1. Register an application at <https://boardgamegeek.com/applications> and, once approved,
   create a token under **Tokens**.
2. Set it as `BGG_API_KEY` (locally in your shell or `.env`; on Render as the prompted
   environment variable). Without it the field is disabled and says so.

Images are served from `/images/<name>` without login; names are minted by the app
(`prize-<id>-<random>.<jpg|png|gif|webp>`), anything else is a 404, and the files are
checked to really be images before they are stored. The directory is a cache: if a file is
gone (Render's filesystem is reset on every deploy, and the blueprint attaches no disk), the
picture is fetched from BGG again the first time someone views it.

## Organizers' pictures (Google Drive)

Organizers and admins can upload up to 10 pictures per prize, ordered as they like; the
first is the primary one (the thumbnail), and the large view shows a strip of the others
that swap in on hover or tap. The BoardGameGeek box image, when there is one, comes last.
Uploads need a Google Drive folder connected under **Settings**: the app keeps the pictures
there (its own folder, created in the connected account, with a subfolder per event named
after the event, using the `drive.file` scope so it can only see files it created) and
caches copies locally. If a cached copy is missing it is
fetched from Drive again; if Drive cannot supply it either, the prize's BoardGameGeek image
is shown instead. Settings shows the connection's state and lets anyone with access check,
reconnect or disconnect it; while it is disconnected or unhealthy, uploads are refused and
existing pictures keep showing from the cache.

To enable it, create a Google OAuth client:

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project (or pick
   one), enable the **Google Drive API**, and configure the OAuth consent screen (External;
   add the Google accounts that will connect as test users while the app is unverified).
2. Create credentials → **OAuth client ID** → *Web application*, with
   `https://<your host>/drive/callback` as an authorised redirect URI (and
   `http://localhost:8081/drive/callback` for local development).
3. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (on Render the blueprint prompts for
   them) and restart. Then open **Settings** and click **Connect Google Drive**.

The refresh token is stored in the database; it grants access only to files the app
created. Disconnecting forgets it (revoke the app at myaccount.google.com/permissions too).

Uploads follow the
[OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html):
only `.jpg`/`.jpeg`/`.png`/`.gif` names and `image/jpeg|png|gif` types are accepted, the
bytes must carry the matching image signature and must decode, the header's dimensions are
checked (max 40 megapixels) before any pixels are allocated, files are capped at 10 MB
(and the servlet at 12 MB per file / 60 MB per request), each user is limited to 60
uploads per 10 minutes, and every picture is re-encoded from its decoded pixels (dropping
EXIF/location data and anything else hidden in the file, after applying the EXIF
orientation so portrait photos stay upright) and shrunk to a 1600px long edge; JPEGs are
decoded with TwelveMonkeys' reader so embedded colour profiles are honoured, PNG profiles
are applied as well (iPhone HDR photos are tone-mapped to sRGB), and JPEGs are saved at
quality 0.9. GIFs become still PNGs. Uploads are streamed to temporary files, big photos are decoded at
a reduced resolution, pictures are processed one at a time (`raffle.uploads.concurrent`),
at most 8 uploads are received at once server-wide, and the size, count and type limits
are enforced on the server as well as in the browser. The `Dockerfile` sizes the JVM for
a 512 MB instance (heap 35% of memory, serial GC, trimmed stacks and code cache); the
process stays under about 430 MB through batches of six 12-megapixel HDR uploads. If you
run it with more memory, raise `-XX:MaxRAMPercentage` in `JAVA_TOOL_OPTIONS`. The upload's bytes and name are never stored: files get app-minted
names, live outside the web root and are served only through `/images/<name>` with a fixed
content type and `nosniff`. Only organizers of the event (or admins) can add, reorder or
remove a prize's pictures, and a prize holds at most 10.

## Roadmap

- **Event wrap-up.** An admin-only step for a finished event, in this order: build a
  downloadable ZIP of all the event's pictures, delete the event (soft delete, as today)
  and delete the event's picture subfolder from Drive (and the cached copies). The ZIP is
  offered for download before the pictures go, so nothing is lost.
- **1.8.0 — virus scanning of uploads** (e.g. ClamAV) and image moderation, if the raffles
  grow beyond trusted organizers.

