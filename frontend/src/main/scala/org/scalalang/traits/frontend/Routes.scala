package org.scalalang.traits.frontend

import com.raquo.waypoint.*
import upickle.default.*

enum Page derives ReadWriter:
  case Home(version: Option[String] = None, q: Option[String] = None)
  case Sips
  case Versions
  case Login
  case NewEntry
  case EntryView(slug: String)
  case EditEntry(slug: String)

object Routes:

  // Route.applyPF (not Route.static): static + ClassTag fails at runtime against Scala 3 enum
  // singletons.
  private def staticRoute(page: Page, segments: List[String]): Route[Page, Unit] =
    Route.applyPF[Page, Unit](
      matchEncode = { case p if p == page => () },
      decode = _ => page,
      pattern = segments.foldLeft(root)(_ / _) / endOfSegments
    )

  // The board's picked version and text filter live in the URL (`/?v=3.4&q=tuple`) so board
  // states are shareable and survive reload. Both params are optional; bare `/` still matches.
  private val homeRoute = Route.onlyQuery[Page.Home, (Option[String], Option[String])](
    encode = p => (p.version, p.q),
    decode = args => Page.Home(args._1, args._2),
    pattern = (root / endOfSegments) ? (param[String]("v").? & param[String]("q").?)
  )

  private val sipsRoute = staticRoute(Page.Sips, List("sips"))

  private val versionsRoute = staticRoute(Page.Versions, List("versions"))

  private val loginRoute = staticRoute(Page.Login, List("login"))

  private val newEntryRoute = staticRoute(Page.NewEntry, List("new"))

  private val entryRoute = Route[Page.EntryView, String](
    encode = _.slug,
    decode = Page.EntryView(_),
    pattern = root / "entries" / segment[String] / endOfSegments
  )

  private val editEntryRoute = Route[Page.EditEntry, String](
    encode = _.slug,
    decode = Page.EditEntry(_),
    pattern = root / "entries" / segment[String] / "edit" / endOfSegments
  )

  val allRoutes: List[Route[? <: Page, ?]] =
    List(homeRoute, sipsRoute, versionsRoute, loginRoute, newEntryRoute, editEntryRoute, entryRoute)

  lazy val router: Router[Page] = new Router[Page](
    routes = allRoutes,
    serializePage = write(_),
    deserializePage = read[Page](_),
    getPageTitle = _ => "Traits",
    routeFallback = _ => Page.Home()
  )

  def urlFor(page: Page): String =
    allRoutes.iterator.flatMap(_.relativeUrlForPage(page)).nextOption().getOrElse("/")
