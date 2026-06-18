# Traits — plan & design decisions

This is the design record for **Traits**, a tracker for Scala language features
as they move through the pipeline: from a pre-SIP idea, through the
[SIP committee process](https://docs.scala-lang.org/sips/process-specification.html),
to experimental → preview → generally available. It answers, for any feature:
*where does it stand, in which Scala version, under which flag, and how did it
get there?* It's public-facing but also a working tool for the SIP committee.

## Decisions

Each decision below was made deliberately during design; the rationale matters
as much as the choice.

### 1. The unit of tracking is a loose "Topic", not a SIP

Not everything has a SIP. A topic might be a pre-SIP idea, a feature that
predates the process, or a cluster of work. So the entity is a **Topic** with an
*optional* `Sip`. This keeps the tracker broader than the proposals repo.

### 2. Availability is the *current* state; transitions live in the timeline

Availability is fundamentally a matrix (experimental in 3.x, preview in 3.y,
stable in 3.z). But we don't maintain dense per-version rows. A topic stores only
the **current furthest-along** `(kind, sinceVersion)` — e.g. "Preview since 3.8".
When it moves to stable, you update that one field; the 3.7-experimental fact
isn't repeated, it's just an earlier **timeline** entry. The timeline (dated
milestones) is what the changelog renders and where the progression history
lives.

### 3. SIP status = the closed set of legal label combinations

A SIP's position isn't one flat value — it's the `(stage, status,
recommendation)` triple the `scala/improvement-proposals` GitHub labels encode,
and only a fixed set of combinations is legal (per the process spec). So
`SipState` is a **closed enum of exactly those legal states** (e.g.
`DesignVoteRequested(Accept)`, `ImplementationWaiting`, `CompletedShipped`),
with the four-stage grouping *derived*. Because these mirror the GitHub labels
1:1, the AI can read a PR's labels and propose the exact `SipState` — the single
most reliable thing it can do.

### 4. Content is freeform markdown sections

A topic carries an ordered list of `(heading, markdown)` **sections** — Overview,
History, Discussion summary, How to try it, whatever fits — not a fixed schema.
Freeform is what the committee wanted; a named heading also gives the AI a
specific target ("update the *Discussion summary* section"). Markdown is stored
raw and rendered through `marked` + `DOMPurify` (public page → sanitise).

### 5. "Where it stands" is derived, never stored

The headline status and the pipeline **Lane** are computed from availability +
SIP state (availability wins; else SIP state; else idea). Storing them would risk
contradicting the underlying facts. Same for the version/availability badges.

### 6. Links are typed and double as the AI's reading list

Each `Link` has a kind (SIP/PR/issue/forum/doc) and a `watch` flag. They're shown
to users *and* `watch = true` marks the set an agent re-reads when curating.

### 7. Humans curate; the AI only suggests

The database is **authoritative and human-curated**. The AI never writes
unattended. A curator points a coding agent at the HTTP API; on demand it
re-reads watched links and proposes changes (summary, SIP state, timeline
entries) as visible writes a human reviews and approves. This keeps the
committee in control and makes AI output reviewable as plain diffs.

### 8. Storage: one SQLite file, each topic a JSON document

The data is small, document-shaped, read whole, and edited as a unit — the case
where a JSON document beats a normalized schema. Each topic is the upickle-
serialized `Topic` in a single `data` column, with `updated_at` and a
denormalised `search_text` alongside for listing/search. The model can evolve
(new section, new field) without a SQL migration, and the AI-rewrites-a-section
flow is a whole-document write.

SQLite (not Postgres) because the DB is tiny and low-write: one file, `cp`
backups, no server, and plain Magnum `sql"…"` code. Migrating to
Postgres later would be a config change, not a rewrite.

### 9. A proven, real-world stack

Scala 3 + tapir (direct style, ox) + Magnum on the backend, Scala.js + Laminar +
Waypoint + Vite on the frontend, a shared module for the wire contract. This is
a proven, real-world stack the author already runs; reusing its conventions
(shared body-only endpoints, Repo/Service/Api triad, netty-sync) means little new
to learn.

### 10. Auth: shared password now, per-user later

Public read, editor-gated writes. A single shared password is exchanged for an
HMAC-signed session cookie. The session carries an `editor` identity string, so
GitHub OAuth with a committee allowlist (the handles are literally on the process
page) can drop in later without reshaping the endpoints.

### 11. AI curation lives outside the app

Rather than bake an LLM into the server, the app exposes a live OpenAPI contract
(Swagger UI at `/docs`) over its editor-gated HTTP API. Curators point a coding
agent they already run — Claude Code, Claude Desktop, anything — at a running
instance to update entries, re-read discussions, and create features. No API
keys or LLM ops in the server, and curators use whatever agent and tools they
prefer. See `docs/agent-curation.md`.

### 12. Name: **Traits**

Plain English (the characteristics of the language) that quietly carries the
Scala meaning without being a pun you must "get". `org.scalalang.traits`.

## Views

* **Pipeline (home)** — kanban columns by `Lane` (Idea → Design → Accepted →
  Experimental → Preview → Stable, plus Closed), with a live text filter. The
  "where does everything stand" glance.
* **Feature detail** — full record: status, SIP, availability, freeform sections,
  links, timeline.
* **Changelog** — every dated milestone across all features, newest first; an
  interactive version history.
* **Search** — full-text over topics (currently a client filter + a `LIKE`
  endpoint; can grow to FTS5).

## Build status

Implemented: the shared model, the full backend API (public reads,
shared-password auth, editor-gated writes) with a live OpenAPI spec at `/docs`,
the read-only pipeline/detail/changelog views, and the editor UI (login plus
create/edit/delete). External-agent curation is documented in
`docs/agent-curation.md`. See `README.md` to run it and `AGENTS.md` for
conventions.

## Roadmap

1. **Agent ergonomics** — a bearer-token / API-key auth path for headless
   agents, and optionally an MCP server so agents that speak MCP (Claude
   Desktop) can curate without shell access.
2. **GitHub label sync** — read SIP PR labels directly to propose `SipState`.
3. **Version matrix** view (feature × Scala version × flag).
4. **Cross-topic references** — `[[slug]]` links with automatic backlinks.

## Deliberately deferred

* **Structured votes / committee log** — decided to keep the DB simple: store
  only the `SipState`; summarise discussions, decisions, and votes in freeform
  text rather than modelling vote tallies.
* **Per-version availability rows** — superseded by "current state + timeline".
* **A migration tool** — idempotent `CREATE TABLE IF NOT EXISTS` is enough at
  this size; add Flyway-equivalent only if the schema gets real.
* **OAuth / per-user identity** — shared password is enough to start; the
  `editor` field leaves the door open.
