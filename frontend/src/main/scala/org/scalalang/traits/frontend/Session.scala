package org.scalalang.traits.frontend

import org.scalalang.traits.shared.Editor
import org.scalalang.traits.frontend.Api.given
import com.raquo.laminar.api.L.*

/** Client-side view of the editor session. The cookie itself is HttpOnly; this just tracks whether
  * `GET /api/me` currently succeeds so the UI can show editor affordances.
  */
object Session:

  val current: Var[Option[Editor]] = Var(None)

  val signedIn: Signal[Boolean] = current.signal.map(_.isDefined)

  /** Probe the existing cookie once at startup. */
  def init(): Unit =
    Api.me().foreach {
      case Right(editor) => current.set(Some(editor))
      case Left(_)       => current.set(None)
    }

  def set(editor: Editor): Unit = current.set(Some(editor))

  def clear(): Unit = current.set(None)
