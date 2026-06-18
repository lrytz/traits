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

A working first version: the pipeline, feature detail, and changelog views,
the full backend API (public reads + editor-gated writes) with a live OpenAPI
spec at `/docs`, and the **editor UI** — sign in and create / edit / delete
features. LLM-assisted curation is external: point a coding agent at the API
(see [`docs/agent-curation.md`](docs/agent-curation.md)).

## Modules

| Module     | Platform | What it holds                                                        |
| ---------- | -------- | ------------------------------------------------------------------- |
| `shared`   | JVM + JS | Domain model (`Topic`, `SipState`, `Lane`, …), tapir `Endpoints`    |
| `backend`  | JVM      | Netty-sync server, SQLite/Magnum store, auth, OpenAPI docs          |
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
  ones an agent re-reads when curating
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

## Curation by a coding agent

There's no LLM baked into the app. Instead, point a coding agent you already run
(Claude Code, Claude Desktop, …) at the editor-gated HTTP API: it reads the live
OpenAPI spec at `/docs`, signs in with the shared password, and updates entries
through `PUT`/`DELETE` — re-reading linked discussions, advancing SIP state, and
drafting timeline entries for you to review. See
[`docs/agent-curation.md`](docs/agent-curation.md).

## Running it

Prerequisites: JDK 17+, sbt, Node.

```bash
# 1. backend (serves /api on :8080)
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

## Data

The store holds **real, curated data** — Scala language features introduced
after 3.0.0, across the lifecycle from idea to generally available. It is
populated and edited through the editor UI or the HTTP API (see
[`docs/agent-curation.md`](docs/agent-curation.md)); the app does **not** seed or
re-initialise the DB on start. Source material is listed in `DATA.md`. The SQLite
file under `traits-data/` is the artifact to back up and deploy.

## Roadmap

1. Agent ergonomics — a bearer-token / API-key auth path for headless agents,
   optionally an MCP server for non-shell agents (Claude Desktop).
2. GitHub label sync — read SIP PR labels directly to propose `SipState`.
3. Version matrix view; cross-topic references (`[[slug]]`) with backlinks.
