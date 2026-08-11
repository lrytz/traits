package org.scalalang.traits.shared

import upickle.default.{read, write}

class VersionIdSuite extends munit.FunSuite:

  test("parse and render") {
    assertEquals(VersionId.parse("3.8"), Some(VersionId(3, 8)))
    assertEquals(VersionId.parse("3.10"), Some(VersionId(3, 10)))
    assertEquals(VersionId(3, 8).render, "3.8")
    assertEquals(VersionId.parse("3"), None)
    assertEquals(VersionId.parse("3.8.1"), None)
    assertEquals(VersionId.parse("3.x"), None)
    assertEquals(VersionId.parse(""), None)
  }

  test("ordering is numeric, not lexicographic") {
    val sorted = List(VersionId(3, 10), VersionId(3, 2), VersionId(3, 9)).sorted
    assertEquals(sorted, List(VersionId(3, 2), VersionId(3, 9), VersionId(3, 10)))
  }

  test("serializes as the rendered string") {
    assertEquals(write(VersionId(3, 8)), "\"3.8\"")
    assertEquals(read[VersionId]("\"3.8\""), VersionId(3, 8))
    intercept[Exception](read[VersionId]("\"nope\""))
  }

class StatusInSuite extends munit.FunSuite:

  private def v(s: String) = VersionId.parse(s).get
  private def main(stage: AvailabilityStage, version: String) =
    Availability(stage, Some(v(version)))
  private def backport(stage: AvailabilityStage, version: String) =
    Availability(stage, Some(v(version)), backport = true)

  // The worked example from PLAN.md: stable in 3.8, backported to 3.3, removed in 3.11.
  private val availability = List(
    main(AvailabilityStage.Stable, "3.8"),
    backport(AvailabilityStage.Stable, "3.3"),
    main(AvailabilityStage.Removed, "3.11")
  )

  test("backport matches its exact version only") {
    assertEquals(
      Availability.statusIn(availability, v("3.3")),
      Some(backport(AvailabilityStage.Stable, "3.3"))
    )
    assertEquals(Availability.statusIn(availability, v("3.4")), None)
    assertEquals(Availability.statusIn(availability, v("3.7")), None)
  }

  test("main line carries forward from its version") {
    for version <- List("3.8", "3.9", "3.10") do
      assertEquals(
        Availability.statusIn(availability, v(version)),
        Some(main(AvailabilityStage.Stable, "3.8"))
      )
  }

  test("removed carries forward too") {
    for version <- List("3.11", "3.12", "3.15") do
      assertEquals(
        Availability.statusIn(availability, v(version)),
        Some(main(AvailabilityStage.Removed, "3.11"))
      )
  }

  test("nothing before the first entry") {
    assertEquals(Availability.statusIn(availability, v("3.0")), None)
  }

  test("a PullRequest entry never provides a status") {
    val pr = List(Availability(AvailabilityStage.PullRequest, None))
    assertEquals(Availability.statusIn(pr, v("3.8")), None)
  }

class BoardSuite extends munit.FunSuite:

  private def v(s: String)             = VersionId.parse(s).get
  private val released: Set[VersionId] = (0 to 8).map(VersionId(3, _)).toSet
  private def cell(availability: List[Availability], at: String, archived: Boolean = false) =
    Board.cell(availability, archived, v(at), released)

  private def main(stage: AvailabilityStage, version: String) =
    Availability(stage, Some(v(version)))

  test("status places the entry in its stage's column") {
    val a = List(main(AvailabilityStage.Stable, "3.6"))
    assertEquals(
      cell(a, "3.8"),
      Some(BoardCell(BoardColumn.Stable, Some(main(AvailabilityStage.Stable, "3.6")), false))
    )
  }

  test("removed shows only in the version it happened") {
    val a = List(main(AvailabilityStage.Stable, "3.4"), main(AvailabilityStage.Removed, "3.6"))
    assertEquals(cell(a, "3.6").map(_.column), Some(BoardColumn.Removed))
    assertEquals(cell(a, "3.7"), None)
    assertEquals(cell(a, "3.5").map(_.column), Some(BoardColumn.Stable))
  }

  test("archived entries are hidden") {
    val a = List(main(AvailabilityStage.Stable, "3.6"))
    assertEquals(cell(a, "3.8", archived = true), None)
  }

  test("no availability at all is an idea") {
    assertEquals(cell(Nil, "3.8"), Some(BoardCell(BoardColumn.Idea, None, false)))
  }

  test("a PullRequest-only entry shows in the PullRequest column") {
    val pr = Availability(AvailabilityStage.PullRequest, None)
    assertEquals(cell(List(pr), "3.8"), Some(BoardCell(BoardColumn.PullRequest, Some(pr), false)))
  }

  test("availability only in an unreleased version shows upcoming, badged") {
    val a = List(main(AvailabilityStage.Experimental, "3.9"))
    assertEquals(
      cell(a, "3.8"),
      Some(BoardCell(BoardColumn.Experimental, Some(a.head), upcoming = true))
    )
  }

  test("upcoming wins over an open pull request") {
    val pr = Availability(AvailabilityStage.PullRequest, None)
    val a  = List(pr, main(AvailabilityStage.Experimental, "3.9"))
    assertEquals(
      cell(a, "3.8").map(c => (c.column, c.upcoming)),
      Some((BoardColumn.Experimental, true))
    )
  }

  test("availability starting in a later released version is hidden on older boards") {
    val a = List(main(AvailabilityStage.Experimental, "3.6"))
    assertEquals(cell(a, "3.4"), None)
  }

class ValidateSuite extends munit.FunSuite:

  private def v(s: String) = VersionId.parse(s).get

  test("a valid availability list passes") {
    val a = List(
      Availability(AvailabilityStage.PullRequest, None),
      Availability(AvailabilityStage.Experimental, Some(v("3.5"))),
      Availability(AvailabilityStage.Stable, Some(v("3.8"))),
      Availability(AvailabilityStage.Stable, Some(v("3.3")), backport = true)
    )
    assertEquals(Availability.validate(a), Nil)
  }

  test("PullRequest must not have a version; other stages need one") {
    assertEquals(
      Availability.validate(List(Availability(AvailabilityStage.PullRequest, Some(v("3.8"))))).size,
      1
    )
    assertEquals(
      Availability.validate(List(Availability(AvailabilityStage.Stable, None))).size,
      1
    )
  }

  test("backport is only legal on Stable, Deprecated, Removed") {
    val bad =
      Availability.validate(
        List(Availability(AvailabilityStage.Experimental, Some(v("3.5")), backport = true))
      )
    assertEquals(bad.size, 1)
    val ok = AvailabilityStage.backportable.toList.flatMap(s =>
      Availability.validate(List(Availability(s, Some(v("3.3")), backport = true)))
    )
    assertEquals(ok, Nil)
  }

  test("at most one main-line entry per version, one backport per version, one PullRequest") {
    val dupMain = List(
      Availability(AvailabilityStage.Experimental, Some(v("3.5"))),
      Availability(AvailabilityStage.Preview, Some(v("3.5")))
    )
    assertEquals(Availability.validate(dupMain).size, 1)

    val dupBackport = List(
      Availability(AvailabilityStage.Stable, Some(v("3.3")), backport = true),
      Availability(AvailabilityStage.Deprecated, Some(v("3.3")), backport = true)
    )
    assertEquals(Availability.validate(dupBackport).size, 1)

    val dupPr = List(
      Availability(AvailabilityStage.PullRequest, None),
      Availability(AvailabilityStage.PullRequest, None)
    )
    assertEquals(Availability.validate(dupPr).size, 1)
  }

class SerializationSuite extends munit.FunSuite:

  private def v(s: String) = VersionId.parse(s).get

  test("availability round-trips; enum cases are bare strings, version a string") {
    val a    = Availability(AvailabilityStage.Stable, Some(v("3.8")), backport = true)
    val json = write(a)
    assert(json.contains("\"Stable\""), json)
    assert(json.contains("\"3.8\""), json)
    assertEquals(read[Availability](json), a)
  }

  test("absent backport and note fall back to defaults") {
    val parsed = read[Availability]("""{"stage":"Experimental","version":"3.5"}""")
    assertEquals(parsed, Availability(AvailabilityStage.Experimental, Some(v("3.5"))))
  }

  test("entry round-trips, unknown keys are ignored") {
    val entry = Entry(
      slug = "named-tuples",
      title = "Named tuples",
      tagline = "Tuples with named fields.",
      sections = List(Section("Overview", "…")),
      links = List(Link(LinkKind.Sip, "SIP-58", "https://example.org", watch = true)),
      timeline = List(TimelineEntry("2024-01-01", "Accepted.")),
      tags = List("types"),
      archived = false,
      sip = Some(
        Sip(Some("SIP-58"), "Named tuples", "https://example.org", SipState.CompletedShipped)
      ),
      availability = List(Availability(AvailabilityStage.Stable, Some(v("3.7")))),
      updatedAt = "2026-01-01T00:00:00Z"
    )
    assertEquals(read[Entry](write(entry)), entry)

    val withExtra = write(entry).init + ",\"bogus\":1}"
    assertEquals(read[Entry](withExtra), entry)
  }

  test("parametric SIP states are tagged objects, singletons bare strings") {
    assertEquals(write[SipState](SipState.Rejected), "\"Rejected\"")
    assertEquals(
      read[SipState]("""{"$type":"DesignVoteRequested","recommendation":"Accept"}"""),
      SipState.DesignVoteRequested(Recommendation.Accept)
    )
  }
