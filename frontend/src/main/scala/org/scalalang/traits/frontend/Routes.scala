package org.scalalang.traits.frontend

import com.raquo.waypoint.*
import upickle.default.*

enum Page derives ReadWriter:
  case Home
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

  private val homeRoute = staticRoute(Page.Home, Nil)

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
    routeFallback = _ => Page.Home
  )

  def urlFor(page: Page): String =
    allRoutes.iterator.flatMap(_.relativeUrlForPage(page)).nextOption().getOrElse("/")
