package org.scalalang.traits.frontend

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** Markdown → sanitised HTML. We store raw markdown everywhere; rendering goes through `marked`
  * and then `DOMPurify` so user/AI-authored content can never inject script into this public page.
  */
@js.native
@JSImport("marked", "marked")
private object Marked extends js.Object:
  def parse(markdown: String): String = js.native

@js.native
@JSImport("dompurify", JSImport.Default)
private object DOMPurify extends js.Object:
  def sanitize(html: String): String = js.native

object Markdown:
  def render(markdown: String): String =
    DOMPurify.sanitize(Marked.parse(markdown))
