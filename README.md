# Traits

A tracker for Scala language features as they move through the pipeline — from a
pre-SIP idea, through the [SIP committee process](https://docs.scala-lang.org/sips/process-specification.html),
to experimental, preview, and finally generally available. It answers "where
does feature X stand, in which Scala version, under which flag, and how did it
get there?"

Built with Scala 3 + tapir (direct style, ox) on the
backend, Scala.js + Laminar on the frontend, a shared module for the wire
contract. Storage is a single SQLite file holding each feature as a JSON
document.

## Status

This is a **first slice** — the foundation plus read-only views over seeded
data. It has not yet been compiled against your toolchain; run
`sbt -client compile` and we'll fix anything `-Werror` flags. What works:
the pipeline view, feature detail, changelog, and the full backend API
(including editor auth and the AI-enrichment stub). What's next: the editor
UI (login + edit forms) and a real `LlmClient`.

## Modules

| Module     | Platform | What it holds                                                        |
| ---------- | -------- | ------------------------------------------------------------------- |
| `shared`   | JVM + JS | Domain model (`Topic`, `SipState`, `Lane`, …), tapir `Endpoints`    |
| `backend`  | JVM      | Netty-sync server, SQLite/Magnum store, auth, AI enrichment         |
| `frontend` | JS       | Laminar SPA: pipeline, detail, changelog                            |

## Data model

A **Topic** is the unit of tracking (a SIP is optional — not everything has one):

- freeform ordered **`sections`** of markdown (Overview, History, Discussion
  summary, How to try it — whatever fits)
- an optional **`sip`** whose `state` is the closed set of legal
  `(stage, status, recommendation)` combinations from the process spec — these
  mirror the GitHub labels on `scala/improvement-proposals`
- the current **`availability`** = `(kind ∈ experimental|preview|stable,
  sinceVersion)`; earlier transitions live in the timeline, not as standing rows
- typed **`links`** (SIP / PR / issue / forum / doc); `watch = true` marks the
  ones the AI re-reads
- a dated **`timeline`** that feeds the global changelog

The headline "where it stands" and the pipeline **`Lane`** are *derived* from
availability + SIP state, so they can never contradict the underlying facts.

## Storage

One SQLite table, `topic`: the whole `Topic` serialized to JSON in a `data`
column, with `updated_at` and a denormalised lowercase `search_text` alongside
for cheap listing and `LIKE` search. WAL mode is on. The same upickle codec
serves the wire and the store, so there's no row-class translation layer.
Schema setup is idempotent (`CREATE TABLE IF NOT EXISTS`) in `Db.migrate`.

## Auth

Public read; editor-gated writes. A single shared password
(`TRAITS_EDITOR_PASSWORD`) is exchanged for an HMAC-signed session cookie. The
session carries an `editor` identity string so per-user auth (e.g. GitHub OAuth
with a committee allowlist) can drop in later without reshaping the endpoints.

## AI enrichment

`POST /api/topics/{slug}/enrich` re-reads a topic's `watch` links and returns an
`EnrichResult` — a *suggestion* (summary, proposed SIP state, timeline entries)
that an editor reviews and applies by hand. It never writes. The LLM is behind a
one-method `LlmClient` trait; `StubLlmClient` returns a placeholder so the flow
works without an API key. Wire a real client into `Main` to enable it.

## Running it

Prerequisites: JDK 17+, sbt, Node.

```bash
# 1. backend (serves /api on :8080, seeds the DB on first run)
sbt backend/run

# 2. frontend dev server (in another terminal)
cd frontend && npm install && npm run dev
# open http://localhost:5173  — /api is proxied to :8080
```

Editing the password / secrets: copy the defaults from `backend/.../Config.scala`
env vars (`TRAITS_EDITOR_PASSWORD`, `TRAITS_SESSION_SECRET`, `TRAITS_DB_PATH`).
`sbt backend/run` sets `TRAITS_ENV=dev` so the dev defaults apply; a packaged
jar must set them explicitly (fail-closed).

For a production build, `cd frontend && npm run build` emits `frontend/dist`,
which the backend serves as static files (`TRAITS_STATIC_FILES`).

## Seed data

`Seed.scala` inserts a few illustrative topics (named tuples, better-fors,
capture checking, union types) into an empty DB. **The facts are approximate** —
versions, SIP states, and links are placeholders to make the UI tangible and
should be corrected through the app.

## Roadmap

1. Editor UI — login, create/edit forms for topics, sections, links, timeline.
2. Real `LlmClient` + parse the model's reply into structured suggestions with a
   side-by-side review/accept panel.
3. GitHub label sync — read SIP PR labels directly to propose `SipState`.
4. Version matrix view; cross-topic references (`[[slug]]`) with backlinks.
