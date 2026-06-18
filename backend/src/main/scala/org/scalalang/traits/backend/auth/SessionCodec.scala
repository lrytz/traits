package org.scalalang.traits.backend.auth

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.Base64

/** The editor session carried in the `traits_session` cookie. With shared-password auth the
  * `editor` name is constant, but the field leaves room for real per-user identity later.
  */
case class Session(editor: String, expiresAt: Instant)

/** Encodes a [[Session]] to `base64url(payload).base64url(hmac)`. Payload is `editor\nexpiryMillis`
  * — small and upickle-free, so we don't need a custom `Instant` codec.
  */
class SessionCodec(secret: Array[Byte]):
  private val enc = Base64.getUrlEncoder.withoutPadding
  private val dec = Base64.getUrlDecoder

  def encode(s: Session): String =
    val payload = s"${s.editor}\n${s.expiresAt.toEpochMilli}".getBytes(UTF_8)
    val sig     = Hmac.sign(secret, payload)
    s"${enc.encodeToString(payload)}.${enc.encodeToString(sig)}"

  def decode(cookie: String): Either[String, Session] =
    cookie.split('.') match
      case Array(payloadB64, sigB64) =>
        try
          val payload = dec.decode(payloadB64)
          val sig     = dec.decode(sigB64)
          if !Hmac.verify(secret, payload, sig) then Left("bad signature")
          else
            new String(payload, UTF_8).split('\n') match
              case Array(editor, millis) =>
                val session = Session(editor, Instant.ofEpochMilli(millis.toLong))
                if session.expiresAt.isAfter(Instant.now) then Right(session)
                else Left("expired")
              case _ => Left("malformed")
        catch case _: Throwable => Left("malformed")
      case _ => Left("malformed")
