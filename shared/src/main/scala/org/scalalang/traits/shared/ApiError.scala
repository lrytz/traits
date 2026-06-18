package org.scalalang.traits.shared

import sttp.model.StatusCode
import sttp.tapir.*
import upickle.default.*

/** The single error shape every endpoint returns. `code` is a stable machine-readable token;
  * `message` is human-readable text for display.
  */
case class ApiError(code: String, message: String) derives ReadWriter, Schema

object ApiError:

  val jsonBody: EndpointOutput[ApiError] = sttp.tapir.json.upickle.jsonBody[ApiError]

  /** errorOut for endpoints where any failure means "must authenticate" — fixed 401. */
  val unauthorized: EndpointOutput[ApiError] =
    statusCode(StatusCode.Unauthorized).and(jsonBody)

  val Unexpected   = "unexpected"
  val Unauthorized = "unauthorized"
  val NotFound     = "not_found"
  val Validation   = "validation"
  val Conflict     = "conflict"
  val NetworkError = "network_error" // frontend-side: the call itself failed
