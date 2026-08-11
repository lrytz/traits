package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Session}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The version registry: the minor releases entries anchor to. Public view is a plain table;
  * editors get inline editing, add and delete.
  */
object VersionsPage:

  private final class Draft(v: Version):
    val lts         = Var(v.lts)
    val released    = Var(v.released)
    val releaseDate = Var(v.releaseDate.getOrElse(""))
    val notesUrl    = Var(v.releaseNotesUrl.getOrElse(""))

    def input: VersionInput = VersionInput(
      lts = lts.now(),
      released = released.now(),
      releaseDate = Some(releaseDate.now().trim).filter(_.nonEmpty),
      releaseNotesUrl = Some(notesUrl.now().trim).filter(_.nonEmpty)
    )

  def apply(): HtmlElement =
    val state = Var[Loaded[List[Version]]](Loaded.Loading)
    val error = Var[Option[String]](None)

    def load(): Unit =
      Api.listVersions().foreach {
        case Right(vs) => state.set(Loaded.Ok(vs))
        case Left(e)   => state.set(Loaded.Failed(e.message))
      }
    load()

    def put(v: VersionId, input: VersionInput): Unit =
      Api.putVersion(v, input).foreach {
        case Right(_) =>
          error.set(None)
          load()
        case Left(e) => error.set(Some(e.message))
      }

    def remove(v: VersionId): Unit =
      if dom.window.confirm(s"Delete version ${v.render} from the registry?") then
        Api.deleteVersion(v).foreach {
          case Right(_) =>
            error.set(None)
            load()
          case Left(e) => error.set(Some(e.message))
        }

    val newVersion = Var("")
    def add(): Unit =
      VersionId.parse(newVersion.now().trim) match
        case Some(v) =>
          newVersion.set("")
          put(
            v,
            VersionInput(lts = false, released = false, releaseDate = None, releaseNotesUrl = None)
          )
        case None => error.set(Some(s"invalid version '${newVersion.now().trim}' (want e.g. 3.9)"))

    def readRow(v: Version): HtmlElement =
      div(
        cls := "grid grid-cols-[6rem_4rem_8rem_1fr] gap-3 items-center bg-white border border-slate-200 rounded-lg px-4 py-2 text-sm",
        div(
          cls := "flex items-center gap-2",
          span(cls := "font-mono text-slate-800", v.version.render),
          if v.lts then Components.badge("LTS", "bg-emerald-100 text-emerald-700") else emptyNode
        ),
        span(
          cls := (if v.released then "text-slate-700" else "text-amber-600"),
          if v.released then "released" else "planned"
        ),
        span(cls := "text-slate-500", v.releaseDate.getOrElse("—")),
        v.releaseNotesUrl
          .map(u =>
            a(
              href   := u,
              target := "_blank",
              cls    := "text-blue-600 hover:underline truncate",
              "release notes"
            )
          )
          .getOrElse(span(cls := "text-slate-300", "—"))
      )

    def editRow(v: Version): HtmlElement =
      val d = Draft(v)
      div(
        cls := "grid grid-cols-1 sm:grid-cols-[5rem_4rem_5.5rem_9rem_1fr_auto_auto] gap-3 items-center bg-white border border-slate-200 rounded-lg px-4 py-2 text-sm",
        span(cls := "font-mono text-slate-800", v.version.render),
        label(
          cls := "flex items-center gap-1.5 text-slate-600",
          Components.checkboxInput(d.lts),
          "LTS"
        ),
        label(
          cls := "flex items-center gap-1.5 text-slate-600",
          Components.checkboxInput(d.released),
          "released"
        ),
        Components.textInput(d.releaseDate, "YYYY-MM-DD"),
        Components.textInput(d.notesUrl, "Release notes URL"),
        button(
          cls := "text-blue-600 text-sm font-medium hover:underline",
          "Save",
          onClick --> { _ => put(v.version, d.input) }
        ),
        button(
          cls := "text-rose-600 text-sm hover:underline",
          "Delete",
          onClick --> { _ => remove(v.version) }
        )
      )

    Components.container(
      Components.pageTitle("Scala versions"),
      Components.subtitle(
        "The minor releases entries anchor to — with LTS, release state and date."
      ),
      div(
        cls := "mt-4 space-y-4",
        child <-- error.signal.map {
          case Some(m) => Components.errorBox(m)
          case None    => emptyNode
        },
        Components.loaded(state.signal) { versions =>
          val ordered = versions.sortBy(_.version).reverse
          div(
            child <-- Session.signedIn.map {
              case false => div(cls := "space-y-2", ordered.map(readRow))
              case true =>
                div(
                  cls := "space-y-2",
                  div(
                    cls := "flex items-center gap-2 mb-3",
                    div(cls := "w-36", Components.textInput(newVersion, "e.g. 3.9")),
                    button(
                      cls := Components.btnSecondary,
                      "+ Add version",
                      onClick --> { _ => add() }
                    )
                  ),
                  ordered.map(editRow)
                )
            }
          )
        }
      )
    )
