package org.scalalang.traits.frontend

import org.scalalang.traits.frontend.pages.{ChangelogPage, PipelinePage, TopicPage}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    val _ = renderOnDomContentLoaded(dom.document.getElementById("app"), app())

  def app(): HtmlElement =
    div(
      cls := "min-h-screen bg-slate-50 text-slate-900",
      navbar(),
      child <-- Routes.router.currentPageSignal.map(renderPage)
    )

  private def navbar(): HtmlElement =
    div(
      cls := "bg-white border-b border-slate-200",
      div(
        cls := "max-w-5xl mx-auto px-4 h-14 flex items-center gap-6",
        navLink(Page.Home, "Traits", "font-semibold text-slate-900 hover:text-slate-900"),
        div(
          cls := "flex gap-4 text-sm",
          navLink(Page.Home, "Pipeline"),
          navLink(Page.Changelog, "Changelog")
        ),
        span(cls := "ml-auto text-xs text-slate-400 hidden sm:block", "Scala language feature tracker")
      )
    )

  private def navLink(page: Page, text: String, extraCls: String = "text-slate-600 hover:text-slate-900"): HtmlElement =
    a(
      cls := extraCls,
      href := Routes.urlFor(page),
      onClick.preventDefault --> { _ => Routes.router.pushState(page) },
      text
    )

  private def renderPage(page: Page): HtmlElement = page match
    case Page.Home            => PipelinePage()
    case Page.Changelog       => ChangelogPage()
    case Page.TopicView(slug) => TopicPage(slug)
