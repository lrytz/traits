package org.scalalang.traits.frontend

import org.scalalang.traits.shared.*
import sttp.client3.FetchBackend
import sttp.tapir.client.sttp.SttpClientInterpreter

import scala.concurrent.{ExecutionContext, Future}

/** Typed tapir-sttp-client wrapper. Every call is checked against the shared endpoint defs at
  * compile time. The session cookie is HttpOnly — we pass `None` to cookie inputs and let the
  * browser round-trip the Cookie header (same-origin in prod, the Vite proxy in dev).
  */
object Api:

  private val backend = FetchBackend()
  private val interp  = SttpClientInterpreter()

  given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  def listEntries(): Future[Either[ApiError, List[EntrySummary]]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.listEntries, baseUri = None)
      .apply(())
      .send(backend)
      .map(_.body)

  def getEntry(slug: String): Future[Either[ApiError, Entry]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.getEntry, baseUri = None)
      .apply(slug)
      .send(backend)
      .map(_.body)

  def search(q: String): Future[Either[ApiError, List[EntrySummary]]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.search, baseUri = None)
      .apply(q)
      .send(backend)
      .map(_.body)

  def listVersions(): Future[Either[ApiError, List[Version]]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.listVersions, baseUri = None)
      .apply(())
      .send(backend)
      .map(_.body)

  def login(password: String): Future[Either[ApiError, Editor]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.login, baseUri = None)
      .apply(LoginRequest(password))
      .send(backend)
      .map(_.body)

  def logout(): Future[Either[ApiError, Unit]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.logout, baseUri = None)
      .apply(())
      .send(backend)
      .map(_.body)

  def me(): Future[Either[ApiError, Editor]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.me, baseUri = None)
      .apply(())
      .send(backend)
      .map(_.body)

  def putEntry(slug: String, input: EntryInput): Future[Either[ApiError, Entry]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.putEntry, baseUri = None)
      .apply((slug, input))
      .send(backend)
      .map(_.body)

  def deleteEntry(slug: String): Future[Either[ApiError, Unit]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.deleteEntry, baseUri = None)
      .apply(slug)
      .send(backend)
      .map(_.body)

  def putVersion(v: VersionId, input: VersionInput): Future[Either[ApiError, Version]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.putVersion, baseUri = None)
      .apply((v, input))
      .send(backend)
      .map(_.body)

  def deleteVersion(v: VersionId): Future[Either[ApiError, Unit]] =
    interp
      .toRequestThrowDecodeFailures(Endpoints.deleteVersion, baseUri = None)
      .apply(v)
      .send(backend)
      .map(_.body)
