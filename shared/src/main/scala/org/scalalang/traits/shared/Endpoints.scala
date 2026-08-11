package org.scalalang.traits.shared

import org.scalalang.traits.shared.Schemas.given
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.upickle.*

/** The HTTP contract, shared by the Netty server (which attaches logic + the session cookie) and
  * the Scala.js client (which derives a typed fetch client from these same values).
  *
  * Read endpoints are public. Write endpoints are body-only here; the editor session cookie is
  * attached server-side in `AuthApi`, so the browser client never has to name an unreadable
  * `Set-Cookie`.
  */
object Endpoints:

  val SessionCookieName = "traits_session"

  case class Health(status: String, entryCount: Long) derives upickle.default.ReadWriter, Schema

  private val base = endpoint.in("api")

  val health: PublicEndpoint[Unit, ApiError, Health, Any] =
    base.get.in("health").out(jsonBody[Health]).errorOut(ApiError.jsonBody)

  // ---- public reads ----

  val listEntries: PublicEndpoint[Unit, ApiError, List[EntrySummary], Any] =
    base.get
      .in("entries")
      .out(jsonBody[List[EntrySummary]])
      .errorOut(ApiError.jsonBody)
      .summary("All entries, summarised, for list and board views")

  val getEntry: PublicEndpoint[String, ApiError, Entry, Any] =
    base.get
      .in("entries" / path[String]("slug"))
      .out(jsonBody[Entry])
      .errorOut(ApiError.jsonBody)
      .summary("One entry in full")

  val search: PublicEndpoint[String, ApiError, List[EntrySummary], Any] =
    base.get
      .in("search")
      .in(query[String]("q"))
      .out(jsonBody[List[EntrySummary]])
      .errorOut(ApiError.jsonBody)
      .summary("Full-text search over entries")

  val listVersions: PublicEndpoint[Unit, ApiError, List[Version], Any] =
    base.get
      .in("versions")
      .out(jsonBody[List[Version]])
      .errorOut(ApiError.jsonBody)
      .summary("The version registry, ascending")

  val versionEntries: PublicEndpoint[VersionId, ApiError, List[EntryStatus], Any] =
    base.get
      .in("versions" / path[VersionId]("version") / "entries")
      .out(jsonBody[List[EntryStatus]])
      .errorOut(ApiError.jsonBody)
      .summary("Every entry with a status in the given version, and that status")

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

  val putEntry: PublicEndpoint[(String, EntryInput), ApiError, Entry, Any] =
    base.put
      .in("entries" / path[String]("slug"))
      .in(jsonBody[EntryInput])
      .out(jsonBody[Entry])
      .errorOut(ApiError.jsonBody)
      .summary("Create or replace an entry")

  val deleteEntry: PublicEndpoint[String, ApiError, Unit, Any] =
    base.delete
      .in("entries" / path[String]("slug"))
      .errorOut(ApiError.jsonBody)
      .summary("Delete an entry")

  val putVersion: PublicEndpoint[(VersionId, VersionInput), ApiError, Version, Any] =
    base.put
      .in("versions" / path[VersionId]("version"))
      .in(jsonBody[VersionInput])
      .out(jsonBody[Version])
      .errorOut(ApiError.jsonBody)
      .summary("Create or replace a version registry row")

  val deleteVersion: PublicEndpoint[VersionId, ApiError, Unit, Any] =
    base.delete
      .in("versions" / path[VersionId]("version"))
      .errorOut(ApiError.jsonBody)
      .summary("Delete a version registry row")
