package org.scalalang.traits.backend.topic

import org.scalalang.traits.backend.auth.AuthApi
import org.scalalang.traits.shared.{ApiError, Endpoints}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** Public reads plus editor-gated writes. Read endpoints are open; write endpoints take the
  * session cookie (attached here, server-side) and run `auth.requireEditor` first.
  */
class TopicApi(auth: AuthApi, topics: TopicService):

  val list: ServerEndpoint[Any, Identity] =
    Endpoints.listTopics.handleSuccess(_ => topics.list())

  val get: ServerEndpoint[Any, Identity] =
    Endpoints.getTopic.handle { slug =>
      topics.get(slug).toRight(ApiError(ApiError.NotFound, s"Topic '$slug' not found"))
    }

  val search: ServerEndpoint[Any, Identity] =
    Endpoints.search.handleSuccess(topics.search)

  val changelog: ServerEndpoint[Any, Identity] =
    Endpoints.changelog.handleSuccess(topics.changelog)

  private val putWithCookie =
    Endpoints.putTopic.in(cookie[Option[String]](Endpoints.SessionCookieName))
  private val deleteWithCookie =
    Endpoints.deleteTopic.in(cookie[Option[String]](Endpoints.SessionCookieName))

  val put: ServerEndpoint[Any, Identity] =
    putWithCookie.handle { case (slug, input, cookie) =>
      auth.requireEditor(cookie).map(_ => topics.put(slug, input))
    }

  val delete: ServerEndpoint[Any, Identity] =
    deleteWithCookie.handle { (slug, cookie) =>
      auth.requireEditor(cookie).flatMap { _ =>
        if topics.delete(slug) then Right(())
        else Left(ApiError(ApiError.NotFound, s"Topic '$slug' not found"))
      }
    }

  val all: List[ServerEndpoint[Any, Identity]] =
    List(list, get, search, changelog, put, delete)
