# Traits v2 — what we track, and how we model it

**Status: decisions settled, ready for review with others.** Drafts 1 and 2
raised thirteen open questions; all are now closed, and the model came out
considerably simpler than the first attempt. This document records what we
track, the lifecycle, the data, the views, how other languages solve the same
problem, and a plan of work.

**[verified]** = checked against a process spec, reference docs, or a live API
on 2026‑08‑11.

## 0. What this document covers — and what it doesn't

This is a **delta against the shipped v1**, not a complete design of the app. It
covers what we track, the lifecycle, the structured data, the views, and the
overlap with other tools.

It deliberately does not restate the decisions that v2 leaves untouched. Those
stay in [`PLAN.md`](../PLAN.md) and are still in force:

| Still valid, in `PLAN.md` | |
| --- | --- |
| §4 | content is freeform markdown sections |
| §5 | "where it stands" is derived, never stored — v2 leans on this harder |
| §6 | links are typed and double as the curating agent's reading list |
| §7 | humans curate; the AI only proposes |
| §8 | storage: one SQLite file, each entry a JSON document |
| §9–§12 | stack, auth, AI-curation-outside-the-app, the name |

**`PLAN.md` is stale where it disagrees with this document.** Specifically, v2
supersedes: §2 (availability as a single current state — now an event list),
the Views section (no changelog; version picker and SIP board are new), the
Roadmap (the version matrix is now core, not future; label sync is limited to
SIP state), and Build status (describes v1).

Once this is agreed, the two documents should be merged back into one rather
than left to drift — see Phase 6.

---

## 1. What we track

An entry is **one notable change to Scala**: a language feature, a library
addition, or anything else a user should know about — including things like
raising the minimum required JDK. If it has a SIP, the SIP is *part of* the
entry, never a separate entry. A follow-up change that has no SIP of its own is
a note in the description, not a new entry.

**No kind field.** Compiler / language / library / research features do not need
to be distinguished in structured data. The only real difference is which stages
they reach — library changes never get `Preview`, research features never reach
`Stable` — and that is expressed by simply not assigning those stages. Nothing
downstream needs to branch on a kind.

### What gets an entry at all

Only larger, significant changes belong here; everything else stays in the
release notes. This line gets drawn hundreds of times by different people, so it
is written down here rather than left to taste — otherwise entries drift, and a
user can no longer read "not in Traits" as "doesn't exist in Scala", which is
most of the app's value.

| Change type | Bar | Judgement |
| --- | --- | --- |
| Has a SIP | always include | none |
| Standard library | went through SLC ⇒ include | none — the process *is* the bar |
| Compiler change, no SIP | the test below | yes |

For the last row: **would someone reasonably ask "can I use X in Scala 3.N?"**
If yes, include it. That is the app's own central question, so the bar stays
self-consistent no matter who applies it.

* Include: capture checking; raising the minimum JDK; removing a deprecated
  method; anything that changes what compiles.
* Exclude: a pattern-matcher crash fix; a faster typer; better error wording; a
  new `-Y` debug flag.
* Borderline (arbitrated, not guessed): a new warning that breaks `-Werror`
  builds; inference changes that alter what compiles.

Borderline calls are arbitrated informally by the small group doing the
curation, which is not expected to be contentious in practice. The point of
writing the bar down is consistency over time, not adjudication.

`tags` stays, and is now load-bearing: with no kind/route/surface field, it is
the only categorization axis — the only way to filter "syntax" or "library".

---

## 2. Lifecycle

### 2.1 Proposal track (SIP) — verified, unchanged

**[verified]** The current `SipState` enum is faithful to the process
specification: all ten states map 1:1 onto its stage/status/recommendation
labels, both vote-requested states carrying a recommendation. **Keep as is.**

Decided: we do *not* distinguish "rejected at design" from "rejected at
implementation". Decided: the spec's "no author reply for two months ⇒
withdrawn" is theoretical and we won't automate against it.

### 2.2 Availability entries

A topic has a list of availability entries. Each is a stage plus, usually, a
Scala minor version:

| Stage | Version | Carries forward |
| --- | --- | --- |
| `PullRequest` | none | n/a |
| `Experimental` | yes | yes |
| `Preview` | yes | yes |
| `Stable` | yes | yes |
| `Deprecated` | yes | yes |
| `Removed` | yes | **no — see §3.3** |

`PullRequest` is a real stage, kept because "this is just an idea" and "this has
an implementation in flight" are genuinely different things to a reader. It is
the one stage with no version. PR *links* live in `links` regardless of stage.

Minor versions only (`3.3`, `3.8`) — patch versions are not modelled.

**Out of scope, decided:** `-source` / `-Xsource` migration levels, `-Y`
research flags, and gate taxonomy generally. We represent what a version ships
officially. `-experimental` / `-preview` only change how the compiler treats
features, so they don't need modelling. Also out of scope: deprecation planning
— no `plannedRemoval`, no `replacedBy`, no `migration` fields.

### 2.3 Backports are availability entries — but they need one marker

Decided: backports are just availability entries with a version, and we won't
model `nominated`/`accepted`/`done`. Nominating stays in the PR queue where it
already works, and a database that briefly lags a merged backport PR is fine.

**But an entry must record whether it is a backport, because it cannot be
derived.** This is worth spelling out, since "just another availability entry"
sounds like it needs no extra field:

Take the ordinary case — stable in 3.8, backported to the 3.3 LTS. The entries
are `Stable@3.3` and `Stable@3.8`. Sorted by version that is a perfectly normal
increasing sequence, indistinguishable from a feature that went stable in 3.3
and was re-noted in 3.8. But the two readings disagree about 3.4–3.7: under the
backport reading the feature is absent there, under the other it is stable.
Nothing in the data breaks the tie.

Derivation only works when the entries contradict each other — e.g.
`Stable@3.3, Experimental@3.7` must mean 3.3 is a backport, since a main line
never regresses. That is the rare case, not the common one.

So: **one boolean on the entry.** `backport: Boolean`. Cheap, explicit, and it
directly expresses the semantics: main-line entries carry forward, backport
entries apply to their own version only.

Decided: `backport` is legal only on `Stable`, `Deprecated` and `Removed` — we
don't backport experimental or preview features to a maintained line. That is a
validation rule, not a second type.

### 2.4 Ideas and archiving

Decided:

* An **idea** is an entry with nothing else — no SIP, no availability entries.
  Nothing to store; it's the empty case.
* **Archived** is a flag. It covers discarded ideas *and* features that have
  been deprecated and removed. Archived entries don't show by default.

One distinction worth keeping straight: `archived` is a **display** decision,
while `Removed@3.11` is a **fact**. The version view still needs the fact to
show a tombstone in 3.11 and nothing in 3.12+ (§3.3), whether or not the entry
is archived. Decided: archiving is **manual**, never automatic on removal —
removal is exactly when people search for a thing ("where did `X` go?"), so
auto-hiding at that moment is the worst possible timing. Archived means "no
longer interesting", which is a judgement.

---

## 3. The data

### 3.1 Sketch

```
Topic
  slug, title, tagline, sections[], links[], timeline[], tags[]   # unchanged
  archived     : Boolean
  sip          : Option[Sip]              # current stage only, as today
  availability : [ Availability ]

Availability
  stage    : PullRequest | Experimental | Preview | Stable | Deprecated | Removed
  version  : Option[Version]      # None only for PullRequest
  backport : Boolean              # only on Stable | Deprecated | Removed
  note     : Option[String]       # markdown: how to enable in this state (§3.6)
```

Validation: `version` is empty exactly for `PullRequest`; at most one main-line
entry per version; a backport entry needs a version; `backport` may only be set
on `Stable`, `Deprecated` and `Removed`.

Decided: **no structured links between entries.** A `relations` field
(supersedes / superseded-by / part-of) was considered and dropped, as was the
`[[slug]]` cross-reference idea from `PLAN.md`'s roadmap. "This replaced that"
is expressed in prose in `sections`, like any other narrative — no backlinks, no
graph to maintain.

### 3.2 Version registry

Decided: versions live in the database, editable through the web UI by admins
and through the API by agents.

```
Version(major, minor, lts: Boolean, released: Boolean)
```

**[verified]** current landscape: 3.0–3.8 released, 3.8.4 newest patch, 3.3
still receiving patches (3.3.8) alongside 3.8.x — the LTS line. Milestones exist
for 3.9.0 and 3.10.0, so "planned but not released" is a state we must render.

Decided: add a release date and a release-notes link:

```
Version(major, minor, lts: Boolean, released: Boolean,
        releaseDate: Option[Date], releaseNotesUrl: Option[String])
```

The date is not decoration — it is what makes the merged entry history (§3.4)
possible, since interleaving a dated timeline event with a versioned availability
event needs a date for the version. Unreleased versions have no date and sort
last, as "upcoming". Rust's equivalent registry carries the same pair
**[verified]**.

### 3.3 Status in a version

```
statusIn(v) =
  backports.find(_.version == v).map(_.stage)          # backports: exact version only
    orElse mainline.filter(_.version <= v).maxBy(_.version) match
      case Removed at w if w < v  => Absent            # tombstone shows only in w
      case entry                  => entry.stage
    orElse Absent
```

The `Removed` case is the one exception to carry-forward, and it comes straight
from your display rule: removed in 3.11 ⇒ shown in 3.11, marked as removed;
absent from 3.12 on. Every other stage carries forward indefinitely.

Worked example — stable in 3.8, backported to 3.3, removed in 3.11:

| Version | Result | Why |
| --- | --- | --- |
| 3.3 | Stable | backport, exact match |
| 3.4–3.7 | Absent | backport doesn't carry; main line hasn't reached it |
| 3.8–3.10 | Stable | main line, carried forward |
| 3.11 | Removed | tombstone, visually distinct |
| 3.12+ | Absent | removal doesn't carry forward |

### 3.4 Timeline — keep it, but narrow its job

You asked whether it can be dropped or inferred. **Keep it.** Once availability
entries carry versions, roughly half of what the timeline holds today becomes
derivable — an entry reading *"Became a preview feature in 3.8.0"* is exactly
`Preview@3.8` written out in prose, and duplicating it invites the two copies to
disagree.

But the other half has nowhere else to live, and it's the interesting half:

* SIP committee votes and meeting outcomes
* the date a proposal was first discussed, and where
* decisions that changed direction ("scoped down to X", "merged with Y")
* anything about an entry with no availability entries at all — an idea's
  history is *entirely* timeline

So the rule for curators becomes: **the timeline is for dated events that are
not stage transitions.** Worth stating explicitly in the curation guide, or
people will keep writing "shipped in 3.8" by hand.

Decided: the *UI* merges both — an entry's history shows explicit timeline
events and events derived from its availability entries in one list. The data
stays non-redundant; the merge happens at render time. This is what forces a
release date onto `Version` (§3.2): interleaving a dated event with a versioned
one needs a date for the version.

### 3.5 Changelog — dropped

**Decided: there is no changelog view in v2.** The global date-ordered union of
timelines goes away, and so do `ChangelogEntry` and `GET /api/changelog`.

What replaces it, without being built as a separate thing: the **version picker**
answers "what's in 3.9", and the **per-entry merged history** (§3.4) answers
"how did this get here". Those were the two real jobs the changelog was doing.

A version-keyed changelog ("3.9 — named tuples: stable · capture checking:
preview") was considered and rejected for the same reason: the version picker
already groups by version, so it would be a second rendering of one query.

### 3.6 How to enable a feature — prose, not a field

Rust records the literal feature-gate name (`flag = "const_io_structs"`) as a
field **[verified]**, so their site can answer "what do I type to enable this".

Decided: we do the same job with the **existing `note` field on an availability
entry** — markdown, "how to enable in this state" — rather than a new structured
field. It already handles the fact that the incantation varies by stage: a
language import while experimental, `-preview` while preview, nothing once
stable. Structure it later only if prose proves insufficient.

---

## 4. Users, views, API, overlap

### 4.1 Who asks what

| User | Question | View |
| --- | --- | --- |
| User on a fixed version | "Can I use X in 3.3?" | version picker |
| User planning an upgrade | "What's new in 3.9?" | version-keyed changelog |
| LTS user | "Did this get backported?" | entry page, backport entries |
| Library maintainer | "What's deprecated or gone?" | version view, deprecated/removed columns |
| SIP author / committee | "Where is my proposal?" | SIP board by stage |
| Contributor | "Has this been proposed before?" | search |
| Tooling author | machine-readable everything | API |

### 4.2 Views

Decided: **the two tracks get two views, and neither outranks the other.** This
removes the precedence problem rather than solving it — there is no rule
deciding whether a topic's lane comes from its SIP or its availability, because
the two never share a view.

* **Pipeline (default)** — columns are *availability* stages only, which every
  entry has a position in: `Idea` (no availability entries at all) ·
  `PullRequest` · `Experimental` · `Preview` · `Stable` · `Deprecated` ·
  `Removed`. Archived entries are hidden.
* **Version picker** — the same board, computed for a chosen version (§3.3).
  The default pipeline *is* this board set to the latest released version.
* **SIP board** — a separate view, columns are SIP stages, showing only entries
  where `sip` is set.
* **Entry page** — full record including backports, with the merged history of
  explicit and derived events (§3.4).

Two carve-outs keep the default pipeline from emptying at its left-hand end,
since anchoring to the latest released version would otherwise hide everything
in flight:

1. Entries with no versioned availability (ideas, in-flight PRs) always show.
2. Entries whose only availability is in an **unreleased** version show in that
   future stage's column, badged with the version ("Stable · 3.9"). When the
   release ships, nothing needs re-bucketing.

A card in the pipeline carries a SIP badge when `sip` is set, so the proposal
dimension isn't invisible there — but the SIP board is where it's actually
navigable.

### 4.3 API

Decided: API stability is not a concern yet; prioritise consistency and
cleanliness. Reads are already public with a live OpenAPI spec. Additions:
`GET /api/versions`, and version-scoped queries returning computed status.

Worth noting as a caution: Rust's caniuse.rs is a WASM SPA whose data is only
reachable by running the app — its own front page says a static listing "is
planned" **[verified]**. Our API-first stance avoids exactly that trap, and it's
the main structural advantage we have over the tools below.

### 4.4 Overlap — decided: we replace two of them

Decided: **this tool replaces Michal's spreadsheet and org project 4** ("the
evolution of Scala 3"). That makes their contents a migration input, and it
means their owners need to be in the review loop before we start (§5, Phase 0).

Decided: we do **not** sync or mirror the PR-queue labels (`release-notes`,
`backport:*`, `needs-minor-release`). If they're useful on PRs, they stay on
PRs; duplicating them here buys nothing.

| Tool | Relationship |
| --- | --- |
| SIP PR queue + labels | source of truth for SIP state; we mirror the current stage only |
| scala3 PR queue + labels | stays as-is, no sync |
| GitHub projects (LTS, 3.Next) | release planning, stays |
| org project 4 | **replaced by this** |
| Michal's spreadsheet | **replaced by this** |
| Milestones | reference for the version registry |
| Release notes | complementary — they cover everything, we cover the significant |
| `DATA.md` | already lists blog, releases, SIP list, reference; extend it |

The positioning in one line: **every other tool is a work queue keyed by pull
request; this is a durable index keyed by the change itself.**

---

## 5. How other languages solve this

Worth reading before we commit, because one of them made exactly the mistake we
are about to avoid, and another validates a piece we invented.

### Java — JEPs

**[verified]** States: Draft, Submitted, Candidate, Proposed to Target,
Targeted, Integrated, Completed, Closed/Withdrawn. The index groups by
in-flight / submitted / draft / delivered / withdrawn, and each row carries a
target release number and a component area.

Java's preview lifecycle is close to ours: a preview feature is *fully specified
and implemented but impermanent*, explicitly **not** experimental, enabled
all-or-nothing with `--enable-preview`, and it either becomes permanent or is
removed. Two preview rounds is the stated norm — preview in N and N+1, final in
N+2. They also keep two further pre-stable notions we don't have: **incubator**
modules for APIs, and **experimental** HotSpot features unlocked with `-XX`.

**The instructive part is what goes wrong.** A JEP is one *proposal round*, not
one feature. "Structured Concurrency" is nine separate JEPs — 428 (Incubator),
437 (Second Incubator), 453 (Preview), then 462, 480, 499, 505, 525, 533 as
second through seventh preview. Deprecation and removal are yet more JEPs
("Deprecate X for Removal", then "Remove X"). Nowhere is there one page
answering "what is the state of structured concurrency" — the reader stitches
nine documents together.

That is precisely the failure mode your entry decision avoids: **one entry per
change, with rounds as availability entries inside it.** Our version of that
story is one topic with `Experimental@3.4, Preview@3.7, Stable@3.9`. This is the
strongest argument for the design and worth leading with when you present it.

### Kotlin — KEEP and stability levels

**[verified]** Four levels: Experimental, Alpha, Beta, Stable, with opt-in
required below Stable. Two ideas worth noting:

* Levels are **deliberately decoupled from version numbers** — they express rate
  of change and user risk, not a release timeline; something can sit in Alpha
  for many versions. Ours are the opposite: every stage is anchored to a
  version. That's the right call for Scala, since our stages *are* release
  facts, but it's a real difference to be able to explain.
* A stable component may contain experimental sub-parts. We sidestep this by
  choosing granularity per change rather than per component.

### Rust — RFCs, feature gates, caniuse.rs

**[verified]** Lifecycle: RFC → tracking issue → nightly feature gate → FCP and
stabilization report → stable release. Feature gates are *data in the compiler*:
entries move from `rustc_feature/src/unstable.rs` to `accepted.rs` with the
stabilizing version recorded inline — the lifecycle is machine-readable by
construction, which is why their tooling can be generated rather than curated.

`caniuse.rs` is the closest existing thing to what we're building. Its data
layout **[verified]**:

* one directory **per released version**, containing one TOML file per feature
* a separate `unstable/` directory for anything not yet stabilized
* a `versions.toml` registry with `release_date`, `release_notes`,
  `gh_milestone_id`, `blog_post_path`
* per feature: `title`, `flag`, `impl_pr_id`, `tracking_issue_id`,
  `stabilization_pr_id`, `items`, `doc_path`

Two takeaways. First, **the version registry is not something we invented** —
they needed the same thing, with a release date. Second, they can use version as
the *primary key* because a Rust feature has exactly one transition that matters
(unstable → stable). Scala features have several, plus backports, so the same
layout would not survive contact with our data — which is the concrete reason
our model is an event list rather than a version index.

---

## 6. Plan of work

**Phase 0 — agree this document.** One open item remains, A: who arbitrates
borderline compiler changes. It doesn't block the model — it blocks *curation*,
so it must be answered before Phase 4. Review needed from the owners of org
project 4 and the spreadsheet, since we're proposing to replace both.

**Phase 1 — model.** Domain types per §3.1, `statusIn` per §3.3 with the removal
exception, and the validation rules — all with tests. Pure `shared` work, no
storage or UI. Small now that kind, gates, routes, and deprecation planning are
all out.

**Phase 2 — version registry.** New entity, admin UI plus API, seeded from
milestones and releases. Blocks every version-scoped view.

**Phase 3 — views.** Version picker first (it proves the computation), then SIP
board, then version-keyed changelog, then entry pages.

**Phase 4 — curation and re-seed.** Write the inclusion bar and the
timeline-vs-availability rule into the curation guide, then re-enter data from
`DATA.md`'s sources plus the spreadsheet and project 4. This is the long pole,
and it's human work.

**Phase 5 — API.** `GET /api/versions`, version-scoped queries.

**Phase 6 — documentation consolidation.** The model change invalidates parts of
four documents, and leaving them stale is how a reviewer ends up designing
against v1:

| Document | What goes stale |
| --- | --- |
| `PLAN.md` | §2, Views, Roadmap, Build status (see §0) — fold this document in and retire it |
| `README.md` | "Data model" and "Storage" describe the v1 availability field; Roadmap |
| `AGENTS.md` | the domain section describes `Topic.lane` / `headline` / `availability` |
| `docs/agent-curation.md` | every wire shape and the example payload change at Phase 1 |

`agent-curation.md` is the urgent one: it is the contract external curating
agents follow, so it must be updated *with* Phase 1, not after.

### Explicitly not doing

Kind/route/surface fields · gate taxonomy · `-source` and `-Y` levels ·
deprecation planning fields · backport workflow states · syncing scala3 PR
labels · patch versions · replacing any GitHub workflow · tracking bug fixes or
performance work.
