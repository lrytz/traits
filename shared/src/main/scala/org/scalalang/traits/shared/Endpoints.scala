package org.scalalang.traits.shared

import org.scalalang.traits.shared.Schemas.given
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.upickle.*

/** The HTTP contract, shared by the Netty server (which attaches logic + the session cookie) and
  * the Scala.js client (which derives a typed fetch client from these same values).
  *
  * Read endpoints are public. Write / enrich endpoints are body-only here; the editor session
  * cookie is attached server-side in `AuthApi`, so the browser client never has to name an
  * unreadable `Set-Cookie`.
  */
object Endpoints:

  val SessionCookieName = "traits_session"

  case class Health(status: String, topicCount: Long) derives upickle.default.ReadWriter, Schema

  private val base = endpoint.in("api")

  val health: PublicEndpoint[Unit, ApiError, Health, Any] =
    base.get.in("health").out(jsonBody[Health]).errorOut(ApiError.jsonBody)

  // ---- public reads ----

  val listTopics: PublicEndpoint[Unit, ApiError, List[FeatureSummary], Any] =
    base.get
      .in("topics")
      .out(jsonBody[List[FeatureSummary]])
      .errorOut(ApiError.jsonBody)
      .summary("All topics, summarised, for list and pipeline views")

  val getTopic: PublicEndpoint[String, ApiError, Topic, Any] =
    base.get
      .in("topics" / path[String]("slug"))
      .out(jsonBody[Topic])
      .errorOut(ApiError.jsonBody)
      .summary("One topic in full")

  val search: PublicEndpoint[String, ApiError, List[FeatureSummary], Any] =
    base.get
      .in("search")
      .in(query[String]("q"))
      .out(jsonBody[List[FeatureSummary]])
      .errorOut(ApiError.jsonBody)
      .summary("Full-text search over topics")

  val changelog: PublicEndpoint[Option[Int], ApiError, List[ChangelogEntry], Any] =
    base.get
      .in("changelog")
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[ChangelogEntry]])
      .errorOut(ApiError.jsonBody)
      .summary("Reverse-chronological timeline flattened across all topics")

  // ---- auth ----

  val login: PublicEndpoint[LoginRequest, ApiError, Editor, Any] =
    base.post
      .in("auth" / "login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[Editor])
      .errorOut(ApiError.unauthorized)
      .summary("Exchange the shared password for an editor session cookie")

  val logout: PublicEndpoint[Unit, ApiError, Unit, Any] =
    base.post.in("auth" / "logout").errorOut(ApiError.jsonBody)

  val me: PublicEndpoint[Unit, ApiError, Editor, Any] =
    base.get
      .in("me")
      .out(jsonBody[Editor])
      .errorOut(ApiError.unauthorized)
      .summary("The current editor, or 401 if not signed in")

  // ---- editor writes (cookie attached server-side) ----

  val putTopic: PublicEndpoint[(String, TopicInput), ApiError, Topic, Any] =
    base.put
      .in("topics" / path[String]("slug"))
      .in(jsonBody[TopicInput])
      .out(jsonBody[Topic])
      .errorOut(ApiError.jsonBody)
      .summary("Create or replace a topic")

  val deleteTopic: PublicEndpoint[String, ApiError, Unit, Any] =
    base.delete
      .in("topics" / path[String]("slug"))
      .errorOut(ApiError.jsonBody)
      .summary("Delete a topic")

  val enrich: PublicEndpoint[(String, EnrichRequest), ApiError, EnrichResult, Any] =
    base.post
      .in("topics" / path[String]("slug") / "enrich")
      .in(jsonBody[EnrichRequest])
      .out(jsonBody[EnrichResult])
      .errorOut(ApiError.jsonBody)
      .summary("Re-read watched links with an LLM and propose updates (suggest-only)")
