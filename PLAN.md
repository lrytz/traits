# Traits — design and plan

**Traits** tracks notable changes to Scala across their whole life: from an idea
or a pre-SIP discussion, through the
[SIP process](https://docs.scala-lang.org/sips/process-specification.html), into
the compiler as experimental → preview → stable, and eventually to deprecated
and removed. For any change it answers *where does this stand, in which Scala
version, and how do I use it?*

It is public-facing and also a working tool for the SIP committee and the
compiler team. A first version exists as a proof of concept; this document
describes the model we are building for real, and the work to get there.

---

## What we track

An entry is **one notable change to Scala** — a language feature, a library
addition, or anything else users need to know about, such as raising the minimum
required JDK. If a change has a SIP, the SIP is *part of* the entry, never a
separate entry. A follow-up change with no SIP of its own is a note in the
description.

Granularity is per *change*, not per proposal round. Java's JEP index shows the
alternative and why we avoid it: a JEP is one round, so "Structured Concurrency"
is spread across nine of them and no single page answers what state the feature
is in.

### What earns an entry

Traits tracks the **progress of features**. It is not a second copy of the
release notes, which remain the complete record of what changed in each version.
If a change's whole story is "it happened in 3.9", the release notes have
already told it.

The test: **would someone look this up by name, long after it shipped?**

| Change | Bar |
| --- | --- |
| Has a SIP | always |
| Standard library | [SLC](https://github.com/scala/scala3/blob/main/docs/_docs/contributing/procedures/contributing-to-stdlib.md) approval makes it eligible; the test still applies |
| Anything else | the test |

Include: capture checking, named tuples, `into` conversions, raising the minimum
required JDK. Exclude: crash fixes, performance work, error-message wording,
inference tweaks, `-Y` debug flags, and retiring an individual deprecated
method. Sunsetting a whole mechanism is an entry; removing one method is a
release note.

The bar is deliberately high, and it is written down because different people
apply it over years. Set too low, the database becomes a worse-maintained
duplicate of the release notes; held high, "not in Traits" reliably means "not a
tracked feature". Borderline calls are settled informally by the people
curating.

Compiler, library and research changes are **not** distinguished in the data.
The only real difference is which stages they reach: library changes never get
`Preview`, research features never reach `Stable`. We simply don't assign those.

---

## Lifecycle

An entry has two independent tracks. A research feature is experimental with no
proposal; a completed SIP can sit with no implementation. Neither track
outranks the other, and they are never merged into a single status.

### Proposal track — the SIP

`SipState` is a closed enum of exactly the legal `(stage, status,
recommendation)` combinations from the process specification, which mirror the
GitHub labels on `scala/improvement-proposals` 1:1 — so a curating agent can
read a PR's labels and derive the state directly. Only the *current* state is
stored; the history lives in the timeline and in the PR itself.

Rejected and withdrawn are terminal; we don't record which stage they happened
at.

### Availability track — the implementation

A list of stages, each usually anchored to a Scala **minor** version (patch
versions are not modelled):

| Stage | Version | Carries forward |
| --- | --- | --- |
| `PullRequest` | none | n/a |
| `Experimental` | yes | yes |
| `Preview` | yes | yes |
| `Stable` | yes | yes |
| `Deprecated` | yes | yes |
| `Removed` | yes | **no** |

`PullRequest` is the only stage with no version — "just an idea" and "has an
implementation in flight" are different things to a reader. A change that has
merged but not yet shipped is simply an entry on a version that isn't released
yet, so nothing needs rewriting when the release happens.

`Removed` is the one stage that does not carry forward: an entry removed in 3.11
shows in 3.11 marked as removed, and is absent from 3.12 on.

### Backports

A backport is an availability entry with `backport = true`, legal only on
`Stable`, `Deprecated` and `Removed`. It applies to its own version only and
does not carry forward — a backport to 3.3 does not make the change available in
3.4.

The flag is not redundant with the version ordering. "Stable in 3.8, backported
to 3.3" and "stable since 3.3" produce the identical entry list, but disagree
about 3.4–3.7; nothing else in the data breaks the tie.

Deciding *whether* to backport stays in the scala3 PR queue with its
`backport:*` labels. We record only the outcome, and it is fine for the database
to briefly lag a merged backport.

### Ideas and archiving

An **idea** is an entry with nothing else — no SIP, no availability. There is
nothing to store; it is the empty case.

**Archived** is a flag that hides an entry from default listings. It covers
discarded ideas and changes that are long gone. It is always a manual editorial
act, never automatic on removal: removal is exactly when people go looking for
something ("where did `X` go?").

---

## Data model

```
Entry
  slug, title, tagline
  sections     : [ (heading, markdown) ]     # freeform, ordered
  links        : [ Link ]
  timeline     : [ (date, summary, url?) ]
  tags         : [ String ]
  archived     : Boolean
  sip          : Option[Sip]                 # current state only
  availability : [ Availability ]

Availability
  stage    : PullRequest | Experimental | Preview | Stable | Deprecated | Removed
  version  : Option[Version]     # absent only for PullRequest
  backport : Boolean             # only on Stable | Deprecated | Removed
  note     : Option[String]      # markdown: how to enable in this state

Version(major, minor, lts: Boolean, released: Boolean,
        releaseDate: Option[Date], releaseNotesUrl: Option[String])
```

Validation: `version` is absent exactly for `PullRequest`; at most one main-line
entry per version; `backport` only on the three stages above.

**Content is freeform markdown sections** — Overview, History, Discussion
summary, How to try it, whatever fits — not a fixed schema. A named heading also
gives a curating agent a specific target ("update the *Discussion summary*").
Markdown is stored raw and rendered through `marked` + `DOMPurify`.

**Links are typed** (SIP / PR / issue / forum / doc) and carry a `watch` flag:
`watch = true` marks the set an agent re-reads when curating. PR links live here
regardless of what stage the entry is in.

**How to enable a feature is prose**, in the `note` on an availability entry —
a language import while experimental, `-preview` while preview, nothing once
stable. Gating mechanisms are not modelled as data.

**Nothing derivable is stored.** The lane an entry occupies, its headline, and
its status in any given version are all computed, so they cannot contradict the
underlying facts.

**Versions live in the database**, editable by admins in the web UI and by
agents through the API. The release date is not decoration: it is what lets an
entry's history interleave dated timeline events with version-anchored
availability events.

### Status in a version

```
statusIn(v) =
  backports.find(_.version == v).map(_.stage)             # exact version only
    orElse mainline.filter(_.version <= v).maxBy(_.version) match
      case Removed at w if w < v => Absent
      case entry                 => entry.stage
    orElse Absent
```

Worked example — stable in 3.8, backported to 3.3, removed in 3.11:

| Version | Status | Why |
| --- | --- | --- |
| 3.3 | Stable | backport, exact match |
| 3.4–3.7 | Absent | backport doesn't carry; main line hasn't reached it |
| 3.8–3.10 | Stable | main line, carried forward |
| 3.11 | Removed | tombstone, shown distinctly |
| 3.12+ | Absent | removal doesn't carry forward |

### Timeline

Dated events that are **not** stage transitions: SIP votes and meeting outcomes,
when and where a proposal was first discussed, decisions that changed direction.
For an idea with no availability entries, the timeline is its entire history.

Transitions are not written here by hand — "became preview in 3.8" is
`Preview@3.8` in prose, and two copies would eventually disagree. The UI merges
explicit timeline events and events derived from availability entries into one
history at render time.

---

## Views and API

* **Pipeline (home)** — columns are availability stages, which every entry has a
  position in: `Idea` (no availability at all) · `PullRequest` · `Experimental` ·
  `Preview` · `Stable` · `Deprecated` · `Removed`. Computed for the latest
  released version, with two carve-outs so in-flight work stays visible: entries
  with no versioned availability always show, and entries whose only
  availability is in an unreleased version show in that stage's column badged
  with the version. Archived entries are hidden. Cards carry a SIP badge.
* **Version picker** — the same board computed for any chosen version.
* **SIP board** — a separate view, columns are SIP stages, only entries with a
  SIP.
* **Entry page** — the full record, including backports and the merged history.
* **Search** — full-text across titles, taglines, tags and section bodies.

The read API is public and documented by a live OpenAPI spec at `/docs`,
generated from the endpoints so it cannot drift. It exposes entries, search, and
the version registry, including version-scoped status. API stability is not
guaranteed yet; consistency and cleanliness come first.

---

## Architecture

**Stack.** Scala 3 + tapir (direct style, ox) + Magnum on the backend, Scala.js +
Laminar + Waypoint + Vite on the frontend, a shared module holding the domain
model and the tapir endpoint definitions so the wire contract is written once.

**Storage: one SQLite file, each entry a JSON document.** The data is small,
document-shaped, read whole and edited as a unit — the case where a JSON
document beats a normalised schema. One table holds the serialised entry in a
`data` column plus `updated_at` and a denormalised `search_text` blob. The same
upickle codec serves the wire and the store, so there is no translation layer.
SQLite because the database is tiny and low-write: one file, `cp` backups, no
server. Moving to Postgres later would be a configuration change, not a rewrite.

*Evolving the stored format:* adding or dropping a field is free — unknown keys
are ignored and absent ones fall back to defaults. Renaming an enum case is not:
the case name is the stored wire format. That needs a data version in
`PRAGMA user_version`, checked at startup, applying ordered steps over untyped
`ujson` — never over the typed model, whose codec by definition cannot read the
old shape — followed by a pass to rebuild `search_text`.

**Auth.** Public read, editor-gated writes. A shared password is exchanged for
an HMAC-signed session cookie. The session carries an `editor` identity string,
so per-user auth (GitHub OAuth against a committee allowlist) can drop in later
without reshaping the endpoints.

**Curation is by an external agent, and humans approve.** There is no LLM in the
server. Curators point a coding agent they already run at the editor-gated HTTP
API; it re-reads watched links, proposes updated entries, and every write is a
visible change a human approves. No API keys or LLM ops in the app, and curators
use whatever tools they prefer. See [`docs/agent-curation.md`](docs/agent-curation.md).

**Name.** Plain English — the characteristics of the language — quietly carrying
the Scala meaning without being a pun you have to get. `org.scalalang.traits`.

---

## Scope

Every other tool in this space is a work queue keyed by pull request. Traits is a
durable index keyed by the change itself. That is the gap it fills, and the
reason it neither replaces nor is replaced by them.

| Tool | Relationship |
| --- | --- |
| SIP PR queue and labels | source of truth for SIP state; we mirror the current state |
| scala3 PR queue and labels | stays as-is; we do not sync or duplicate it |
| GitHub projects (LTS, 3.Next) | release planning, stays |
| Milestones | reference for the version registry |
| org project 4, "the evolution of Scala 3" | **replaced by this** |
| Michal's feature spreadsheet | **replaced by this** |
| Release notes | complementary: they cover everything, we cover the significant |

Data sources for curation are listed in [`DATA.md`](DATA.md).

### Not doing

Kind, route or surface fields · gating taxonomy · `-source` and `-Y` levels ·
deprecation planning (planned removals, replacements, migration notes) ·
structured links between entries · backport workflow states · patch versions ·
structured vote tallies — discussions and decisions are summarised in prose ·
tracking bug fixes or performance work · replacing any GitHub workflow.

---

## Plan of work

1. **Model.** The types above, `statusIn` with the removal exception, and the
   validation rules, all with tests. Pure `shared` module work — no storage or
   UI. This is the piece worth getting right; the rest is mechanical.
2. **Version registry.** New entity, admin UI and API, seeded from scala3
   milestones and releases. Blocks every version-scoped view.
3. **Views.** Version picker first, since it proves the computation, then the
   SIP board, then entry pages.
4. **Curation and data.** Write the inclusion bar and the
   timeline-vs-availability rule into the curation guide, then enter data from
   the sources in `DATA.md` plus the spreadsheet and org project 4. The data
   starts over from scratch. This is the long pole and it is human work.
5. **API.** Version registry and version-scoped queries, public.

[`docs/agent-curation.md`](docs/agent-curation.md) is the contract external
agents follow, so it has to be updated alongside step 1 rather than after it.
[`README.md`](README.md) and [`AGENTS.md`](AGENTS.md) also describe the current
model and need the same treatment.
