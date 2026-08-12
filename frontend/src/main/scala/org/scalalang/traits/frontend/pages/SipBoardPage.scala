package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.Api
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*

/** The SIP board: same layout as the pipeline — count strip plus one stacked section per SIP stage,
  * ending with a Closed section for rejected and withdrawn proposals. Only entries with a SIP
  * appear here.
  */
object SipBoardPage:

  // `None` is the Closed section (SipState.stage is None for Rejected | Withdrawn).
  private val stages: List[Option[SipStage]] =
    SipStage.values.toList.map(Some(_)) :+ None

  private def stageLabel(c: Option[SipStage]): String = c match
    case Some(SipStage.PreSip)         => "Pre-SIP"
    case Some(SipStage.Design)         => "Design"
    case Some(SipStage.Implementation) => "Implementation"
    case Some(SipStage.Completed)      => "Completed"
    case None                          => "Closed"

  private def stageClasses(c: Option[SipStage]): String = c match
    case Some(SipStage.PreSip)         => "bg-slate-100 text-slate-600"
    case Some(SipStage.Design)         => "bg-amber-100 text-amber-700"
    case Some(SipStage.Implementation) => "bg-violet-100 text-violet-700"
    case Some(SipStage.Completed)      => "bg-emerald-100 text-emerald-700"
    case None                          => "bg-rose-100 text-rose-700"

  def apply(): HtmlElement =
    val state = Var[Loaded[List[EntrySummary]]](Loaded.Loading)

    Api.listEntries().foreach {
      case Right(es) => state.set(Loaded.Ok(es))
      case Left(e)   => state.set(Loaded.Failed(e.message))
    }

    Components.containerWide(
      div(
        cls := "mb-5",
        Components.pageTitle("Scala improvement proposals"),
        Components.subtitle("Every tracked change with a SIP, by its position in the process.")
      ),
      Components.loaded(state.signal) { entries =>
        val withSip = entries.filterNot(_.archived).flatMap(e => e.sip.map(e -> _))
        Components.board(
          stages.map { c =>
            val items = withSip
              .filter((_, sip) => SipState.stage(sip.state) == c)
              .sortBy(_._1.title)
            Components.BoardSection(
              anchorId = s"sip-${stageLabel(c)}",
              label = stageLabel(c),
              colorCls = stageClasses(c),
              cards = items.map(card)
            )
          }
        )
      }
    )

  private def card(item: (EntrySummary, Sip)): HtmlElement =
    val (e, sip) = item
    Components.boardCard(
      slug = e.slug,
      cardTitle = e.title,
      tooltip = e.tagline,
      corner = Some(sip.number.getOrElse("SIP")),
      statusLine = Components.boardCardStatus(SipState.label(sip.state))
    )
