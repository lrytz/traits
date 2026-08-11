# Traits

A tracker for notable changes to Scala as they move through their lifecycle —
from an idea or pre-SIP discussion, through the
[SIP process](https://docs.scala-lang.org/sips/process-specification.html), into
the compiler as experimental → preview → stable, and eventually to deprecated
and removed. It answers, for any change: *where does this stand, in which Scala
version, and how do I use it?*

## Status

A working proof of concept, deployed, holding sample data. It validated the
idea; the model is now being rebuilt properly, and real curation starts after
that.

**[`PLAN.md`](PLAN.md) is the design document** — what the app tracks, the
lifecycle, the data model, the views, and the plan of work. Read it first. The
notes below describe the code as it stands today, which the plan will change.

## Running it

Prerequisites: JDK 17+, sbt, Node.

```bash
sbt backend/run                              # serves /api on :8080

cd frontend && npm install && npm run dev    # in another terminal
# open http://localhost:5173 — /api is proxied to :8080
```

`sbt backend/run` sets `TRAITS_ENV=dev`, which supplies dev defaults for
`TRAITS_EDITOR_PASSWORD` (`let-me-in`), `TRAITS_SESSION_SECRET` and
`TRAITS_DB_PATH`. A packaged jar has no fallbacks and requires them explicitly.

For a production build, `cd frontend && npm run build` emits `frontend/dist`,
which the backend serves as static files (`TRAITS_STATIC_FILES`).

## Modules

| Module     | Platform | What it holds                                                    |
| ---------- | -------- | ---------------------------------------------------------------- |
| `shared`   | JVM + JS | Domain model and the tapir `Endpoints` — the wire contract        |
| `backend`  | JVM      | Netty-sync server, SQLite/Magnum store, auth, OpenAPI docs        |
| `frontend` | JS       | Laminar SPA                                                       |

Both sides depend on `shared`, so the HTTP shape can't drift between them.

Storage is a single SQLite file holding each entry as a JSON document. Reads are
public; writes are gated by a shared password exchanged for a signed session
cookie. There is no LLM in the server — curation is done by pointing a coding
agent at the HTTP API. See [`PLAN.md`](PLAN.md) for why.

## Documentation

| | |
| --- | --- |
| [`PLAN.md`](PLAN.md) | design and plan — the model, views, scope, roadmap |
| [`AGENTS.md`](AGENTS.md) | conventions for working in this codebase |
| [`docs/agent-curation.md`](docs/agent-curation.md) | curating the data with a coding agent, via the HTTP API |
| [`docs/deploy.md`](docs/deploy.md) | deploying to the server |
| [`DATA.md`](DATA.md) | where the curated data comes from |
