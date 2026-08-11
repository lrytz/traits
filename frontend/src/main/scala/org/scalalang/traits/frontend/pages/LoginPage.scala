package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.Components
import org.scalalang.traits.frontend.{Api, Page, Routes, Session}
import org.scalalang.traits.frontend.Api.given
import com.raquo.laminar.api.L.*

/** Shared-password sign-in. On success the HttpOnly cookie is set and [[Session]] flips to editor.
  */
object LoginPage:

  def apply(): HtmlElement =
    val password   = Var("")
    val error      = Var[Option[String]](None)
    val submitting = Var(false)

    def submit(): Unit =
      if password.now().nonEmpty && !submitting.now() then
        submitting.set(true)
        error.set(None)
        Api.login(password.now()).foreach {
          case Right(editor) =>
            submitting.set(false)
            Session.set(editor)
            Routes.router.pushState(Page.Home())
          case Left(e) =>
            submitting.set(false)
            error.set(Some(e.message))
        }

    Components.container(
      div(
        cls := "max-w-sm mx-auto mt-10",
        Components.pageTitle("Sign in"),
        Components.subtitle("Editor access for the SIP committee."),
        div(
          cls := "mt-5 space-y-4",
          Components.field(
            "Password",
            Components.passwordInput(password, "Editor password", () => submit())
          ),
          child <-- error.signal.map {
            case Some(m) => Components.errorBox(m)
            case None    => emptyNode
          },
          button(
            cls := Components.btnPrimary,
            disabled <-- submitting.signal,
            child.text <-- submitting.signal.map(if _ then "Signing in…" else "Sign in"),
            onClick --> { _ => submit() }
          )
        )
      )
    )
