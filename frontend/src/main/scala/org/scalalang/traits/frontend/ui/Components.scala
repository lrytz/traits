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

  // ---- form primitives (editor UI) ----

  val btnPrimary   = "bg-blue-600 text-white rounded-md px-4 py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
  val btnSecondary = "border border-slate-300 text-slate-700 rounded-md px-4 py-2 text-sm hover:bg-slate-50"
  val btnDanger    = "border border-rose-300 text-rose-700 rounded-md px-4 py-2 text-sm hover:bg-rose-50"

  private val controlCls =
    "w-full border border-slate-300 rounded-md px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-200"

  def fieldLabel(text: String): HtmlElement =
    div(cls := "text-xs font-medium uppercase tracking-wide text-slate-500 mb-1", text)

  def field(labelText: String, control: HtmlElement): HtmlElement =
    label(cls := "block", fieldLabel(labelText), control)

  def textInput(v: Var[String], placeholderText: String = ""): HtmlElement =
    input(
      tpe         := "text",
      placeholder := placeholderText,
      cls         := controlCls,
      controlled(value <-- v.signal, onInput.mapToValue --> v)
    )

  def passwordInput(v: Var[String], placeholderText: String, onEnter: () => Unit): HtmlElement =
    input(
      tpe         := "password",
      placeholder := placeholderText,
      cls         := controlCls,
      controlled(value <-- v.signal, onInput.mapToValue --> v),
      onKeyDown.filter(_.key == "Enter") --> { _ => onEnter() }
    )

  def multilineInput(v: Var[String], rowCount: Int = 4, placeholderText: String = ""): HtmlElement =
    textArea(
      rows        := rowCount,
      placeholder := placeholderText,
      cls         := s"$controlCls font-mono leading-relaxed",
      controlled(value <-- v.signal, onInput.mapToValue --> v)
    )

  def checkboxInput(v: Var[Boolean]): HtmlElement =
    input(
      tpe := "checkbox",
      cls := "h-4 w-4 rounded border-slate-300 align-middle",
      controlled(checked <-- v.signal, onInput.mapToChecked --> v)
    )

  /** A single-select bound to a typed `Var[A]`. `key` must be a stable, unique string per option. */
  def selectInput[A](current: Var[A], options: List[A], render: A => String, key: A => String): HtmlElement =
    select(
      cls := controlCls,
      controlled(
        value <-- current.signal.map(key),
        onChange.mapToValue.map(s => options.find(o => key(o) == s).getOrElse(options.head)) --> current
      ),
      options.map(o => option(value := key(o), render(o)))
    )
