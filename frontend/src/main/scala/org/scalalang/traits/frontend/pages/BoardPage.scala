package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Routes}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*

/** Home view: the pipeline board, one column per availability stage plus Idea, computed for a
  * picked version (default: the latest released one). The picked version and the text filter are
  * URL query params (`/?v=3.4&q=tuple`) — the URL is the source of truth, written with
  * `replaceState` so typing doesn't pollute history.
  */
object BoardPage:

  private val controlCls =
    "border border-slate-300 rounded-md px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-200"

  def apply(pageSignal: Signal[Page.Home]): HtmlElement =
    val state = Var[Loaded[(List[EntrySummary], List[Version])]](Loaded.Loading)

    Api.listEntries().zip(Api.listVersions()).foreach {
      case (Right(es), Right(vs)) => state.set(Loaded.Ok((es, vs)))
      case (Left(e), _)           => state.set(Loaded.Failed(e.message))
      case (_, Left(e))           => state.set(Loaded.Failed(e.message))
    }

    def update(f: Page.Home => Page.Home): Unit =
      Routes.router.currentPageSignal.now() match
        case h: Page.Home => Routes.router.replaceState(f(h))
        case _            => ()

    Components.containerWide(
      Components.loaded(state.signal) { case (entries, versions) =>
        val released = versions.filter(_.released).map(_.version).toSet
        val default = Version
          .latestReleased(versions)
          .orElse(versions.map(_.version).maxOption)
          .getOrElse(VersionId(3, 0))
        def versionOf(p: Page.Home): VersionId =
          p.version.flatMap(VersionId.parse).getOrElse(default)

        div(
          div(
            cls := "flex flex-wrap items-end justify-between gap-3 mb-5",
            div(
              Components.pageTitle("Scala language features"),
              Components.subtitle("Where every change stands, from idea to stable and beyond.")
            ),
            div(
              cls := "flex items-end gap-3",
              if versions.isEmpty then
                span(cls := "text-xs text-amber-600", "Version registry is empty")
              else
                label(
                  cls := "block",
                  Components.fieldLabel("Scala version"),
                  select(
                    cls := controlCls,
                    controlled(
                      value <-- pageSignal.map(p => versionOf(p).render),
                      onChange.mapToValue --> { s => update(_.copy(version = Some(s))) }
                    ),
                    versions
                      .map(_.version)
                      .sorted
                      .reverse
                      .map(v => option(value := v.render, versionLabel(v, versions)))
                  )
                )
              ,
              input(
                tpe         := "search",
                placeholder := "Filter features…",
                cls         := s"$controlCls w-56",
                controlled(
                  value <-- pageSignal.map(_.q.getOrElse("")),
                  onInput.mapToValue --> { s => update(_.copy(q = Some(s).filter(_.nonEmpty))) }
                )
              )
            )
          ),
          child <-- pageSignal.map(p =>
            board(filter(entries, p.q.getOrElse("")), versionOf(p), released)
          )
        )
      }
    )

  private def versionLabel(v: VersionId, versions: List[Version]): String =
    versions.find(_.version == v) match
      case Some(reg) =>
        val lts        = if reg.lts then " LTS" else ""
        val unreleased = if reg.released then "" else " (unreleased)"
        s"${v.render}$lts$unreleased"
      case None => v.render

  private def filter(entries: List[EntrySummary], term: String): List[EntrySummary] =
    val t = term.trim.toLowerCase
    if t.isEmpty then entries
    else
      entries.filter { e =>
        (e.title + " " + e.tagline + " " + e.tags.mkString(" ") + " " + e.slug).toLowerCase
          .contains(t)
      }

  private def board(
      entries: List[EntrySummary],
      v: VersionId,
      released: Set[VersionId]
  ): HtmlElement =
    val cells =
      entries.flatMap(e => Board.cell(e.availability, e.archived, v, released).map(e -> _))
    val columns = BoardColumn.all.filter {
      case c @ (BoardColumn.Deprecated | BoardColumn.Removed) => cells.exists(_._2.column == c)
      case _                                                  => true
    }
    div(
      cls := "flex flex-col sm:flex-row gap-4 sm:overflow-x-auto pb-4",
      columns.map(c => column(c, cells.filter(_._2.column == c)))
    )

  private def column(c: BoardColumn, items: List[(EntrySummary, BoardCell)]): HtmlElement =
    div(
      cls := "w-full sm:flex-1 sm:min-w-44",
      div(
        cls := "flex items-center gap-2 mb-2",
        Components.columnBadge(c),
        span(cls := "text-xs text-slate-400", items.size.toString)
      ),
      div(
        cls := "space-y-2",
        if items.isEmpty then p(cls := "text-xs text-slate-300 pb-1", "—")
        else items.map(card)
      )
    )

  private def card(item: (EntrySummary, BoardCell)): HtmlElement =
    val (e, cell) = item
    val status: Node = cell.status match
      case Some(a) if cell.upcoming =>
        Components.badge(
          a.version.fold("planned")(v => s"in ${v.render}"),
          "bg-amber-100 text-amber-700"
        )
      case Some(a) => span(cls := "text-xs text-slate-400", Components.statusText(a))
      case None    => emptyNode
    a(
      href := Routes.urlFor(Page.EntryView(e.slug)),
      onClick.preventDefault --> { _ => Routes.router.pushState(Page.EntryView(e.slug)) },
      cls := "block bg-white rounded-lg border border-slate-200 p-3 hover:border-blue-300 hover:shadow-sm transition",
      div(cls := "font-medium text-slate-900 text-sm", e.title),
      div(cls := "text-slate-500 text-xs mt-1 line-clamp-2", e.tagline),
      div(
        cls := "flex items-center gap-2 mt-2",
        status,
        e.sip
          .map(s =>
            span(cls := "text-xs font-mono text-slate-400 ml-auto", s.number.getOrElse("SIP"))
          )
          .getOrElse(emptyNode)
      )
    )
