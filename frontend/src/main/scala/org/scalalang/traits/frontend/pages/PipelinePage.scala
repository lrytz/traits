package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Routes}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.{FeatureSummary, Lane}
import com.raquo.laminar.api.L.*

/** Home view: a kanban-style pipeline with one column per [[Lane]], plus a live text filter. */
object PipelinePage:

  def apply(): HtmlElement =
    val state      = Var[Loaded[List[FeatureSummary]]](Loaded.Loading)
    val searchTerm = Var("")

    Api.listTopics().foreach {
      case Right(ts) => state.set(Loaded.Ok(ts))
      case Left(e)   => state.set(Loaded.Failed(e.message))
    }

    Components.containerWide(
      div(
        cls := "flex flex-wrap items-end justify-between gap-3 mb-5",
        div(
          Components.pageTitle("Scala language features"),
          Components.subtitle("Where every feature stands, from idea to generally available.")
        ),
        input(
          tpe         := "search",
          placeholder := "Filter features…",
          cls := "border border-slate-300 rounded-md px-3 py-1.5 text-sm w-56 focus:outline-none focus:ring-2 focus:ring-blue-200",
          onInput.mapToValue --> searchTerm
        )
      ),
      Components.loaded(state.signal) { topics =>
        div(
          child <-- searchTerm.signal.map(term => lanesView(filter(topics, term)))
        )
      }
    )

  private def filter(topics: List[FeatureSummary], term: String): List[FeatureSummary] =
    val t = term.trim.toLowerCase
    if t.isEmpty then topics
    else
      topics.filter { s =>
        (s.title + " " + s.tagline + " " + s.tags.mkString(" ") + " " + s.slug).toLowerCase
          .contains(t)
      }

  private def lanesView(topics: List[FeatureSummary]): HtmlElement =
    val lanes =
      Lane.pipeline ++ (if topics.exists(_.lane == Lane.Closed) then List(Lane.Closed) else Nil)
    div(
      cls := "flex flex-col sm:flex-row gap-4 sm:overflow-x-auto pb-4",
      lanes.map(lane => column(lane, topics.filter(_.lane == lane)))
    )

  private def column(lane: Lane, items: List[FeatureSummary]): HtmlElement =
    div(
      cls := "w-full sm:flex-1 sm:min-w-44",
      div(
        cls := "flex items-center gap-2 mb-2",
        Components.laneBadge(lane),
        span(cls := "text-xs text-slate-400", items.size.toString)
      ),
      div(
        cls := "space-y-2",
        if items.isEmpty then p(cls := "text-xs text-slate-300 pb-1", "—")
        else items.map(card)
      )
    )

  private def card(s: FeatureSummary): HtmlElement =
    a(
      href := Routes.urlFor(Page.TopicView(s.slug)),
      onClick.preventDefault --> { _ => Routes.router.pushState(Page.TopicView(s.slug)) },
      cls := "block bg-white rounded-lg border border-slate-200 p-3 hover:border-blue-300 hover:shadow-sm transition",
      div(cls := "font-medium text-slate-900 text-sm", s.title),
      div(cls := "text-slate-500 text-xs mt-1 line-clamp-2", s.tagline),
      div(
        cls := "flex items-center gap-2 mt-2",
        span(cls := "text-xs text-slate-400", s.headline),
        s.sipNumber.map(n =>
          span(cls := "text-xs font-mono text-slate-400 ml-auto", n)
        ).getOrElse(emptyNode)
      )
    )
