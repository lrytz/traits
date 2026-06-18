package org.scalalang.traits.frontend.ui

import org.scalalang.traits.frontend.{Markdown, Page, Routes}
import org.scalalang.traits.shared.Lane
import com.raquo.laminar.api.L.*

/** Three-state wrapper for an async load. */
enum Loaded[+A]:
  case Loading
  case Ok(value: A)
  case Failed(message: String)

object Components:

  def container(mods: Mod[HtmlElement]*): HtmlElement =
    div(cls := "max-w-5xl mx-auto px-4 py-6", mods)

  /** Wider variant for board/overview layouts that benefit from horizontal room. */
  def containerWide(mods: Mod[HtmlElement]*): HtmlElement =
    div(cls := "max-w-7xl mx-auto px-4 py-6", mods)

  def pageTitle(text: String): HtmlElement =
    h1(cls := "text-2xl font-semibold text-slate-900", text)

  def subtitle(text: String): HtmlElement =
    p(cls := "text-slate-500 text-sm mt-1", text)

  /** Internal SPA link that updates the Waypoint router and keeps a real href for cmd-click. */
  def pageLink(page: Page, text: String, extraCls: String = ""): HtmlElement =
    a(
      cls := s"text-blue-600 hover:underline $extraCls",
      href := Routes.urlFor(page),
      onClick.preventDefault --> { _ => Routes.router.pushState(page) },
      text
    )

  def laneBadge(lane: Lane): HtmlElement =
    span(
      cls := s"inline-block whitespace-nowrap text-xs font-medium px-2 py-0.5 rounded-full ${laneClasses(lane)}",
      Lane.label(lane)
    )

  private def laneClasses(lane: Lane): String = lane match
    case Lane.Idea         => "bg-slate-100 text-slate-600"
    case Lane.Design       => "bg-amber-100 text-amber-700"
    case Lane.Accepted     => "bg-violet-100 text-violet-700"
    case Lane.Experimental => "bg-orange-100 text-orange-700"
    case Lane.Preview      => "bg-sky-100 text-sky-700"
    case Lane.Stable       => "bg-emerald-100 text-emerald-700"
    case Lane.Closed       => "bg-rose-100 text-rose-700"

  /** Render trusted-after-sanitising markdown into a styled block. */
  def markdown(md: String): HtmlElement =
    div(
      cls := "rich-content text-slate-700 text-sm leading-relaxed",
      onMountCallback(ctx => ctx.thisNode.ref.innerHTML = Markdown.render(md))
    )

  def spinner: HtmlElement =
    div(cls := "text-slate-400 text-sm py-10 text-center", "Loading…")

  def errorBox(message: String): HtmlElement =
    div(cls := "bg-rose-50 text-rose-700 text-sm rounded-md px-4 py-3", message)

  /** Standard async-content switch used by every page. */
  def loaded[A](signal: Signal[Loaded[A]])(view: A => HtmlElement): HtmlElement =
    div(
      child <-- signal.map {
        case Loaded.Loading      => spinner
        case Loaded.Failed(m)    => errorBox(m)
        case Loaded.Ok(value)    => view(value)
      }
    )
