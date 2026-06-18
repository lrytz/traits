package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.ChangelogEntry
import com.raquo.laminar.api.L.*

/** A flat, reverse-chronological feed of every milestone across all features. */
object ChangelogPage:

  def apply(): HtmlElement =
    val state = Var[Loaded[List[ChangelogEntry]]](Loaded.Loading)

    Api.changelog(Some(200)).foreach {
      case Right(es) => state.set(Loaded.Ok(es))
      case Left(e)   => state.set(Loaded.Failed(e.message))
    }

    Components.container(
      Components.pageTitle("Changelog"),
      Components.subtitle("Every dated milestone across all features, newest first."),
      div(cls := "mt-4", Components.loaded(state.signal)(feed))
    )

  private def feed(entries: List[ChangelogEntry]): HtmlElement =
    if entries.isEmpty then p(cls := "text-slate-400 text-sm", "No milestones recorded yet.")
    else
      ul(
        cls := "space-y-3",
        entries.map(row)
      )

  private def row(e: ChangelogEntry): HtmlElement =
    li(
      cls := "flex gap-3 items-baseline bg-white border border-slate-200 rounded-lg px-4 py-3",
      span(cls := "text-xs font-mono text-slate-400 w-24 shrink-0", e.date),
      div(
        cls := "min-w-0",
        div(
          cls := "flex items-center gap-2",
          Components.laneBadge(e.lane),
          Components.pageLink(Page.TopicView(e.slug), e.title, "font-medium")
        ),
        div(cls := "text-sm text-slate-600 mt-0.5", e.summary),
        e.sourceUrl
          .map(u => a(href := u, target := "_blank", cls := "text-xs text-blue-600 hover:underline", "source"))
          .getOrElse(emptyNode)
      )
    )
