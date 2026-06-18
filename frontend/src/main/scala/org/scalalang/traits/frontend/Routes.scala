package org.scalalang.traits.frontend

import com.raquo.waypoint.*
import upickle.default.*

enum Page derives ReadWriter:
  case Home
  case Changelog
  case Login
  case NewTopic
  case TopicView(slug: String)
  case EditTopic(slug: String)

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

  private val changelogRoute = staticRoute(Page.Changelog, List("changelog"))

  private val loginRoute = staticRoute(Page.Login, List("login"))

  private val newTopicRoute = staticRoute(Page.NewTopic, List("new"))

  private val topicRoute = Route[Page.TopicView, String](
    encode = _.slug,
    decode = Page.TopicView(_),
    pattern = root / "topics" / segment[String] / endOfSegments
  )

  private val editTopicRoute = Route[Page.EditTopic, String](
    encode = _.slug,
    decode = Page.EditTopic(_),
    pattern = root / "topics" / segment[String] / "edit" / endOfSegments
  )

  val allRoutes: List[Route[? <: Page, ?]] =
    List(homeRoute, changelogRoute, loginRoute, newTopicRoute, editTopicRoute, topicRoute)

  lazy val router: Router[Page] = new Router[Page](
    routes = allRoutes,
    serializePage = write(_),
    deserializePage = read[Page](_),
    getPageTitle = _ => "Traits",
    routeFallback = _ => Page.Home
  )

  def urlFor(page: Page): String =
    allRoutes.iterator.flatMap(_.relativeUrlForPage(page)).nextOption().getOrElse("/")
