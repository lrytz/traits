package org.scalalang.traits.backend.version

import org.scalalang.traits.backend.auth.AuthApi
import org.scalalang.traits.shared.{ApiError, Endpoints}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** The version registry: public list, editor-gated writes. */
class VersionApi(auth: AuthApi, versions: VersionService):

  val list: ServerEndpoint[Any, Identity] =
    Endpoints.listVersions.handleSuccess(_ => versions.list())

  private val putWithCookie =
    Endpoints.putVersion.in(cookie[Option[String]](Endpoints.SessionCookieName))
  private val deleteWithCookie =
    Endpoints.deleteVersion.in(cookie[Option[String]](Endpoints.SessionCookieName))

  val put: ServerEndpoint[Any, Identity] =
    putWithCookie.handle { case (v, input, cookie) =>
      auth.requireEditor(cookie).map(_ => versions.put(v, input))
    }

  val delete: ServerEndpoint[Any, Identity] =
    deleteWithCookie.handle { (v, cookie) =>
      auth.requireEditor(cookie).flatMap { _ =>
        if versions.delete(v) then Right(())
        else Left(ApiError(ApiError.NotFound, s"Version '${v.render}' not found"))
      }
    }

  val all: List[ServerEndpoint[Any, Identity]] =
    List(list, put, delete)
