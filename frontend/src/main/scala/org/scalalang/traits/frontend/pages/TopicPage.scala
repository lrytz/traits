package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Session}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*

/** Full detail for one feature: status, SIP, availability, freeform sections, links, timeline. */
object TopicPage:

  def apply(slug: String): HtmlElement =
    val state = Var[Loaded[Topic]](Loaded.Loading)

    Api.getTopic(slug).foreach {
      case Right(t) => state.set(Loaded.Ok(t))
      case Left(e)  => state.set(Loaded.Failed(e.message))
    }

    Components.container(
      div(
        cls := "flex items-center justify-between",
        Components.pageLink(Page.Home, "← All features", "text-sm"),
        child <-- Session.signedIn.map {
          case true  => Components.pageLink(Page.EditTopic(slug), "Edit", "text-sm font-medium")
          case false => emptyNode
        }
      ),
      div(cls := "mt-3", Components.loaded(state.signal)(view))
    )

  private def view(t: Topic): HtmlElement =
    div(
      header(t),
      metaRow(t),
      sections(t),
      links(t),
      timeline(t)
    )

  private def header(t: Topic): HtmlElement =
    div(
      cls := "border-b border-slate-200 pb-4 mb-4",
      div(
        cls := "flex items-center gap-3 flex-wrap",
        h1(cls := "text-2xl font-semibold text-slate-900", t.title),
        Components.laneBadge(t.lane),
        span(cls := "text-sm text-slate-500", t.headline)
      ),
      p(cls := "text-slate-600 mt-1", t.tagline),
      if t.tags.isEmpty then emptyNode
      else
        div(
          cls := "flex gap-1.5 mt-2",
          t.tags.map(tag =>
            span(cls := "text-xs bg-slate-100 text-slate-500 rounded px-1.5 py-0.5", tag)
          )
        )
    )

  private def metaRow(t: Topic): HtmlElement =
    div(
      cls := "grid sm:grid-cols-2 gap-4 mb-5",
      t.sip.map(sipCard).getOrElse(emptyNode),
      t.availability.map(availabilityCard).getOrElse(emptyNode)
    )

  private def sipCard(s: Sip): HtmlElement =
    infoCard(
      "SIP",
      div(
        div(
          cls := "flex items-center gap-2",
          s.number.map(n => span(cls := "font-mono text-sm text-slate-700", n)).getOrElse(emptyNode),
          a(href := s.url, target := "_blank", cls := "text-blue-600 hover:underline text-sm", s.title)
        ),
        div(cls := "text-sm text-slate-500 mt-1", SipState.label(s.state))
      )
    )

  private def availabilityCard(a: Availability): HtmlElement =
    val kind = a.kind match
      case AvailabilityKind.Experimental => "Experimental"
      case AvailabilityKind.Preview      => "Preview"
      case AvailabilityKind.Stable       => "Stable"
    infoCard(
      "Availability",
      div(
        div(cls := "text-sm text-slate-700", s"$kind since Scala ${a.sinceVersion}"),
        a.note.map(n => div(cls := "mt-1", Components.markdown(n))).getOrElse(emptyNode)
      )
    )

  private def infoCard(label: String, body: HtmlElement): HtmlElement =
    div(
      cls := "bg-white border border-slate-200 rounded-lg p-4",
      div(cls := "text-xs uppercase tracking-wide text-slate-400 mb-2", label),
      body
    )

  private def sections(t: Topic): HtmlElement =
    div(
      cls := "space-y-5 mb-6",
      t.sections.map(s =>
        div(
          h2(cls := "text-lg font-semibold text-slate-800 mb-1", s.heading),
          Components.markdown(s.body)
        )
      )
    )

  private def links(t: Topic): Node =
    if t.links.isEmpty then emptyNode
    else
      div(
        cls := "mb-6",
        h2(cls := "text-lg font-semibold text-slate-800 mb-2", "Links"),
        ul(
          cls := "space-y-1",
          t.links.map(l =>
            li(
              cls := "text-sm flex items-center gap-2",
              span(cls := "text-xs uppercase text-slate-400 w-24 shrink-0", linkKindLabel(l.kind)),
              a(href := l.url, target := "_blank", cls := "text-blue-600 hover:underline", l.title),
              if l.watch then span(cls := "text-xs text-emerald-600", "• watched") else emptyNode
            )
          )
        )
      )

  private def timeline(t: Topic): Node =
    if t.timeline.isEmpty then emptyNode
    else
      div(
        h2(cls := "text-lg font-semibold text-slate-800 mb-2", "History"),
        ul(
          cls := "border-l-2 border-slate-200 ml-2 space-y-3",
          t.timeline.sortBy(_.date)(using Ordering[String].reverse).map(e =>
            li(
              cls := "pl-4 relative",
              span(cls := "absolute -left-[5px] top-1.5 w-2 h-2 rounded-full bg-slate-300"),
              div(cls := "text-xs text-slate-400", e.date),
              div(cls := "text-sm text-slate-700", e.summary),
              e.sourceUrl
                .map(u => a(href := u, target := "_blank", cls := "text-xs text-blue-600 hover:underline", "source"))
                .getOrElse(emptyNode)
            )
          )
        )
      )

  private def linkKindLabel(k: LinkKind): String = k match
    case LinkKind.Sip        => "SIP"
    case LinkKind.Pr         => "PR"
    case LinkKind.Issue      => "Issue"
    case LinkKind.ForumThread => "Forum"
    case LinkKind.Doc        => "Doc"
    case LinkKind.Other      => "Link"
