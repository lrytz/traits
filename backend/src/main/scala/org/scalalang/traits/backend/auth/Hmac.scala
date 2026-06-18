package org.scalalang.traits.backend.auth

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Hmac:
  private val Algo = "HmacSHA256"

  def sign(secret: Array[Byte], message: Array[Byte]): Array[Byte] =
    val mac = Mac.getInstance(Algo)
    mac.init(SecretKeySpec(secret, Algo))
    mac.doFinal(message)

  def verify(secret: Array[Byte], message: Array[Byte], signature: Array[Byte]): Boolean =
    MessageDigest.isEqual(sign(secret, message), signature)

  /** Constant-time string comparison, for the shared editor password check. */
  def constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(
      a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
      b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    )
