package org.scalalang.traits.frontend

import org.scalalang.traits.frontend.pages.{
  BoardPage,
  EditorPage,
  EntryPage,
  LoginPage,
  SipBoardPage,
  VersionsPage
}
import org.scalalang.traits.frontend.Api.given
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    Session.init()
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
        cls := "max-w-7xl mx-auto px-4 h-14 flex items-center gap-6",
        navLink(Page.Home, "Traits", "font-semibold text-slate-900 hover:text-slate-900"),
        div(
          cls := "flex gap-4 text-sm",
          navLink(Page.Home, "Pipeline"),
          navLink(Page.Sips, "SIPs"),
          navLink(Page.Versions, "Versions")
        ),
        div(
          cls := "ml-auto flex items-center gap-4 text-sm",
          child <-- Session.current.signal.map(editorControls)
        )
      )
    )

  private def editorControls(editor: Option[org.scalalang.traits.shared.Editor]): HtmlElement =
    editor match
      case Some(_) =>
        div(
          cls := "flex items-center gap-3",
          navLink(Page.NewEntry, "+ New entry", "text-blue-600 hover:underline font-medium"),
          button(
            cls := "text-slate-500 hover:text-slate-900",
            "Sign out",
            onClick --> { _ =>
              Api.logout().foreach { _ =>
                Session.clear()
                Routes.router.pushState(Page.Home)
              }
            }
          )
        )
      case None =>
        navLink(Page.Login, "Sign in", "text-slate-600 hover:text-slate-900")

  private def navLink(
      page: Page,
      text: String,
      extraCls: String = "text-slate-600 hover:text-slate-900"
  ): HtmlElement =
    a(
      cls  := extraCls,
      href := Routes.urlFor(page),
      onClick.preventDefault --> { _ => Routes.router.pushState(page) },
      text
    )

  private def renderPage(page: Page): HtmlElement = page match
    case Page.Home            => BoardPage()
    case Page.Sips            => SipBoardPage()
    case Page.Versions        => VersionsPage()
    case Page.Login           => LoginPage()
    case Page.NewEntry        => EditorPage(None)
    case Page.EntryView(slug) => EntryPage(slug)
    case Page.EditEntry(slug) => EditorPage(Some(slug))
