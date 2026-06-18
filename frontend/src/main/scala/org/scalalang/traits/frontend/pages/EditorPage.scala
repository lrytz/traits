package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Routes, Session}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Create (`slugOpt = None`) or edit an existing topic. Writes go through the editor-gated
  * `putTopic` / `deleteTopic`; the form mirrors the [[TopicInput]] shape field-for-field.
  */
object EditorPage:

  private val allSipStates: List[SipState] = List(
    SipState.PreSipSubmitted,
    SipState.DesignUnderReview,
    SipState.DesignVoteRequested(Recommendation.Accept),
    SipState.DesignVoteRequested(Recommendation.Reject),
    SipState.ImplementationWaiting,
    SipState.ImplementationUnderReview,
    SipState.ImplementationVoteRequested(Recommendation.Accept),
    SipState.ImplementationVoteRequested(Recommendation.Reject),
    SipState.CompletedAccepted,
    SipState.CompletedShipped,
    SipState.Rejected,
    SipState.Withdrawn
  )

  private def linkKindLabel(k: LinkKind): String = k match
    case LinkKind.Sip         => "SIP"
    case LinkKind.Pr          => "PR"
    case LinkKind.Issue       => "Issue"
    case LinkKind.ForumThread => "Forum"
    case LinkKind.Doc         => "Doc"
    case LinkKind.Other       => "Other"

  private final class SectionDraft(heading0: String, body0: String):
    val heading            = Var(heading0)
    val body               = Var(body0)
    def toModel: Section   = Section(heading.now().trim, body.now())

  private final class LinkDraft(kind0: LinkKind, title0: String, url0: String, watch0: Boolean):
    val kind          = Var(kind0)
    val title         = Var(title0)
    val url           = Var(url0)
    val watch         = Var(watch0)
    def toModel: Link = Link(kind.now(), title.now().trim, url.now().trim, watch.now())

  private final class TimelineDraft(date0: String, summary0: String, source0: String):
    val date     = Var(date0)
    val summary  = Var(summary0)
    val source   = Var(source0)
    def toModel: TimelineEntry =
      TimelineEntry(date.now().trim, summary.now().trim, Some(source.now().trim).filter(_.nonEmpty))

  def apply(slugOpt: Option[String]): HtmlElement =
    val creating = slugOpt.isEmpty

    val slug     = Var(slugOpt.getOrElse(""))
    val title    = Var("")
    val tagline  = Var("")
    val tagsText = Var("")
    val sections = Var(List.empty[SectionDraft])
    val links    = Var(List.empty[LinkDraft])
    val timeline = Var(List.empty[TimelineDraft])

    val availEnabled = Var(false)
    val availKind    = Var[AvailabilityKind](AvailabilityKind.Experimental)
    val availVersion = Var("")
    val availNote    = Var("")

    val sipEnabled = Var(false)
    val sipNumber  = Var("")
    val sipTitle   = Var("")
    val sipUrl     = Var("")
    val sipState   = Var[SipState](SipState.PreSipSubmitted)

    val loadState = Var[Loaded[Unit]](if creating then Loaded.Ok(()) else Loaded.Loading)
    val error     = Var[Option[String]](None)
    val saving    = Var(false)

    slugOpt.foreach { s =>
      Api.getTopic(s).foreach {
        case Right(t) =>
          title.set(t.title)
          tagline.set(t.tagline)
          tagsText.set(t.tags.mkString(", "))
          sections.set(t.sections.map(sec => SectionDraft(sec.heading, sec.body)))
          links.set(t.links.map(l => LinkDraft(l.kind, l.title, l.url, l.watch)))
          timeline.set(t.timeline.map(e => TimelineDraft(e.date, e.summary, e.sourceUrl.getOrElse(""))))
          t.availability.foreach { a =>
            availEnabled.set(true)
            availKind.set(a.kind)
            availVersion.set(a.sinceVersion)
            availNote.set(a.note.getOrElse(""))
          }
          t.sip.foreach { sp =>
            sipEnabled.set(true)
            sipNumber.set(sp.number.getOrElse(""))
            sipTitle.set(sp.title)
            sipUrl.set(sp.url)
            sipState.set(sp.state)
          }
          loadState.set(Loaded.Ok(()))
        case Left(e) => loadState.set(Loaded.Failed(e.message))
      }
    }

    def buildInput(): TopicInput =
      TopicInput(
        title = title.now().trim,
        tagline = tagline.now().trim,
        sections = sections.now().map(_.toModel),
        availability =
          if availEnabled.now() then
            Some(Availability(availKind.now(), availVersion.now().trim, Some(availNote.now()).filter(_.trim.nonEmpty)))
          else None,
        sip =
          if sipEnabled.now() then
            Some(Sip(Some(sipNumber.now().trim).filter(_.nonEmpty), sipTitle.now().trim, sipUrl.now().trim, sipState.now()))
          else None,
        links = links.now().map(_.toModel),
        timeline = timeline.now().map(_.toModel),
        tags = tagsText.now().split(",").map(_.trim).filter(_.nonEmpty).toList
      )

    def save(): Unit =
      val s = slug.now().trim
      if s.isEmpty || title.now().trim.isEmpty then error.set(Some("Slug and title are required."))
      else if !saving.now() then
        saving.set(true)
        error.set(None)
        Api.putTopic(s, buildInput()).foreach {
          case Right(t) =>
            saving.set(false)
            Routes.router.pushState(Page.TopicView(t.slug))
          case Left(e) =>
            saving.set(false)
            error.set(Some(e.message))
        }

    def remove(): Unit =
      if dom.window.confirm(s"Delete '${slug.now()}'? This cannot be undone.") then
        Api.deleteTopic(slug.now()).foreach {
          case Right(_) => Routes.router.pushState(Page.Home)
          case Left(e)  => error.set(Some(e.message))
        }

    // ---- dynamic-list rows ----

    def sectionRow(d: SectionDraft): HtmlElement =
      div(
        cls := "border border-slate-200 rounded-lg p-3 space-y-2 bg-white",
        div(
          cls := "flex gap-2 items-center",
          Components.textInput(d.heading, "Heading (e.g. Overview)"),
          button(
            cls := "text-rose-600 text-sm shrink-0 hover:underline",
            "Remove",
            onClick --> { _ => sections.update(_.filterNot(_ eq d)) }
          )
        ),
        Components.multilineInput(d.body, 5, "Markdown…")
      )

    def linkRow(d: LinkDraft): HtmlElement =
      div(
        cls := "border border-slate-200 rounded-lg p-3 space-y-2 bg-white",
        div(
          cls := "grid grid-cols-1 sm:grid-cols-[7rem_1fr] gap-2",
          Components.selectInput(d.kind, LinkKind.values.toList, linkKindLabel, _.toString),
          Components.textInput(d.title, "Title")
        ),
        Components.textInput(d.url, "https://…"),
        div(
          cls := "flex items-center justify-between",
          label(
            cls := "flex items-center gap-2 text-sm text-slate-600",
            Components.checkboxInput(d.watch),
            "Watch (AI re-reads this link)"
          ),
          button(
            cls := "text-rose-600 text-sm hover:underline",
            "Remove",
            onClick --> { _ => links.update(_.filterNot(_ eq d)) }
          )
        )
      )

    def timelineRow(d: TimelineDraft): HtmlElement =
      div(
        cls := "border border-slate-200 rounded-lg p-3 space-y-2 bg-white",
        div(
          cls := "grid grid-cols-1 sm:grid-cols-[10rem_1fr] gap-2",
          Components.textInput(d.date, "YYYY-MM-DD"),
          Components.textInput(d.summary, "What happened")
        ),
        div(
          cls := "flex items-center gap-2",
          Components.textInput(d.source, "Source URL (optional)"),
          button(
            cls := "text-rose-600 text-sm shrink-0 hover:underline",
            "Remove",
            onClick --> { _ => timeline.update(_.filterNot(_ eq d)) }
          )
        )
      )

    def sectionBlock(heading: String, addLabel: String, rows: HtmlElement, onAdd: () => Unit): HtmlElement =
      div(
        cls := "space-y-3",
        h2(cls := "text-lg font-semibold text-slate-800", heading),
        rows,
        button(cls := Components.btnSecondary, addLabel, onClick --> { _ => onAdd() })
      )

    val availabilityBlock =
      div(
        cls := "border border-slate-200 rounded-lg p-4 space-y-3 bg-white",
        label(
          cls := "flex items-center gap-2 font-medium text-slate-800",
          Components.checkboxInput(availEnabled),
          "Availability"
        ),
        child <-- availEnabled.signal.map {
          case false => emptyNode
          case true =>
            div(
              cls := "space-y-3",
              div(
                cls := "grid grid-cols-1 sm:grid-cols-2 gap-3",
                Components.field("Kind", Components.selectInput(availKind, AvailabilityKind.values.toList, _.toString, _.toString)),
                Components.field("Since version", Components.textInput(availVersion, "e.g. 3.7.0"))
              ),
              Components.field("Note (markdown, optional)", Components.multilineInput(availNote, 3, "How to enable in this state…"))
            )
        }
      )

    val sipBlock =
      div(
        cls := "border border-slate-200 rounded-lg p-4 space-y-3 bg-white",
        label(
          cls := "flex items-center gap-2 font-medium text-slate-800",
          Components.checkboxInput(sipEnabled),
          "SIP"
        ),
        child <-- sipEnabled.signal.map {
          case false => emptyNode
          case true =>
            div(
              cls := "space-y-3",
              div(
                cls := "grid grid-cols-1 sm:grid-cols-[10rem_1fr] gap-3",
                Components.field("Number (optional)", Components.textInput(sipNumber, "e.g. SIP-58")),
                Components.field("Title", Components.textInput(sipTitle, "Proposal title"))
              ),
              Components.field("URL", Components.textInput(sipUrl, "https://…")),
              Components.field("State", Components.selectInput(sipState, allSipStates, SipState.label, _.toString))
            )
        }
      )

    def formBody: HtmlElement =
      div(
        cls := "space-y-6",
        child <-- Session.signedIn.map {
          case true => emptyNode
          case false =>
            div(
              cls := "bg-amber-50 text-amber-800 text-sm rounded-md px-4 py-2",
              "You're not signed in — saving will fail. ",
              Components.pageLink(Page.Login, "Sign in", "font-medium")
            )
        },
        div(
          cls := "grid grid-cols-1 sm:grid-cols-2 gap-4",
          Components.field(
            "Slug",
            if creating then Components.textInput(slug, "url-id, e.g. named-tuples")
            else div(cls := "text-sm text-slate-500 py-1.5 font-mono", slug.now())
          ),
          Components.field("Title", Components.textInput(title, "Display name"))
        ),
        Components.field("Tagline", Components.textInput(tagline, "One-line summary")),
        Components.field("Tags (comma-separated)", Components.textInput(tagsText, "syntax, types, …")),
        sectionBlock(
          "Sections",
          "+ Add section",
          div(cls := "space-y-3", children <-- sections.signal.map(_.map(sectionRow))),
          () => sections.update(_ :+ SectionDraft("", ""))
        ),
        div(
          cls := "grid grid-cols-1 sm:grid-cols-2 gap-4",
          availabilityBlock,
          sipBlock
        ),
        sectionBlock(
          "Links",
          "+ Add link",
          div(cls := "space-y-3", children <-- links.signal.map(_.map(linkRow))),
          () => links.update(_ :+ LinkDraft(LinkKind.Doc, "", "", false))
        ),
        sectionBlock(
          "Timeline",
          "+ Add milestone",
          div(cls := "space-y-3", children <-- timeline.signal.map(_.map(timelineRow))),
          () => timeline.update(_ :+ TimelineDraft("", "", ""))
        ),
        child <-- error.signal.map {
          case Some(m) => Components.errorBox(m)
          case None    => emptyNode
        },
        div(
          cls := "flex items-center gap-3 border-t border-slate-200 pt-4",
          button(
            cls := Components.btnPrimary,
            disabled <-- saving.signal,
            child.text <-- saving.signal.map(if _ then "Saving…" else if creating then "Create feature" else "Save changes"),
            onClick --> { _ => save() }
          ),
          a(
            cls := Components.btnSecondary,
            "Cancel",
            href := Routes.urlFor(slugOpt.map(Page.TopicView(_)).getOrElse(Page.Home)),
            onClick.preventDefault --> { _ =>
              Routes.router.pushState(slugOpt.map(Page.TopicView(_)).getOrElse(Page.Home))
            }
          ),
          if creating then emptyNode
          else
            button(
              cls := s"${Components.btnDanger} ml-auto",
              "Delete",
              onClick --> { _ => remove() }
            )
        )
      )

    Components.container(
      Components.pageLink(Page.Home, "← All features", "text-sm"),
      div(
        cls := "mt-3 max-w-3xl",
        Components.pageTitle(if creating then "New feature" else s"Edit ${slugOpt.getOrElse("")}"),
        div(cls := "mt-4", Components.loaded(loadState.signal)(_ => formBody))
      )
    )
