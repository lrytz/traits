package org.scalalang.traits.shared

import sttp.tapir.Schema

/** Hand-written `Schema`s for the domain enums. Tapir derives case-class schemas automatically
  * (`generic.auto`), but field-less enums need to be told to encode as strings, and the
  * parameterized `SipState` needs an explicit coproduct derivation. Import `Schemas.given` wherever
  * a `jsonBody[...]` over these types is built.
  */
object Schemas:
  given Schema[Lane]             = Schema.derivedEnumeration[Lane].defaultStringBased
  given Schema[Recommendation]   = Schema.derivedEnumeration[Recommendation].defaultStringBased
  given Schema[SipStage]         = Schema.derivedEnumeration[SipStage].defaultStringBased
  given Schema[AvailabilityKind] = Schema.derivedEnumeration[AvailabilityKind].defaultStringBased
  given Schema[LinkKind]         = Schema.derivedEnumeration[LinkKind].defaultStringBased
  given Schema[SipState]         = Schema.derived[SipState]
