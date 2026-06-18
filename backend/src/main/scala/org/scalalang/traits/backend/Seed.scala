package org.scalalang.traits.backend

import org.scalalang.traits.backend.topic.TopicService
import org.scalalang.traits.shared.*

/** Illustrative starter data, inserted once into an empty store so the app has something to show.
  * The facts here are approximate — versions, SIP states and links should be corrected by an editor
  * (that's the whole point of the tool).
  */
object Seed:

  def run(topics: TopicService): Unit =
    all.foreach((slug, input) => topics.put(slug, input))
    scribe.info(s"Seeded ${all.size} illustrative topics")

  private val all: List[(String, TopicInput)] = List(
    "named-tuples" -> TopicInput(
      title = "Named Tuples",
      tagline = "Tuples whose elements have names — lightweight anonymous records.",
      sections = List(
        Section(
          "Overview",
          "Named tuples let you attach names to tuple elements, e.g. `(name = \"Dotty\", age = 3)`, " +
            "and access them as `.name` / `.age`. They double as a lightweight structural record type."
        ),
        Section(
          "How to try it",
          "Add `import scala.language.experimental.namedTuples` in the file, or enable the feature " +
            "project-wide. Available in recent Scala 3 releases as an experimental feature."
        )
      ),
      availability = Some(
        Availability(
          AvailabilityKind.Experimental,
          "3.6.0",
          Some("`import scala.language.experimental.namedTuples`")
        )
      ),
      sip = Some(
        Sip(
          Some("SIP-58"),
          "Named Tuples",
          "https://github.com/scala/improvement-proposals/pull/58",
          SipState.ImplementationUnderReview
        )
      ),
      links = List(
        Link(LinkKind.Sip, "SIP-58 document", "https://github.com/scala/improvement-proposals/pull/58", watch = true),
        Link(LinkKind.ForumThread, "Pre-SIP discussion", "https://contributors.scala-lang.org/", watch = true),
        Link(LinkKind.Doc, "Reference docs", "https://docs.scala-lang.org/scala3/reference/")
      ),
      timeline = List(
        TimelineEntry("2024-05-01", "Accepted for implementation by the SIP committee."),
        TimelineEntry("2024-11-01", "Shipped as an experimental feature.")
      ),
      tags = List("tuples", "records", "types")
    ),
    "better-fors" -> TopicInput(
      title = "Better fors",
      tagline = "Cleaner desugaring and ergonomics for for-comprehensions.",
      sections = List(
        Section(
          "Overview",
          "A set of improvements to `for` comprehensions: a simpler, more uniform desugaring and " +
            "small syntactic conveniences that remove long-standing papercuts."
        )
      ),
      availability = None,
      sip = Some(
        Sip(
          Some("SIP-62"),
          "Better fors",
          "https://github.com/scala/improvement-proposals/pull/62",
          SipState.ImplementationWaiting
        )
      ),
      links = List(
        Link(LinkKind.Sip, "SIP-62 document", "https://github.com/scala/improvement-proposals/pull/62", watch = true),
        Link(LinkKind.ForumThread, "Pre-SIP discussion", "https://contributors.scala-lang.org/", watch = true)
      ),
      timeline = List(
        TimelineEntry("2024-09-01", "Accepted for implementation.")
      ),
      tags = List("for-comprehension", "syntax")
    ),
    "capture-checking" -> TopicInput(
      title = "Capture Checking",
      tagline = "Tracking captured capabilities in types for safe effects and resources.",
      sections = List(
        Section(
          "Overview",
          "Capture checking augments the type system to track which capabilities a value captures, " +
            "enabling safe scoped resources and a foundation for effect tracking. A long-running " +
            "research effort, available experimentally for early adopters."
        ),
        Section(
          "How to try it",
          "Enable with the experimental language import and the relevant compiler flag. Expect the " +
            "design to keep evolving across releases."
        )
      ),
      availability = Some(
        Availability(
          AvailabilityKind.Experimental,
          "3.5.0",
          Some("`import scala.language.experimental.captureChecking`")
        )
      ),
      sip = None,
      links = List(
        Link(LinkKind.Doc, "Capture checking reference", "https://docs.scala-lang.org/scala3/reference/experimental/cc.html", watch = true)
      ),
      timeline = List(
        TimelineEntry("2023-01-01", "First experimental implementation lands.")
      ),
      tags = List("effects", "capabilities", "research")
    ),
    "union-types" -> TopicInput(
      title = "Union Types",
      tagline = "`A | B` types, generally available since Scala 3.0.",
      sections = List(
        Section(
          "Overview",
          "Union types express that a value is one of several types, written `A | B`. They are a " +
            "core part of Scala 3's type system and require no flag."
        )
      ),
      availability = Some(Availability(AvailabilityKind.Stable, "3.0.0", None)),
      sip = None,
      links = List(
        Link(LinkKind.Doc, "Union types reference", "https://docs.scala-lang.org/scala3/reference/new-types/union-types.html")
      ),
      timeline = List(
        TimelineEntry("2021-05-01", "Generally available in Scala 3.0.0.")
      ),
      tags = List("types")
    )
  )
