package org.scalalang.traits.backend.ai

/** Pluggable LLM boundary. Direct-style and synchronous — a real implementation (Anthropic /
  * OpenAI / local) just makes a blocking HTTP call inside `complete`. Kept deliberately tiny so the
  * enrichment service doesn't depend on any particular provider.
  */
trait LlmClient:
  def complete(prompt: String): String

/** No-op implementation used until a provider is wired in. Returns a clearly-marked placeholder so
  * the end-to-end enrichment flow (button → review panel) works without an API key.
  */
class StubLlmClient extends LlmClient:
  def complete(prompt: String): String =
    val _ = prompt
    "_(AI stub — no LLM configured.)_ Wire an `LlmClient` implementation into `Main` to have this " +
      "re-read the watched discussions and draft a summary, a suggested SIP state, and timeline " +
      "entries for you to review."
