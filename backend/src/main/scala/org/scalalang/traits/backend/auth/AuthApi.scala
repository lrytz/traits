package org.scalalang.traits.backend.auth

import org.scalalang.traits.shared.{ApiError, Editor, Endpoints}

import java.time.Instant
import sttp.model.headers.Cookie.SameSite
import sttp.model.headers.CookieValueWithMeta
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** Shared-password auth. `login` checks the configured editor password and mints an HMAC-signed
  * session cookie; `requireEditor` is the gate every write/enrich handler runs first.
  */
class AuthApi(
    codec: SessionCodec,
    editorPassword: String,
    sessionTtlSeconds: Long,
    cookieSecure: Boolean
):

  private def buildCookie(value: String, maxAgeSec: Long): CookieValueWithMeta =
    CookieValueWithMeta.unsafeApply(
      value = value,
      maxAge = Some(maxAgeSec),
      path = Some("/"),
      httpOnly = true,
      secure = cookieSecure,
      sameSite = Some(SameSite.Lax)
    )

  def requireEditor(cookieValue: Option[String]): Either[ApiError, Session] =
    cookieValue match
      case None    => Left(ApiError(ApiError.Unauthorized, "Not signed in"))
      case Some(c) =>
        codec.decode(c) match
          case Left(_)        => Left(ApiError(ApiError.Unauthorized, "Invalid session"))
          case Right(session) => Right(session)

  private val loginWithCookie  = Endpoints.login.out(setCookie(Endpoints.SessionCookieName))
  private val logoutWithCookie = Endpoints.logout.out(setCookie(Endpoints.SessionCookieName))
  private val meWithCookie     = Endpoints.me.in(cookie[Option[String]](Endpoints.SessionCookieName))

  val login: ServerEndpoint[Any, Identity] =
    loginWithCookie.handle { req =>
      if Hmac.constantTimeEquals(req.password, editorPassword) then
        val session = Session("editor", Instant.now.plusSeconds(sessionTtlSeconds))
        Right((Editor("editor"), buildCookie(codec.encode(session), sessionTtlSeconds)))
      else Left(ApiError(ApiError.Unauthorized, "Wrong password"))
    }

  val logout: ServerEndpoint[Any, Identity] =
    logoutWithCookie.handle(_ => Right(buildCookie(value = "", maxAgeSec = 0)))

  val me: ServerEndpoint[Any, Identity] =
    meWithCookie.handle(cookie => requireEditor(cookie).map(s => Editor(s.editor)))

  val all: List[ServerEndpoint[Any, Identity]] = List(login, logout, me)
