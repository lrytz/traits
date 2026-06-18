package org.scalalang.traits.backend.ai

import org.scalalang.traits.shared.*

/** Turns a topic plus its watched links into a prompt, asks the [[LlmClient]], and packages the
  * reply as a *suggestion* the editor reviews. Never writes to the store — applying a suggestion is
  * a separate, explicit edit by a human.
  */
class EnrichService(llm: LlmClient):

  def enrich(topic: Topic, req: EnrichRequest): EnrichResult =
    val watched = topic.links.filter(_.watch).map(_.url) ++ req.extraUrls
    val reply   = llm.complete(buildPrompt(topic, watched, req.instructions))
    EnrichResult(
      notes = reply,
      suggestedSipState = None,
      suggestedAvailability = None,
      suggestedSections = List(Section("Discussion summary", reply)),
      suggestedTimeline = Nil,
      discoveredLinks = Nil
    )

  private def buildPrompt(
      topic: Topic,
      watched: List[String],
      instructions: Option[String]
  ): String =
    val urls = if watched.isEmpty then "(none)" else watched.mkString("\n- ", "\n- ", "")
    s"""You are tracking the Scala language feature "${topic.title}".
       |Current headline: ${topic.headline}.
       |Re-read these sources and summarise what changed, propose an updated SIP state if the
       |committee labels moved, and draft timeline entries for any new milestones.
       |Sources:$urls
       |${instructions.fold("")(i => s"Extra instructions: $i")}""".stripMargin
