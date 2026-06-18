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

  def listTopics(): Future[Either[ApiError, List[FeatureSummary]]] =
    interp.toRequestThrowDecodeFailures(Endpoints.listTopics, baseUri = None).apply(()).send(backend).map(_.body)

  def getTopic(slug: String): Future[Either[ApiError, Topic]] =
    interp.toRequestThrowDecodeFailures(Endpoints.getTopic, baseUri = None).apply(slug).send(backend).map(_.body)

  def search(q: String): Future[Either[ApiError, List[FeatureSummary]]] =
    interp.toRequestThrowDecodeFailures(Endpoints.search, baseUri = None).apply(q).send(backend).map(_.body)

  def changelog(limit: Option[Int]): Future[Either[ApiError, List[ChangelogEntry]]] =
    interp.toRequestThrowDecodeFailures(Endpoints.changelog, baseUri = None).apply(limit).send(backend).map(_.body)

  def login(password: String): Future[Either[ApiError, Editor]] =
    interp.toRequestThrowDecodeFailures(Endpoints.login, baseUri = None).apply(LoginRequest(password)).send(backend).map(_.body)

  def logout(): Future[Either[ApiError, Unit]] =
    interp.toRequestThrowDecodeFailures(Endpoints.logout, baseUri = None).apply(()).send(backend).map(_.body)

  def me(): Future[Either[ApiError, Editor]] =
    interp.toRequestThrowDecodeFailures(Endpoints.me, baseUri = None).apply(()).send(backend).map(_.body)

  def putTopic(slug: String, input: TopicInput): Future[Either[ApiError, Topic]] =
    interp.toRequestThrowDecodeFailures(Endpoints.putTopic, baseUri = None).apply((slug, input)).send(backend).map(_.body)

  def deleteTopic(slug: String): Future[Either[ApiError, Unit]] =
    interp.toRequestThrowDecodeFailures(Endpoints.deleteTopic, baseUri = None).apply(slug).send(backend).map(_.body)
