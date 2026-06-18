package org.scalalang.traits.shared

import upickle.default.*

/** Lifecycle lane used to lay out the pipeline view and colour badges. Derived from a topic's
  * availability and SIP state — never stored — so the headline can't contradict the underlying
  * facts.
  */
enum Lane derives ReadWriter:
  case Idea // pre-SIP / no proposal yet
  case Design // SIP submitted, under committee review
  case Accepted // accepted for implementation, or implemented-but-not-yet-available
  case Experimental // reachable behind -experimental / a language import
  case Preview // reachable behind -preview
  case Stable // generally available
  case Closed // rejected or withdrawn

object Lane:
  val pipeline: List[Lane] = List(Idea, Design, Accepted, Experimental, Preview, Stable)

  def label(l: Lane): String = l match
    case Idea         => "Idea"
    case Design       => "In design"
    case Accepted     => "Accepted"
    case Experimental => "Experimental"
    case Preview      => "Preview"
    case Stable       => "Stable"
    case Closed       => "Closed"

/** Vote recommendation a SIP manager brings to the committee. */
enum Recommendation derives ReadWriter:
  case Accept, Reject

/** The four maturity stages of the SIP process. */
enum SipStage derives ReadWriter:
  case PreSip, Design, Implementation, Completed

/** A SIP's position, modelled as exactly the legal `(stage, status[, recommendation])`
  * combinations from the process specification — mirrors the GitHub labels on
  * `scala/improvement-proposals`, so the AI can read them off a PR directly.
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
    case PreSipSubmitted           => "Pre-SIP, submitted"
    case DesignUnderReview         => "Design — under review"
    case DesignVoteRequested(r)    => s"Design — vote requested (recommend ${r.toString.toLowerCase})"
    case ImplementationWaiting     => "Implementation — awaiting implementation"
    case ImplementationUnderReview => "Implementation — under review"
    case ImplementationVoteRequested(r) =>
      s"Implementation — vote requested (recommend ${r.toString.toLowerCase})"
    case CompletedAccepted => "Accepted — ships stable next minor"
    case CompletedShipped  => "Completed — shipped"
    case Rejected          => "Rejected"
    case Withdrawn         => "Withdrawn"

/** How a feature is reachable in a given release. Only the *current* furthest-along state is kept;
  * earlier transitions live in the [[Topic.timeline]].
  */
enum AvailabilityKind derives ReadWriter:
  case Experimental, Preview, Stable

case class Availability(
    kind: AvailabilityKind,
    sinceVersion: String, // e.g. "3.7.0"
    note: Option[String] = None // markdown: how to enable in this state
) derives ReadWriter

enum LinkKind derives ReadWriter:
  case Sip, Pr, Issue, ForumThread, Doc, Other

/** A reference shown to users and — when `watch` is set — re-read by the AI on enrichment. */
case class Link(
    kind: LinkKind,
    title: String,
    url: String,
    watch: Boolean = false
) derives ReadWriter

/** A freeform editorial block, rendered as markdown. */
case class Section(heading: String, body: String) derives ReadWriter

/** A dated milestone; the union of these across topics is the changelog. */
case class TimelineEntry(
    date: String, // ISO-8601 date
    summary: String,
    sourceUrl: Option[String] = None
) derives ReadWriter

/** A reference to a Scala Improvement Proposal. */
case class Sip(
    number: Option[String], // e.g. "SIP-58"; None for an unnumbered pre-proposal
    title: String,
    url: String,
    state: SipState
) derives ReadWriter

/** The full, document-shaped record for one language feature. Persisted whole as JSON. */
case class Topic(
    slug: String,
    title: String,
    tagline: String,
    sections: List[Section],
    availability: Option[Availability],
    sip: Option[Sip],
    links: List[Link],
    timeline: List[TimelineEntry],
    tags: List[String],
    updatedAt: String // ISO-8601 timestamp of last edit
) derives ReadWriter:

  def lane: Lane = availability match
    case Some(a) =>
      a.kind match
        case AvailabilityKind.Experimental => Lane.Experimental
        case AvailabilityKind.Preview      => Lane.Preview
        case AvailabilityKind.Stable       => Lane.Stable
    case None =>
      sip match
        case Some(s) =>
          s.state match
            case SipState.Rejected | SipState.Withdrawn => Lane.Closed
            case SipState.CompletedShipped              => Lane.Stable
            case SipState.CompletedAccepted             => Lane.Accepted
            case SipState.ImplementationWaiting | SipState.ImplementationUnderReview |
                SipState.ImplementationVoteRequested(_) =>
              Lane.Accepted
            case SipState.DesignUnderReview | SipState.DesignVoteRequested(_) => Lane.Design
            case SipState.PreSipSubmitted                                     => Lane.Idea
        case None => Lane.Idea

  /** Short "where it stands" string for headers and list rows. */
  def headline: String = availability match
    case Some(a) =>
      val kind = a.kind match
        case AvailabilityKind.Experimental => "Experimental"
        case AvailabilityKind.Preview      => "Preview"
        case AvailabilityKind.Stable       => "Stable"
      s"$kind since ${a.sinceVersion}"
    case None =>
      sip match
        case Some(s) => SipState.label(s.state)
        case None    => "Idea / pre-SIP"

  def stableSince: Option[String] =
    availability.collect { case Availability(AvailabilityKind.Stable, v, _) => v }

  def summary: FeatureSummary =
    FeatureSummary(
      slug = slug,
      title = title,
      tagline = tagline,
      lane = lane,
      headline = headline,
      sipNumber = sip.flatMap(_.number),
      stableSince = stableSince,
      tags = tags,
      updatedAt = updatedAt
    )

/** Lightweight projection for list / pipeline views. */
case class FeatureSummary(
    slug: String,
    title: String,
    tagline: String,
    lane: Lane,
    headline: String,
    sipNumber: Option[String],
    stableSince: Option[String],
    tags: List[String],
    updatedAt: String
) derives ReadWriter

/** A changelog row: a [[TimelineEntry]] lifted with its owning topic. */
case class ChangelogEntry(
    date: String,
    slug: String,
    title: String,
    summary: String,
    sourceUrl: Option[String],
    lane: Lane
) derives ReadWriter

/** Body for create / replace. The slug travels in the path, `updatedAt` is stamped server-side. */
case class TopicInput(
    title: String,
    tagline: String,
    sections: List[Section],
    availability: Option[Availability],
    sip: Option[Sip],
    links: List[Link],
    timeline: List[TimelineEntry],
    tags: List[String]
) derives ReadWriter

/** Who is editing. With shared-password auth this is always the same identity, but the type leaves
  * room for per-user identity (e.g. GitHub OAuth) later.
  */
case class Editor(name: String) derives ReadWriter

case class LoginRequest(password: String) derives ReadWriter

/** Request the AI re-read a topic's watched links (plus any `extraUrls`) and propose updates. */
case class EnrichRequest(
    instructions: Option[String] = None,
    extraUrls: List[String] = Nil
) derives ReadWriter

/** AI proposal, surfaced for human review — never auto-applied. */
case class EnrichResult(
    notes: String, // the model's reasoning / caveats
    suggestedSipState: Option[SipState],
    suggestedAvailability: Option[Availability],
    suggestedSections: List[Section],
    suggestedTimeline: List[TimelineEntry],
    discoveredLinks: List[Link]
) derives ReadWriter
