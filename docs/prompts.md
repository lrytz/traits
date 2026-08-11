

❯ so far this project is a vibe code to check with other poeple if something like that would be useful. it seems people like it, so we need to start taking things serious. i'm not yet talking about the data in the app, there we'll probably start over
  from scratch. what we need to figure out:

  1. what kind of features we want to track in the app
  2. the precise lifecycle of a feature
  3. what structured data do we actually need
  4. users and use cases; how this tool overlaps with other tools

  2. and 3. depend on the kind of feature (1.); most likely it's enough to have optional structured data, like we do now: "sip" is optional, "availability" is optional.

  for 1. we have
  - features that have a SIP; they may have an implementation or not.
  - features / larger changes that don't have a SIP; stuff added by the maintainers that might still be worth tracking / finding in the app.
  - changes in the standard library. there's a separate lightweight process for that (SLC): https://github.com/scala/scala3/blob/main/docs/_docs/contributing/procedures/contributing-to-stdlib.md. these changes cannot be experimental.
  - "research" features added as exerimental in the compiler. these might eventually get a SIP, they would need a SIP in order for the implementation to be promoted.
  - ideas being discussed

  for 2.
  - sunsetting: features can get deprecated and eventually be removed. we need to be able to represent that as well.
  - SIP lifecycle is well defined in the SIP process; i will need to double check if what we currently have implemented reflects the process correctly.
  - in the compiler, features can be pull requests, experimental, preview, stable, deprecated, removed. i need to double check again if that's really it, or if there's more to it.
  - standard library changes: no "experimental" or preview for those, so they can be 'pull request', 'stable', 'deprecated', 'removed'
  - backporting: we backport a lot from the current release to the current LTS, and even to the previous LTS which is maintained a in parallel with the current LTS for a year. we need to be able to represent that too.

  for 3., that depends on what features we want to represent (1.), what parts of the lifecycle we need structural data for (2.), what views on the data the app needs to support / expose (4.)

  for 4.
  - a feature lifecycle pipeline view, like we currently have, is useful. but we need to adjust the stages. there are SIP stages, implementation stages, and probably some generic stages like "idea" or "discarded".
  - for SIPs it's enough to store the current stage in structured data. we need a view to see sips by their current stage.
  - for feature implementation status (experimental, preview, stable, deprecated, removed), we need to have structured data by scala minor version (3.3, 3.7, 3.8, etc. we don't need patch versions). we want to be able to see for a feature in what version
  it was introduced, to what version(s) it was backported. we also want a lifecycle / pipeline view where we can select a scala version and then see what features are experimental, preview, ... in that version.
    - the status for a specific scala version is computed. for example, if something was preview in 3.7, stable in 3.8, then it's also stable in 3.9.
    - so experimental, preview, stable, deprecated and removed status carry to the next scala version.
    - 'pull request' status doesn't have a scala version
    - backports have a scala version, but that doesn't carry over. so a backport to 3.3 doesn't make the feature available in 3.4. a backported can turn a feature 'stable' or 'deprecated'. a feature can have multiple backports.
  - for library changes (SLC)
    - 'pull request' status doesn't have a scala version
    - 'stable', 'deprecated' and 'removed' have a scala version, status carries forward.
    - backports of library changes work similar to features
  - there can be features that are in neither pipeline (sip, compiler implementation, library change) yet: i guess that's 'idea' and 'discarded'

  - we want the app to expose the data publicly not only on the website, but also in an API. maybe it's enough to make the read API that already exists public, not sure if we would need to expose other views that the web site supports in API.

  - other tools to track feature progress: the SIP PR queue, with its labels, the scala3 PR queue, the 'release-notes' label there, the scala3 github projects (https://github.com/scala/scala3/projects), there we have projects for LTS, 3.Next, 3.Next
  changes that can only go in the next minor (not in a patch). the goal of our new app is not to replace those.
  - data: https://github.com/orgs/scala/projects/4 ("the evolution of scala 3", a lot of deprecation planning.
  - https://docs.google.com/spreadsheets/d/1DZW9ePBgtyj5gjjByYNCHbbCpM7ppLNotumCVEunLIo/edit?pli=1&gid=0#gid=0 spreadsheet by michal
  - release notes
  - probably we already documented some existing data sources in this repo
  - https://github.com/scala/scala3/milestones

  NOTE: we need to plan now. don't start coding. but feel free to do your research, then let's write a plan what needs to be done. i'll also have to review our design and plan with others.






❯ "rejected at design" vs "at implementation": we don't need to distinguish that in the state.

  the "withdraw after 2 month of no reply" is more in theory, in reality we wouldn't be that strict. so no automation here.

  about representing backport status (nominated, accepted, done), let's not do that. if a backport PR is merged and the traits database is updated before a new LTS patch version is out, things can be a bit out of sync, but that's fine. about nominating
  backports, we won't use the traits tool for that, but continue with the PR queue labels.

  about the other PR labels: we don't want to sync them here. if they are useful as PR labels, that's great, but leave them there. we don't want to duplicate information here unnecessarily.

  'pull request' stage: i think it's useful. we want to know if a feature is just an idea or has a PR, in a way that is a different stage.

  what is one entry: a feature, a language change, a library addition, any other change like the upgrading the minimal required JDK. one SIP generating multiple topics: no, if there's a sip, it's one thing in this DB. if something is a follow-up to a sip
  that itself doesn't have a SIP, that's just a comment in the description. any entry can have a SIP or not, like today. and then it can have multiple implementation/availability entries ('pull request', 'experimental', etc), most of them have an
  associated scala version. backports are also availability entries. pull requests links we keep in the links section, irrespective of the stage.

  not every change in the compiler is going to end up in the app, only larger, significant things. other changes will only appear in the release notes.

  i think we don't need to distinguish between compiler/language/library/research features at all in the structured data. the only difference seems to be that library features don't have 'preview', research features don't reach 'stable' etc, so we just
  don't assign that.

  idea/discarded: good point. let's say ideas are entries that have nothing else, no SIP, no availability. to represent discarded entries, we can have an 'archived' flag. this can also be used for entries/features that are deprecated and removed.
  archived items no longer show up by default.

  -source is also a good point. but let's consider that out of scope, just represent what a version ships officially.

  gate vs stage: we treat 'experimental', 'preview' and 'stable' as stages. -Y is out of scope, -source is out of scope, -experimental / -preview just change how the features are treated.

  the current implementation has a timeline (date, comment, url). do we need to keep that, or can it be dropped / inferred from the remaining data? my guess is keep, what do you say?

  Deprecation planning: none of that. no `plannedRemoval`, no `replacedBy`, no `migration`.

  versions: yes, we want versions in the list. `Version(major, minor, lts: Boolean, released: Boolean)` is enough. versions are in the database, need to be editable by admins (web ui) or agents (API).

  where to show removed features? i would say if something is removed in 3.11, it shows up in 3.11 visually distinct as removed, and does not show up in 3.12+.


  q11: API stbility is not important for now, let's focus on making things consistent, clean.

  q12: yes this tool will replace the spreadsheet and org project 4.

  changelog is a union of all timeline events of individual entries?


  another thing to research: what do other languages do in this area? java (JEPs), rust (RFC Book), kotlin (KEEP)


