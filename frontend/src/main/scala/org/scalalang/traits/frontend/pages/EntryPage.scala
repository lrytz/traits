package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Session}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*

/** Full detail for one entry: status, SIP, the availability track, freeform sections, links, and
  * the merged history (explicit timeline events + events derived from availability).
  */
object EntryPage:

  def apply(slug: String): HtmlElement =
    val state = Var[Loaded[(Entry, List[Version])]](Loaded.Loading)

    Api.getEntry(slug).zip(Api.listVersions()).foreach {
      case (Right(e), Right(vs)) => state.set(Loaded.Ok((e, vs)))
      case (Left(e), _)          => state.set(Loaded.Failed(e.message))
      case (_, Left(e))          => state.set(Loaded.Failed(e.message))
    }

    Components.container(
      div(
        cls := "flex items-center justify-between",
        Components.pageLink(Page.Home, "← All features", "text-sm"),
        child <-- Session.signedIn.map {
          case true  => Components.pageLink(Page.EditEntry(slug), "Edit", "text-sm font-medium")
          case false => emptyNode
        }
      ),
      div(cls := "mt-3", Components.loaded(state.signal) { case (e, vs) => view(e, vs) })
    )

  private def view(e: Entry, versions: List[Version]): HtmlElement =
    div(
      header(e, versions),
      metaRow(e),
      sections(e),
      links(e),
      history(e, versions)
    )

  private def header(e: Entry, versions: List[Version]): HtmlElement =
    val current = Version.latestReleased(versions).flatMap(e.statusIn)
    val statusBadge: Node = current match
      case Some(a) =>
        Components.badge(
          BoardColumn.label(BoardColumn.of(a.stage)),
          Components.columnClasses(BoardColumn.of(a.stage))
        )
      case None =>
        e.sip match
          case Some(_) => Components.badge("SIP", "bg-amber-100 text-amber-700")
          case None =>
            if e.availability.nonEmpty then Components.columnBadge(BoardColumn.PullRequest)
            else Components.columnBadge(BoardColumn.Idea)
    val statusLine = current
      .map(Components.statusText)
      .orElse(e.sip.map(s => SipState.label(s.state)))
    div(
      cls := "border-b border-slate-200 pb-4 mb-4",
      div(
        cls := "flex items-center gap-3 flex-wrap",
        h1(cls := "text-2xl font-semibold text-slate-900", e.title),
        statusBadge,
        if e.archived then Components.badge("Archived", "bg-slate-200 text-slate-500")
        else emptyNode,
        statusLine.map(t => span(cls := "text-sm text-slate-500", t)).getOrElse(emptyNode)
      ),
      p(cls := "text-slate-600 mt-1", e.tagline),
      if e.tags.isEmpty then emptyNode
      else
        div(
          cls := "flex gap-1.5 mt-2",
          e.tags.map(tag =>
            span(cls := "text-xs bg-slate-100 text-slate-500 rounded px-1.5 py-0.5", tag)
          )
        )
    )

  private def metaRow(e: Entry): HtmlElement =
    div(
      cls := "grid sm:grid-cols-2 gap-4 mb-5",
      e.sip.map(sipCard).getOrElse(emptyNode),
      if e.availability.isEmpty then emptyNode else availabilityCard(e.availability)
    )

  private def sipCard(s: Sip): HtmlElement =
    infoCard(
      "SIP",
      div(
        div(
          cls := "flex items-center gap-2",
          s.number
            .map(n => span(cls := "font-mono text-sm text-slate-700", n))
            .getOrElse(emptyNode),
          a(
            href   := s.url,
            target := "_blank",
            cls    := "text-blue-600 hover:underline text-sm",
            s.title
          )
        ),
        div(cls := "text-sm text-slate-500 mt-1", SipState.label(s.state))
      )
    )

  /** Unversioned (pull request) first, then by version; the whole track, backports included. */
  private def availabilityCard(availability: List[Availability]): HtmlElement =
    val ordered = availability.sortBy(a => (a.version.isDefined, a.version, a.backport))
    infoCard(
      "Availability",
      div(
        cls := "space-y-2",
        ordered.map(a =>
          div(
            div(
              cls := "flex items-center gap-2 text-sm text-slate-700",
              Components.badge(
                AvailabilityStage.label(a.stage),
                Components.columnClasses(BoardColumn.of(a.stage))
              ),
              span(Components.statusText(a))
            ),
            a.note.map(n => div(cls := "mt-1", Components.markdown(n))).getOrElse(emptyNode)
          )
        )
      )
    )

  private def infoCard(label: String, body: HtmlElement): HtmlElement =
    div(
      cls := "bg-white border border-slate-200 rounded-lg p-4",
      div(cls := "text-xs uppercase tracking-wide text-slate-400 mb-2", label),
      body
    )

  private def sections(e: Entry): HtmlElement =
    div(
      cls := "space-y-5 mb-6",
      e.sections.map(s =>
        div(
          h2(cls := "text-lg font-semibold text-slate-800 mb-1", s.heading),
          Components.markdown(s.body)
        )
      )
    )

  private def links(e: Entry): Node =
    if e.links.isEmpty then emptyNode
    else
      div(
        cls := "mb-6",
        h2(cls := "text-lg font-semibold text-slate-800 mb-2", "Links"),
        ul(
          cls := "space-y-1",
          e.links.map(l =>
            li(
              cls := "text-sm flex items-center gap-2",
              span(cls := "text-xs uppercase text-slate-400 w-24 shrink-0", linkKindLabel(l.kind)),
              a(href := l.url, target := "_blank", cls := "text-blue-600 hover:underline", l.title),
              if l.watch then span(cls := "text-xs text-emerald-600", "• watched") else emptyNode
            )
          )
        )
      )

  /** One merged history: explicit timeline events plus events derived from versioned availability
    * entries, dated by the version's release date from the registry. Events in unreleased versions
    * float to the top as upcoming.
    */
  private case class HistoryEvent(
      date: Option[String], // None = upcoming (unreleased version)
      label: String,        // what to show in the date slot
      summary: String,
      sourceUrl: Option[String]
  )

  private def history(e: Entry, versions: List[Version]): Node =
    val explicit = e.timeline.map(t => HistoryEvent(Some(t.date), t.date, t.summary, t.sourceUrl))
    val derived = e.availability.flatMap { a =>
      a.version.map { v =>
        val reg      = versions.find(_.version == v)
        val backport = if a.backport then " (backport)" else ""
        val summary  = s"${AvailabilityStage.label(a.stage)} in Scala ${v.render}$backport"
        reg.filter(_.released).flatMap(_.releaseDate) match
          case Some(date) => HistoryEvent(Some(date), date, summary, reg.flatMap(_.releaseNotesUrl))
          case None =>
            val upcoming = if reg.forall(_.released) then v.render else s"${v.render} · upcoming"
            HistoryEvent(None, upcoming, summary, reg.flatMap(_.releaseNotesUrl))
      }
    }
    val (undated, dated) = (explicit ++ derived).partition(_.date.isEmpty)
    val events           = undated ++ dated.sortBy(_.date)(using Ordering[Option[String]].reverse)
    if events.isEmpty then emptyNode
    else
      div(
        h2(cls := "text-lg font-semibold text-slate-800 mb-2", "History"),
        ul(
          cls := "border-l-2 border-slate-200 ml-2 space-y-3",
          events.map(ev =>
            li(
              cls := "pl-4 relative",
              span(cls := "absolute -left-[5px] top-1.5 w-2 h-2 rounded-full bg-slate-300"),
              div(cls  := "text-xs text-slate-400", ev.label),
              div(cls  := "text-sm text-slate-700", ev.summary),
              ev.sourceUrl
                .map(u =>
                  a(
                    href   := u,
                    target := "_blank",
                    cls    := "text-xs text-blue-600 hover:underline",
                    "source"
                  )
                )
                .getOrElse(emptyNode)
            )
          )
        )
      )

  private def linkKindLabel(k: LinkKind): String = k match
    case LinkKind.Sip         => "SIP"
    case LinkKind.Pr          => "PR"
    case LinkKind.Issue       => "Issue"
    case LinkKind.ForumThread => "Forum"
    case LinkKind.Doc         => "Doc"
    case LinkKind.Other       => "Link"
