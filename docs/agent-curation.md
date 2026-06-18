# Curating Traits with a coding agent

Traits has no built-in LLM. Instead, the data is curated over its HTTP API, so
you can point a coding agent you already run — Claude Code, Claude Desktop, or
any other — at a running instance and have it update entries, re-read linked
discussions, and create new features. **The human drives and reviews; the agent
proposes the writes.**

## The contract

The server publishes a live OpenAPI 3.1 spec, generated from the actual
endpoints so it can't drift:

* Swagger UI: `http://localhost:8080/docs`
* Machine-readable spec: `http://localhost:8080/docs/docs.yaml`

Hand your agent this file plus that spec URL and it has everything it needs.

## Auth

Reads are public. Writes (`PUT`, `DELETE`) are editor-gated: exchange the shared
password for an HttpOnly session cookie and send it back on writes.

```bash
# sign in, store the cookie
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"password":"let-me-in"}'

# the dev password is "let-me-in"; in prod it's TRAITS_EDITOR_PASSWORD
```

## Endpoints

| Method & path | Auth | Purpose |
| --- | --- | --- |
| `GET /api/topics` | public | All topics, summarised (for lists) |
| `GET /api/topics/{slug}` | public | One topic, in full |
| `GET /api/search?q=` | public | Full-text search |
| `GET /api/changelog?limit=` | public | Flattened timeline, newest first |
| `PUT /api/topics/{slug}` | editor | Create or replace a topic |
| `DELETE /api/topics/{slug}` | editor | Delete a topic |

## Data shapes

A read returns a **`Topic`**; a write sends a **`TopicInput`**, which is a
`Topic` minus the `slug` (it's in the path) and `updatedAt` (stamped
server-side). The lane and headline you see in the UI are *derived* from
`availability` + `sip` — never sent.

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

When unsure of a shape, `GET` an existing topic and mirror it.

## Workflows

**Update an existing entry** — read, modify, write the whole document back:

```bash
curl -s http://localhost:8080/api/topics/better-fors > topic.json
# edit topic.json: drop "slug"/"updatedAt", change what you need
curl -b cookies.txt -X PUT http://localhost:8080/api/topics/better-fors \
  -H 'Content-Type: application/json' -d @topic.json
```

**Re-read discussions** — the agent fetches the `watch: true` links itself (it
has web tools), summarises what changed into a section, advances `sip.state` if
the GitHub labels moved, and appends a dated `timeline` entry — then PUTs the
result for you to review.

**Create a new feature** — `PUT` to a fresh slug with a `TopicInput` body.

**Delete** — `DELETE /api/topics/{slug}` (with the cookie).

## Keeping humans in control

The database stays authoritative and human-curated. Because the agent proposes
each `PUT`/`DELETE` as a visible tool call, you approve every change before it
lands — the AI never writes unattended. For an audit trail, every write stamps
`updatedAt`.
