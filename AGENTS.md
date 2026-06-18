# traits — agent instructions

`traits` tracks Scala language features through their lifecycle — from a
pre-SIP idea, through the [SIP committee process](https://docs.scala-lang.org/sips/process-specification.html),
to experimental → preview → generally available. Public read, committee-edited.

The stack and conventions are deliberate: Scala 3 + tapir (direct style, ox) on
the backend,
Scala.js + Laminar on the frontend, a shared module for the wire contract. When
in doubt about a pattern, follow the existing code. The one big difference:
storage is a single **SQLite** file holding each feature as a **JSON document**,
not a normalized Postgres schema.

> Status: this is an early scaffold. It has **not yet been compiled** against the
> toolchain — the first `sbt compile` will likely surface a few `-Werror` nits
> (unused imports, value-discard). Fix them; don't suppress.

## Modules

| Module     | Platform | Package root                         | Holds                                              |
| ---------- | -------- | ------------------------------------ | -------------------------------------------------- |
| `shared`   | JVM + JS | `org.scalalang.traits.shared`        | Domain model, tapir `Endpoints`, `Schemas`, `ApiError` |
| `backend`  | JVM      | `org.scalalang.traits.backend`       | Netty-sync server, SQLite/Magnum store, auth, AI   |
| `frontend` | JS       | `org.scalalang.traits.frontend`      | Laminar SPA: pipeline, detail, changelog           |

`shared` is a pure cross-project; both sides depend on it so the HTTP shape
can't drift.

## Build & verify

Use `sbt` via the Bash tool.

* `sbt compile` — main compile across modules
* `sbt Test/compile` / `sbt test` — tests (none yet; add munit suites under
  `*/src/test/scala`)
* `sbt backend/run` — start the API on `:8080` (sets `TRAITS_ENV=dev`, seeds the
  DB on first run)
* `sbt frontend/fastLinkJS` — link Scala.js for vite

If you keep an interactive session running (`sbt "~frontend/fastLinkJS"`),
`sbt -client "<task>"` reuses its incremental state and is much faster.

**`-Werror` is on**, with `-Wunused:all`, `-Wvalue-discard`, `-deprecation`,
`-feature`, `-unchecked` (see `build.sbt`). Treat every warning as a failure:

* no unused imports / locals / private members — delete them
* discard non-Unit results explicitly with `val _ = expr`
* `compile` must be green before committing

scalafmt is configured (`.scalafmt.conf`, `align.preset = more`): write
fields/case-arms with a single space and let the formatter pad the alignment —
don't align by hand. `sbt scalafmtAll` to format.

JDK 17+ is fine (no `-release` pin).

## Architecture

Request flow per concern uses a **Repo / Service / Api** triad:

* **`Endpoints`** (shared) — one `val` per route, body-only, error type `ApiError`.
  The single source of truth for the contract; the frontend derives its client
  from these same values.
* **`*Repo`** (backend) — raw Magnum `sql"…"` only. Returns the stored JSON
  document as a `String`; no upickle here.
* **`*Service`** (backend) — owns a `DataSource`, runs repo calls inside
  `connect`/`transact`, parses/serializes the document with upickle, maps to
  shared types.
* **`*Api`** (backend) — attaches server logic via `.handle` / `.handleSuccess`,
  attaches the session cookie for editor-gated routes, exposes `all:
  List[ServerEndpoint[Any, Identity]]`. Wired together in `Main`.

Server is `tapir-netty-server-sync` + `ox` — **direct style, no `Future`**. This
suits Magnum's blocking JDBC.

### Adding an endpoint

1. Add the `val` to `shared/.../Endpoints.scala` (body-only).
2. Wire server logic in the relevant `*Api.scala`. For an editor-gated write,
   add `.in(cookie[Option[String]](Endpoints.SessionCookieName))` and run
   `auth.requireEditor(cookie)` first (see `TopicApi.put`).
3. Add it to that class's `all` list (already aggregated in `Main`).
4. Add a typed wrapper to `frontend/.../Api.scala`.

## Storage (SQLite JSON document)

One table, `topic` (`backend/.../Db.scala`):

```
slug TEXT PK | updated_at TEXT | search_text TEXT | data TEXT(JSON)
```

* `data` is the whole `Topic` serialized with upickle. The **same upickle codec
  serves the wire and the store** — no row-class translation layer.
* `search_text` is a denormalised lowercase blob (title + tagline + tags +
  section bodies) recomputed on every write, for `LIKE` search.
* Reads return `data` as a `String`; `TopicService.parse` does `read[Topic](_)`.
  Writes do `write(topic)` and upsert.

Magnum notes:

* Raw `sql"…"` interpolation only — **no `Repo[T]` tables**, so no SQLite
  `DbType`/dialect is needed.
* Use the **block form**: `connect(ds) { … }` for reads, `transact(ds) { … }`
  for writes. The contextual `DbCon` is in scope inside the block.
* SQLite stores JSON as plain `TEXT` — no `::jsonb` cast (that's Postgres).
* `Db.migrate` is idempotent (`CREATE TABLE IF NOT EXISTS`) and sets WAL mode.
  `PRAGMA journal_mode=WAL` must run **outside** a transaction → it's in a
  `connect` block, not `transact`. There's no migration tool yet; evolve the
  schema with more idempotent statements, or add one when it gets real.
* The store is tiny and low-write; everything else (`Lane`, headline, version
  matrix) is derived in code from the parsed documents, not queried in SQL.

To reset/re-seed: delete `traits-data/traits.sqlite*` and restart the backend.

## Domain model (`shared/.../Domain.scala`)

A **`Topic`** is the unit of tracking; a SIP is optional. Key points:

* `sections: List[Section]` — freeform `(heading, markdown)`, rendered in order.
* `sip: Option[Sip]` whose `state: SipState` is the **closed set of legal
  `(stage, status, recommendation)` combinations** from the process spec —
  these mirror the GitHub labels on `scala/improvement-proposals`.
* `availability: Option[Availability]` = the **current** `(kind, sinceVersion)`
  only (experimental/preview/stable). Earlier transitions live in `timeline`,
  not as standing rows.
* `links: List[Link]` typed (SIP/PR/issue/forum/doc); `watch = true` marks the
  ones the AI re-reads.
* `timeline: List[TimelineEntry]` — dated milestones; the union across topics is
  the changelog.

**`Topic.lane` and `Topic.headline` are derived**, never stored or trusted from
the client — availability wins, else SIP state, else "idea". `FeatureSummary`
(list/pipeline projection) and `ChangelogEntry` are computed from `Topic`. If you
change the lifecycle rules, change the derivation in `Domain.scala` only.

upickle: every domain type uses `derives ReadWriter`, including enums (parameter-
ized cases like `SipState.DesignVoteRequested(Recommendation)` are supported).

tapir schemas: field-less enums need explicit string-based schemas and the
parameterized `SipState` needs `Schema.derived` — all in `shared/.../Schemas.scala`.
`Endpoints` does `import Schemas.given` + `import sttp.tapir.generic.auto.*` so
case-class schemas derive on top of them. When you add an enum used in a body
type, add its `Schema` given to `Schemas`.

## Auth

Public read; editor-gated writes/enrich. A single shared password
(`TRAITS_EDITOR_PASSWORD`) is exchanged at `POST /api/auth/login` for an
HMAC-signed session cookie (`SessionCodec`, `traits_session`). `AuthApi.requireEditor`
is the gate. The session carries an `editor` identity string, so swapping in
GitHub OAuth (committee allowlist) later is localised to `AuthApi` + `login`.

## AI enrichment

`POST /api/topics/{slug}/enrich` re-reads a topic's `watch` links and returns an
`EnrichResult` — a **suggestion** (summary, proposed SIP state, timeline entries)
the editor reviews and applies by hand. **It never writes.** The LLM is behind
the one-method `LlmClient` trait (`backend/.../ai/`); `StubLlmClient` returns a
placeholder. To enable it: implement `LlmClient.complete` (a blocking HTTP call
to Anthropic/OpenAI/local), construct it in `Main`, and have `EnrichService`
parse the reply into structured fields. The GitHub-label → `SipState` mapping is
the highest-value thing the AI can do reliably.

## Frontend conventions

* **Routing: Waypoint.** Use the `staticRoute` helper (`Route.applyPF`) for
  static routes — `Route.static` with a `ClassTag` fails at runtime against
  Scala 3 enum singletons. Add a `Page` case + a route to `allRoutes` +
  a branch in `Main.renderPage`.
* **API: tapir-sttp-client.** Shared endpoints are body-only; cookies ride
  automatically (HttpOnly, browser-attached). Add a wrapper to `Api.scala`
  using `toRequestThrowDecodeFailures(Endpoints.x, baseUri = None)`.
* **Markdown** is stored raw and rendered through `marked` + `DOMPurify`
  (`Markdown.scala` facade) — never store or inject HTML. Use
  `Components.markdown(md)` (sets sanitised `innerHTML` in an `onMountCallback`).
* **Laminar gotchas:** structural tags are `sectionTag` / `headerTag` /
  `navTag`, not bare `section`/`header`/`nav` — use `div` unless you need the
  semantic tag. The `type` attribute is `tpe`. `onInput.mapToValue --> aVar`
  binds a `Var` directly. `emptyNode` for the empty branch.
* **Shared primitives** go in `ui/Components.scala` (badges, cards, the
  `Loaded` async-switch, markdown). Don't inline-then-extract.
* **Async load pattern:** `Var[Loaded[A]]`, fire the `Api` call in the page's
  `apply()`, `Components.loaded(state.signal)(view)`.

## Code style

**Minimise comments** (only when the *why* is non-obvious), no
section banners, no scaladoc on trivial members. Keep the `Repo`/`Service`/`Api`
split. One short summary line max where a doc comment earns its place.

## Git

The repo is at `~/code/projects/traits` (branch `main`). **Run git yourself from
a real terminal** — git writes from the agent sandbox leave undeletable `.lock`
files on the mounted volume and ref updates fail. The convention:
finish a change, get `compile` green, let the user test, then one clean commit
when asked. Never amend/force-push. `frontend/package-lock.json` is tracked
(only `node_modules/` and `dist/` are ignored).

## Running locally

```bash
sbt backend/run                              # :8080, seeds DB on first run
cd frontend && npm install && npm run dev    # :5173, proxies /api → :8080
```

Open http://localhost:5173. Config is env-vars (`Config.scala`), fail-closed:
`sbt backend/run` sets `TRAITS_ENV=dev` so dev defaults apply; a
packaged jar must set `TRAITS_SESSION_SECRET` / `TRAITS_EDITOR_PASSWORD`
explicitly. Others: `TRAITS_HTTP_PORT`, `TRAITS_DB_PATH`, `TRAITS_DB_POOL_SIZE`,
`TRAITS_STATIC_FILES`. Prod build: `cd frontend && npm run build` → `dist/`,
served by the backend as static files.

## Seed data

`Seed.scala` inserts a few illustrative topics into an empty DB. **The facts are
approximate** — versions, SIP states, and links are placeholders to make the UI
tangible; correct them through the app.

## Roadmap

1. **Editor UI** — login + create/edit forms for topics, sections, links,
   timeline (backend already supports the writes).
2. **Real `LlmClient`** + parse replies into a side-by-side review/accept panel.
3. **GitHub label sync** — read SIP PR labels directly to propose `SipState`.
4. **Version matrix** view; cross-topic references (`[[slug]]`) with backlinks.
