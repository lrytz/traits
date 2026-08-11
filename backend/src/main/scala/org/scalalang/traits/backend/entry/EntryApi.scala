package org.scalalang.traits.backend.entry

import org.scalalang.traits.backend.auth.AuthApi
import org.scalalang.traits.shared.{ApiError, Endpoints}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** Public reads plus editor-gated writes. Read endpoints are open; write endpoints take the session
  * cookie (attached here, server-side) and run `auth.requireEditor` first.
  */
class EntryApi(auth: AuthApi, entries: EntryService):

  val list: ServerEndpoint[Any, Identity] =
    Endpoints.listEntries.handleSuccess(_ => entries.list())

  val get: ServerEndpoint[Any, Identity] =
    Endpoints.getEntry.handle { slug =>
      entries.get(slug).toRight(ApiError(ApiError.NotFound, s"Entry '$slug' not found"))
    }

  val search: ServerEndpoint[Any, Identity] =
    Endpoints.search.handleSuccess(entries.search)

  val versionEntries: ServerEndpoint[Any, Identity] =
    Endpoints.versionEntries.handleSuccess(entries.statusIn)

  private val putWithCookie =
    Endpoints.putEntry.in(cookie[Option[String]](Endpoints.SessionCookieName))
  private val deleteWithCookie =
    Endpoints.deleteEntry.in(cookie[Option[String]](Endpoints.SessionCookieName))

  val put: ServerEndpoint[Any, Identity] =
    putWithCookie.handle { case (slug, input, cookie) =>
      auth.requireEditor(cookie).flatMap { _ =>
        input.validate match
          case Nil    => Right(entries.put(slug, input))
          case errors => Left(ApiError(ApiError.Validation, errors.mkString("; ")))
      }
    }

  val delete: ServerEndpoint[Any, Identity] =
    deleteWithCookie.handle { (slug, cookie) =>
      auth.requireEditor(cookie).flatMap { _ =>
        if entries.delete(slug) then Right(())
        else Left(ApiError(ApiError.NotFound, s"Entry '$slug' not found"))
      }
    }

  val all: List[ServerEndpoint[Any, Identity]] =
    List(list, get, search, versionEntries, put, delete)
