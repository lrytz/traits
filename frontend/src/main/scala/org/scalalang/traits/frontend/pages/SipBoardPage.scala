package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Routes}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*

/** The SIP board: one column per SIP stage plus a Closed column for rejected and withdrawn
  * proposals. Only entries with a SIP appear here.
  */
object SipBoardPage:

  // `None` is the Closed column (SipState.stage is None for Rejected | Withdrawn).
  private val columns: List[Option[SipStage]] =
    SipStage.values.toList.map(Some(_)) :+ None

  private def columnLabel(c: Option[SipStage]): String = c match
    case Some(SipStage.PreSip)         => "Pre-SIP"
    case Some(SipStage.Design)         => "Design"
    case Some(SipStage.Implementation) => "Implementation"
    case Some(SipStage.Completed)      => "Completed"
    case None                          => "Closed"

  private def columnClasses(c: Option[SipStage]): String = c match
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
        div(
          cls := "flex flex-col sm:flex-row gap-4 sm:overflow-x-auto pb-4",
          columns.map(c => column(c, withSip.filter((_, sip) => SipState.stage(sip.state) == c)))
        )
      }
    )

  private def column(c: Option[SipStage], items: List[(EntrySummary, Sip)]): HtmlElement =
    div(
      cls := "w-full sm:flex-1 sm:min-w-44",
      div(
        cls := "flex items-center gap-2 mb-2",
        Components.badge(columnLabel(c), columnClasses(c)),
        span(cls := "text-xs text-slate-400", items.size.toString)
      ),
      div(
        cls := "space-y-2",
        if items.isEmpty then p(cls := "text-xs text-slate-300 pb-1", "—")
        else items.map(card)
      )
    )

  private def card(item: (EntrySummary, Sip)): HtmlElement =
    val (e, sip) = item
    a(
      href := Routes.urlFor(Page.EntryView(e.slug)),
      onClick.preventDefault --> { _ => Routes.router.pushState(Page.EntryView(e.slug)) },
      cls := "block bg-white rounded-lg border border-slate-200 p-3 hover:border-blue-300 hover:shadow-sm transition",
      div(
        cls := "flex items-center gap-2",
        div(cls := "font-medium text-slate-900 text-sm", e.title),
        sip.number
          .map(n => span(cls := "text-xs font-mono text-slate-400 ml-auto", n))
          .getOrElse(emptyNode)
      ),
      div(cls := "text-slate-500 text-xs mt-1 line-clamp-2", e.tagline),
      div(cls := "text-xs text-slate-400 mt-2", SipState.label(sip.state))
    )
