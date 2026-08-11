# Curating Traits with a coding agent

Traits has no built-in LLM. The data is curated over its HTTP API, so you can
point a coding agent you already run — Claude Code, Claude Desktop, or any
other — at a running instance and have it update entries, re-read linked
discussions, and create new features. **You drive and review; the agent
proposes the writes.**

Pick one of the two setups below, then hand your agent this file. Everything
after the setup sections applies to both.

> This documents the API as it runs today. The data model is being rebuilt —
> see [`PLAN.md`](../PLAN.md) — which will change the shapes below.

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
topics by hand, or copy a SQLite file into `traits-data/`.

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
| `GET /api/health` | public | `{"status":"ok","topicCount":N}` — doubles as a readiness probe |
| `GET /api/topics` | public | All topics, summarised (for lists) |
| `GET /api/topics/{slug}` | public | One topic, in full |
| `GET /api/search?q=` | public | Full-text search |
| `GET /api/changelog?limit=` | public | Flattened timeline, newest first |
| `POST /api/auth/login` | public | Password → session cookie |
| `POST /api/auth/logout` | public | Clears the cookie |
| `GET /api/me` | editor | Current editor, or `401` — the way to test a session |
| `PUT /api/topics/{slug}` | editor | Create or replace a topic |
| `DELETE /api/topics/{slug}` | editor | Delete a topic |

Note `GET /api/me`, not `/api/auth/me` — see the gotcha about wrong paths below.

## Data shapes

A read returns a **`Topic`**; a write sends a **`TopicInput`**, which is a
`Topic` minus `slug` (it's in the path) and `updatedAt` (stamped server-side).
The lane and headline shown in the UI are *derived* from `availability` + `sip`
— never sent.

**Every `TopicInput` field is required**, including the nullable ones: omitting
`availability` or `sip` is a `400`, so send them as explicit `null`. There are
no defaults and no partial updates.

A topic with a SIP (`GET /api/topics/better-fors`):

```json
{
  "slug": "better-fors",
  "title": "Better fors",
  "tagline": "Cleaner desugaring and ergonomics for for-comprehensions.",
  "sections": [{ "heading": "Overview", "body": "A set of improvements…" }],
  "availability": null,
  "sip": {
    "number": "SIP-62",
    "title": "Better fors",
    "url": "https://github.com/scala/improvement-proposals/pull/62",
    "state": "ImplementationWaiting"
  },
  "links": [
    { "kind": "Sip", "title": "SIP-62 document", "url": "https://…", "watch": true }
  ],
  "timeline": [{ "date": "2024-09-01", "summary": "Accepted for implementation." }],
  "tags": ["for-comprehension", "syntax"],
  "updatedAt": "2026-06-18T09:48:20Z"
}
```

`availability` (when present): `{ "kind": "Experimental|Preview|Stable",
"sinceVersion": "3.5.0", "note": "markdown, optional" }`.

### Enum encodings

* `link.kind`: `Sip | Pr | Issue | ForumThread | Doc | Other`
* `availability.kind`: `Experimental | Preview | Stable`
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
fallback. When unsure of a shape, `GET` an existing topic and mirror it.

## Workflows

`$BASE` below is `http://localhost:8080` or `https://traits.ddns.net`; the
cookie jar is always `.traits-cookies`.

**Update an existing entry** — `PUT` is a whole-document *replace*, not a
patch, so always read first and send the full document back. Anything you omit
is deleted:

```bash
curl -sS $BASE/api/topics/better-fors | jq 'del(.slug, .updatedAt)' > topic.json
# edit topic.json
curl -sS -b .traits-cookies -X PUT $BASE/api/topics/better-fors \
  -H 'Content-Type: application/json' -d @topic.json
```

**Re-read discussions** — the agent fetches the `watch: true` links itself (it
has web tools), summarises what changed into a section, advances `sip.state` if
the GitHub labels moved, and appends a dated `timeline` entry — then PUTs the
result for you to review.

**Create a new feature** — `PUT` to a fresh slug with a full `TopicInput` body.

**Delete** — `DELETE $BASE/api/topics/{slug}` (with the cookie).

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
