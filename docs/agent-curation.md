# Curating Traits with a coding agent

Traits has no built-in LLM. The data is curated over its HTTP API, so you can
point a coding agent you already run — Claude Code, Claude Desktop, or any
other — at a running instance and have it update entries, re-read linked
discussions, and create new ones. **You drive and review; the agent proposes
the writes.**

Pick one of the two setups below, then hand your agent this file. Everything
after the setup sections applies to both.

## What the agent needs

This file, plus the live OpenAPI 3.1 spec — generated from the actual endpoints,
so it can't drift from what the server does:

| | Local dev | Live |
| --- | --- | --- |
| Swagger UI | `http://localhost:8080/docs` | `https://traits.ddns.net/docs` |
| Spec | `http://localhost:8080/docs/docs.yaml` | `https://traits.ddns.net/docs/docs.yaml` |

## Setup: local dev instance

Start here if you're experimenting — the data is yours and mistakes cost
nothing.

Prerequisites and the frontend dev server are in the [README](../README.md);
for API curation you only need the backend:

```bash
sbt backend/run          # serves /api on :8080
```

`sbt backend/run` sets `TRAITS_ENV=dev`, which enables the dev fallbacks — so
the editor password is **`let-me-in`** and you don't have to configure
anything. (A packaged jar has no fallbacks: `Config.scala` fails closed and
requires `TRAITS_EDITOR_PASSWORD` and `TRAITS_SESSION_SECRET` explicitly.)

Sign in, storing the session in `.traits-cookies` (gitignored, in the repo root
— the same jar is used for both instances):

```bash
curl -sS -b .traits-cookies -c .traits-cookies \
  -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"password":"let-me-in"}'

curl -sS -b .traits-cookies http://localhost:8080/api/me
# → {"name":"editor"}
```

Login passes **both** `-b` and `-c`: cookies are stored per host, and `-c`
alone rewrites the jar from scratch, dropping the other instance's session.
With both flags one file holds local and live at once. (`-b` on a jar that
doesn't exist yet is a no-op, so the first login is fine.)

A fresh local DB is **empty** — the app never seeds itself. Either curate a few
entries by hand, or copy a SQLite file into `traits-data/`.

## Setup: the live site

Same flow, three differences that all cause confusing failures if missed.

**1. HTTPS is mandatory.** In prod the session cookie is issued with the
`Secure` flag, so a login over `http://` yields a cookie that curl stores but
will never send back — writes then fail with `401 Not signed in` rather than
anything about the protocol. Always write `https://`.

**2. The password is the real one**, set as `TRAITS_EDITOR_PASSWORD` in `.env`
on the server (see [deploy.md](deploy.md)) — there is no fallback. Keep it out
of your shell history, your agent's transcript, and the repo. The repo ignores
a `.secret` file for exactly this:

```bash
# in your own terminal, not through the agent:
read -rs TRAITS_PW && printf '%s' "$TRAITS_PW" > .secret && chmod 600 .secret && unset TRAITS_PW
```

**3. Let the shell read the file**, so the password never passes through the
agent's context:

```bash
curl -sS -b .traits-cookies -c .traits-cookies \
  -X POST https://traits.ddns.net/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg p "$(cat .secret)" '{password:$p}')"

curl -sS -b .traits-cookies https://traits.ddns.net/api/me
# → {"name":"editor"}
```

`jq -n` builds the JSON so a password containing a quote or backslash can't
break the payload. Both `.secret` and `.traits-cookies` are gitignored; the
jar holds a 30-day credential, so treat it like the password.

Sessions last 30 days, so this is one-time setup; repeat it only on a `401`.

## Endpoints

| Method & path | Auth | Purpose |
| --- | --- | --- |
| `GET /api/health` | public | `{"status":"ok","entryCount":N}` — doubles as a readiness probe |
| `GET /api/entries` | public | All entries, summarised (no sections/links/timeline) |
| `GET /api/entries/{slug}` | public | One entry, in full |
| `GET /api/search?q=` | public | Full-text search |
| `GET /api/versions` | public | The version registry, ascending |
| `GET /api/versions/{v}/entries` | public | Every entry with a status in version `v`, and that status |
| `POST /api/auth/login` | public | Password → session cookie |
| `POST /api/auth/logout` | public | Clears the cookie |
| `GET /api/me` | editor | Current editor, or `401` — the way to test a session |
| `PUT /api/entries/{slug}` | editor | Create or replace an entry |
| `DELETE /api/entries/{slug}` | editor | Delete an entry |
| `PUT /api/versions/{v}` | editor | Create or replace a version registry row |
| `DELETE /api/versions/{v}` | editor | Delete a version registry row |

Note `GET /api/me`, not `/api/auth/me` — see the gotcha about wrong paths below.

## Data shapes

The model — the two tracks, what carries forward, what earns an entry — is
specified in [`PLAN.md`](../PLAN.md); read it before curating. On the wire:

A read returns an **`Entry`**; a write sends an **`EntryInput`**, which is an
`Entry` minus `slug` (it's in the path) and `updatedAt` (stamped server-side).
Versions are strings of a Scala **minor** version — `"3.8"`, never `"3.8.1"`.

**Every top-level `EntryInput` field is required**, including the nullable
`sip`: omitting it is a `400`, so send an explicit `null`. There are no partial
updates. (Inside an availability item, `backport` and `note` do default to
`false`/absent, and responses omit them at those defaults.)

A full entry (`GET /api/entries/named-tuples`):

```json
{
  "slug": "named-tuples",
  "title": "Named tuples",
  "tagline": "Tuples with named fields.",
  "sections": [{ "heading": "Overview", "body": "Markdown…" }],
  "links": [
    { "kind": "Sip", "title": "SIP-58 document", "url": "https://…", "watch": true }
  ],
  "timeline": [{ "date": "2024-09-01", "summary": "Accepted by the committee." }],
  "tags": ["types", "syntax"],
  "archived": false,
  "sip": {
    "number": "SIP-58",
    "title": "Named tuples",
    "url": "https://github.com/scala/improvement-proposals/pull/58",
    "state": "CompletedShipped"
  },
  "availability": [
    { "stage": "Experimental", "version": "3.5" },
    { "stage": "Stable", "version": "3.7" }
  ],
  "updatedAt": "2026-06-18T09:48:20Z"
}
```

`availability` is the whole track, one item per stage reached: `stage` is
`PullRequest | Experimental | Preview | Stable | Deprecated | Removed`;
`version` is required for every stage except `PullRequest`, where it must be
absent (`null`); `backport: true` (legal on `Stable`/`Deprecated`/`Removed`)
marks an entry that applies to its own version only; `note` is optional
markdown for how to enable the feature in that state. The server validates all
of this and rejects violations with a `400` listing the problems. Timeline is
only for events that are *not* stage transitions — transitions are derived from
`availability` and would end up duplicated.

A version registry row (`PUT /api/versions/3.9`, all fields required):

```json
{ "lts": false, "released": false, "releaseDate": null, "releaseNotesUrl": null }
```

### Enum encodings

* `link.kind`: `Sip | Pr | Issue | ForumThread | Doc | Other`
* `availability.stage`: `PullRequest | Experimental | Preview | Stable | Deprecated | Removed`
* `sip.state` — a closed set mirroring the `scala/improvement-proposals` labels.
  No-argument states are bare strings; the two vote-requested states carry a
  recommendation and encode as a tagged object:

  | State | JSON |
  | --- | --- |
  | `PreSipSubmitted` | `"PreSipSubmitted"` |
  | `DesignUnderReview` | `"DesignUnderReview"` |
  | `DesignVoteRequested` | `{"$type":"DesignVoteRequested","recommendation":"Accept"}` (or `"Reject"`) |
  | `ImplementationWaiting` | `"ImplementationWaiting"` |
  | `ImplementationUnderReview` | `"ImplementationUnderReview"` |
  | `ImplementationVoteRequested` | `{"$type":"ImplementationVoteRequested","recommendation":"Accept"}` |
  | `CompletedAccepted` | `"CompletedAccepted"` |
  | `CompletedShipped` | `"CompletedShipped"` |
  | `Rejected` | `"Rejected"` |
  | `Withdrawn` | `"Withdrawn"` |

Case names are exact and closed; an unrecognised one is a parse error, not a
fallback. When unsure of a shape, `GET` an existing entry and mirror it.

## Workflows

`$BASE` below is `http://localhost:8080` or `https://traits.ddns.net`; the
cookie jar is always `.traits-cookies`.

**Update an existing entry** — `PUT` is a whole-document *replace*, not a
patch, so always read first and send the full document back. Anything you omit
is deleted:

```bash
curl -sS $BASE/api/entries/named-tuples | jq 'del(.slug, .updatedAt)' > entry.json
# edit entry.json
curl -sS -b .traits-cookies -X PUT $BASE/api/entries/named-tuples \
  -H 'Content-Type: application/json' -d @entry.json
```

**Record a stage transition** — append an item to `availability` (don't touch
the earlier ones; the track is the history): a feature that just went stable in
3.9 gains `{ "stage": "Stable", "version": "3.9" }` next to its existing
`Experimental` item. Don't add a timeline event for it.

**Re-read discussions** — the agent fetches the `watch: true` links itself (it
has web tools), summarises what changed into a section, advances `sip.state` if
the GitHub labels moved, and appends dated `timeline` events for votes and
decisions — then PUTs the result for you to review.

**Create a new entry** — `PUT` to a fresh slug with a full `EntryInput` body.
Whether a change deserves one at all is the bar in `PLAN.md`: would someone
look it up by name, long after it shipped?

**Maintain the version registry** — when a new minor is planned or released
(sources: the scala3 milestones and release notes):

```bash
curl -sS -b .traits-cookies -X PUT $BASE/api/versions/3.9 \
  -H 'Content-Type: application/json' \
  -d '{"lts":false,"released":true,"releaseDate":"2026-05-14","releaseNotesUrl":"https://github.com/scala/scala3/releases/tag/3.9.0"}'
```

**Delete** — `DELETE $BASE/api/entries/{slug}` (with the cookie). Prefer
setting `"archived": true` for anything that was ever real; delete is for
mistakes.

## Gotchas

* **A wrong `/api/...` path returns `200` and HTML, not `404`.** Unmatched
  routes fall through to the SPA's `index.html`, so a typo'd endpoint looks
  like success to a script. Don't treat `200` as proof — pipe through `jq -e`,
  or check that the content type is JSON.
* **`http://` against the live site** 308-redirects, and a login there leaves
  you with an unusable cookie. Use `https://`.
* **`PUT` replaces the whole document.** Omitted fields are dropped, not kept.
* **Unknown JSON keys are silently ignored** on the way in. A stray field
  won't error — it just doesn't do anything. (Misspelling a *required* field
  does error, since the real one is then missing.)
* **The DB is never re-seeded**; deleting the SQLite file loses data
  permanently. The live store is on the `traits-data` volume — back it up
  before bulk edits (`VACUUM INTO` is a safe one-statement snapshot).

## Keeping humans in control

The database stays authoritative and human-curated. Because the agent proposes
each `PUT`/`DELETE` as a visible tool call, you approve every change before it
lands — the AI never writes unattended. For an audit trail, every write stamps
`updatedAt`.
