package org.scalalang.traits.shared

import sttp.tapir.{Codec, DecodeResult, Schema}

/** Hand-written tapir glue for the domain types. Case-class schemas derive automatically
  * (`generic.auto`), but field-less enums need to be told to encode as strings, the parameterized
  * `SipState` needs an explicit coproduct derivation, and `VersionId` is a string on the wire and
  * in paths. Import `Schemas.given` wherever a `jsonBody[...]` over these types is built.
  */
object Schemas:
  given Schema[Recommendation]    = Schema.derivedEnumeration[Recommendation].defaultStringBased
  given Schema[SipStage]          = Schema.derivedEnumeration[SipStage].defaultStringBased
  given Schema[AvailabilityStage] = Schema.derivedEnumeration[AvailabilityStage].defaultStringBased
  given Schema[LinkKind]          = Schema.derivedEnumeration[LinkKind].defaultStringBased
  given Schema[SipState]          = Schema.derived[SipState]
  given Schema[VersionId]         = Schema.string

  given Codec.PlainCodec[VersionId] = Codec.string.mapDecode(s =>
    VersionId.parse(s) match
      case Some(v) => DecodeResult.Value(v)
      case None    => DecodeResult.Error(s, new IllegalArgumentException(s"invalid version '$s'"))
  )(_.render)
