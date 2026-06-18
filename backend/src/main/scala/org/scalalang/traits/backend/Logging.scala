package org.scalalang.traits.backend

import scribe.Level
import scribe.format.*

object Logging:

  def init(): Unit =
    val _ = scribe.Logger.root
      .clearHandlers()
      .clearModifiers()
      .withHandler(formatter = Formatter.enhanced, minimumLevel = Some(Level.Info))
      .replace()
