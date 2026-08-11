package org.scalalang.traits.shared

import upickle.default.*

import scala.math.Ordering.Implicits.infixOrderingOps

/** A Scala minor version ("3.8"). Patch versions are not modelled. Serialized as the rendered
  * string, both on the wire and in the store.
  */
case class VersionId(major: Int, minor: Int):
  def render: String = s"$major.$minor"

object VersionId:
  given Ordering[VersionId] = Ordering.by(v => (v.major, v.minor))

  def parse(s: String): Option[VersionId] = s match
    case s"$major.$minor" =>
      for
        maj <- major.toIntOption
        min <- minor.toIntOption
      yield VersionId(maj, min)
    case _ => None

  given ReadWriter[VersionId] = readwriter[String].bimap(
    _.render,
    s => parse(s).getOrElse(throw upickle.core.Abort(s"invalid version '$s'"))
  )

/** One row of the version registry — the metadata that can't be derived from entries. The release
  * date is what lets availability events interleave with dated timeline events.
  */
case class Version(
    version: VersionId,
    lts: Boolean,
    released: Boolean,
    releaseDate: Option[String], // ISO-8601 date
    releaseNotesUrl: Option[String]
) derives ReadWriter

object Version:
  def latestReleased(versions: List[Version]): Option[VersionId] =
    versions.filter(_.released).map(_.version).maxOption

/** Body for create / replace of a registry row; the version travels in the path. */
case class VersionInput(
    lts: Boolean,
    released: Boolean,
    releaseDate: Option[String],
    releaseNotesUrl: Option[String]
) derives ReadWriter

/** Vote recommendation a SIP manager brings to the committee. */
enum Recommendation derives ReadWriter:
  case Accept, Reject

/** The four maturity stages of the SIP process. */
enum SipStage derives ReadWriter:
  case PreSip, Design, Implementation, Completed

/** A SIP's position, modelled as exactly the legal `(stage, status[, recommendation])` combinations
  * from the process specification — mirrors the GitHub labels on `scala/improvement-proposals`, so
  * a curating agent can read them off a PR directly.
  */
enum SipState derives ReadWriter:
  case PreSipSubmitted
  case DesignUnderReview
  case DesignVoteRequested(recommendation: Recommendation)
  case ImplementationWaiting
  case ImplementationUnderReview
  case ImplementationVoteRequested(recommendation: Recommendation)
  case CompletedAccepted
  case CompletedShipped
  case Rejected
  case Withdrawn

object SipState:
  def stage(s: SipState): Option[SipStage] = s match
    case PreSipSubmitted                            => Some(SipStage.PreSip)
    case DesignUnderReview | DesignVoteRequested(_) => Some(SipStage.Design)
    case ImplementationWaiting | ImplementationUnderReview | ImplementationVoteRequested(_) =>
      Some(SipStage.Implementation)
    case CompletedAccepted | CompletedShipped => Some(SipStage.Completed)
    case Rejected | Withdrawn                 => None

  def label(s: SipState): String = s match
    case PreSipSubmitted        => "Pre-SIP, submitted"
    case DesignUnderReview      => "Design — under review"
    case DesignVoteRequested(r) => s"Design — vote requested (recommend ${r.toString.toLowerCase})"
    case ImplementationWaiting  => "Implementation — awaiting implementation"
    case ImplementationUnderReview => "Implementation — under review"
    case ImplementationVoteRequested(r) =>
      s"Implementation — vote requested (recommend ${r.toString.toLowerCase})"
    case CompletedAccepted => "Accepted — ships stable next minor"
    case CompletedShipped  => "Completed — shipped"
    case Rejected          => "Rejected"
    case Withdrawn         => "Withdrawn"

/** The stages of the availability track. Every stage except [[PullRequest]] is anchored to a
  * version and carries forward until the next main-line entry supersedes it.
  */
enum AvailabilityStage derives ReadWriter:
  case PullRequest, Experimental, Preview, Stable, Deprecated, Removed

object AvailabilityStage:
  /** Stages a backport is legal on. */
  val backportable: Set[AvailabilityStage] = Set(Stable, Deprecated, Removed)

  def label(s: AvailabilityStage): String = s match
    case PullRequest  => "Pull request"
    case Experimental => "Experimental"
    case Preview      => "Preview"
    case Stable       => "Stable"
    case Deprecated   => "Deprecated"
    case Removed      => "Removed"

/** One point on the availability track. `version` is absent exactly for `PullRequest`. A backport
  * applies to its own version only and does not carry forward.
  */
case class Availability(
    stage: AvailabilityStage,
    version: Option[VersionId],
    backport: Boolean = false,  // only on Stable | Deprecated | Removed
    note: Option[String] = None // markdown: how to enable in this state
) derives ReadWriter

object Availability:

  /** The availability entry in effect in `v`: a backport for exactly `v`, else the latest main-line
    * entry at or before `v` (main-line entries carry forward).
    */
  def statusIn(availability: List[Availability], v: VersionId): Option[Availability] =
    availability
      .find(a => a.backport && a.version.contains(v))
      .orElse(
        availability
          .filter(a => !a.backport && a.version.exists(_ <= v))
          .maxByOption(_.version)
      )

  def validate(availability: List[Availability]): List[String] =
    val perEntry = availability.flatMap { a =>
      val versionRule =
        if a.stage == AvailabilityStage.PullRequest then
          Option.when(a.version.isDefined)("a PullRequest availability must not have a version")
        else Option.when(a.version.isEmpty)(s"a ${a.stage} availability needs a version")
      val backportRule = Option.when(a.backport && !AvailabilityStage.backportable(a.stage))(
        s"backport is not allowed on ${a.stage}"
      )
      versionRule.toList ++ backportRule
    }
    val onePullRequest = Option.when(
      availability.count(_.stage == AvailabilityStage.PullRequest) > 1
    )("at most one PullRequest availability")
    def duplicates(backport: Boolean, what: String): List[String] =
      availability
        .filter(a => a.backport == backport && a.version.isDefined)
        .groupBy(_.version)
        .collect {
          case (Some(v), as) if as.sizeIs > 1 =>
            s"more than one $what for version ${v.render}"
        }
        .toList
    perEntry ++ onePullRequest ++
      duplicates(backport = false, "main-line availability") ++
      duplicates(backport = true, "backport")

/** A pipeline-board column: the availability stages plus `Idea` for entries with no availability at
  * all.
  */
enum BoardColumn:
  case Idea, PullRequest, Experimental, Preview, Stable, Deprecated, Removed

object BoardColumn:
  val all: List[BoardColumn] = values.toList

  def of(s: AvailabilityStage): BoardColumn = s match
    case AvailabilityStage.PullRequest  => PullRequest
    case AvailabilityStage.Experimental => Experimental
    case AvailabilityStage.Preview      => Preview
    case AvailabilityStage.Stable       => Stable
    case AvailabilityStage.Deprecated   => Deprecated
    case AvailabilityStage.Removed      => Removed

  def label(c: BoardColumn): String = c match
    case Idea         => "Idea"
    case PullRequest  => "Pull request"
    case Experimental => "Experimental"
    case Preview      => "Preview"
    case Stable       => "Stable"
    case Deprecated   => "Deprecated"
    case Removed      => "Removed"

/** An entry's placement on the per-version board. `status` is the availability entry behind the
  * placement (absent only for `Idea`); `upcoming` marks the carve-out where the entry's first
  * availability is in an unreleased version, shown badged with that version.
  */
case class BoardCell(column: BoardColumn, status: Option[Availability], upcoming: Boolean)

object Board:

  /** Where an entry shows on the board computed for `v`, or `None` if hidden there. Hidden:
    * archived entries; entries removed before `v` (removal carries forward in the data but is shown
    * only in the version it happened); entries whose availability starts only in a later,
    * already-released version.
    */
  def cell(
      availability: List[Availability],
      archived: Boolean,
      v: VersionId,
      released: VersionId => Boolean
  ): Option[BoardCell] =
    if archived then None
    else
      Availability.statusIn(availability, v) match
        case Some(a) =>
          if a.stage == AvailabilityStage.Removed && !a.version.contains(v) then None
          else Some(BoardCell(BoardColumn.of(a.stage), Some(a), upcoming = false))
        case None =>
          val mainline = availability.filter(a => !a.backport && a.version.isDefined)
          mainline.minByOption(_.version) match
            case Some(next) if !next.version.forall(released) =>
              Some(BoardCell(BoardColumn.of(next.stage), Some(next), upcoming = true))
            case Some(_) => None
            case None =>
              availability.find(_.stage == AvailabilityStage.PullRequest) match
                case Some(pr) =>
                  Some(BoardCell(BoardColumn.PullRequest, Some(pr), upcoming = false))
                case None =>
                  if availability.isEmpty then
                    Some(BoardCell(BoardColumn.Idea, None, upcoming = false))
                  else None

enum LinkKind derives ReadWriter:
  case Sip, Pr, Issue, ForumThread, Doc, Other

/** A reference shown to users and — when `watch` is set — re-read by a curating agent. */
case class Link(
    kind: LinkKind,
    title: String,
    url: String,
    watch: Boolean = false
) derives ReadWriter

/** A freeform editorial block, rendered as markdown. */
case class Section(heading: String, body: String) derives ReadWriter

/** A dated event that is not a stage transition — votes, meeting outcomes, direction changes.
  * Transitions are derived from `availability` at render time, never written here by hand.
  */
case class TimelineEntry(
    date: String, // ISO-8601 date
    summary: String,
    sourceUrl: Option[String] = None
) derives ReadWriter

/** A reference to a Scala Improvement Proposal. Only the current state is stored; the history lives
  * in the timeline and the PR itself.
  */
case class Sip(
    number: Option[String], // e.g. "SIP-58"; None for an unnumbered pre-proposal
    title: String,
    url: String,
    state: SipState
) derives ReadWriter

/** One notable change to Scala — the full, document-shaped record, persisted whole as JSON. */
case class Entry(
    slug: String,
    title: String,
    tagline: String,
    sections: List[Section],
    links: List[Link],
    timeline: List[TimelineEntry],
    tags: List[String],
    archived: Boolean,
    sip: Option[Sip],
    availability: List[Availability],
    updatedAt: String // ISO-8601 timestamp of last edit
) derives ReadWriter:

  def statusIn(v: VersionId): Option[Availability] = Availability.statusIn(availability, v)

  def summary: EntrySummary =
    EntrySummary(
      slug = slug,
      title = title,
      tagline = tagline,
      tags = tags,
      archived = archived,
      sip = sip,
      availability = availability,
      updatedAt = updatedAt
    )

/** List projection: everything except the freeform content (sections, links, timeline). Carries the
  * full availability list and SIP so boards are computable client-side.
  */
case class EntrySummary(
    slug: String,
    title: String,
    tagline: String,
    tags: List[String],
    archived: Boolean,
    sip: Option[Sip],
    availability: List[Availability],
    updatedAt: String
) derives ReadWriter:
  def statusIn(v: VersionId): Option[Availability] = Availability.statusIn(availability, v)

/** Body for create / replace. The slug travels in the path, `updatedAt` is stamped server-side. */
case class EntryInput(
    title: String,
    tagline: String,
    sections: List[Section],
    links: List[Link],
    timeline: List[TimelineEntry],
    tags: List[String],
    archived: Boolean,
    sip: Option[Sip],
    availability: List[Availability]
) derives ReadWriter:
  def validate: List[String] = Availability.validate(availability)

/** An entry's status within one version, for the version-scoped read API. */
case class EntryStatus(
    slug: String,
    title: String,
    tagline: String,
    archived: Boolean,
    status: Availability
) derives ReadWriter

/** Who is editing. With shared-password auth this is always the same identity, but the type leaves
  * room for per-user identity (e.g. GitHub OAuth) later.
  */
case class Editor(name: String) derives ReadWriter

case class LoginRequest(password: String) derives ReadWriter
