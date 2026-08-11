package org.scalalang.traits.frontend.pages

import org.scalalang.traits.frontend.ui.{Components, Loaded}
import org.scalalang.traits.frontend.{Api, Page, Routes, Session}
import org.scalalang.traits.frontend.Api.given
import org.scalalang.traits.shared.*
import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Create (`slugOpt = None`) or edit an existing entry. Writes go through the editor-gated
  * `putEntry` / `deleteEntry`; the form mirrors the [[EntryInput]] shape field-for-field, and runs
  * the shared validation rules before sending.
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
    val heading          = Var(heading0)
    val body             = Var(body0)
    def toModel: Section = Section(heading.now().trim, body.now())

  private final class LinkDraft(kind0: LinkKind, title0: String, url0: String, watch0: Boolean):
    val kind          = Var(kind0)
    val title         = Var(title0)
    val url           = Var(url0)
    val watch         = Var(watch0)
    def toModel: Link = Link(kind.now(), title.now().trim, url.now().trim, watch.now())

  private final class TimelineDraft(date0: String, summary0: String, source0: String):
    val date    = Var(date0)
    val summary = Var(summary0)
    val source  = Var(source0)
    def toModel: TimelineEntry =
      TimelineEntry(date.now().trim, summary.now().trim, Some(source.now().trim).filter(_.nonEmpty))

  private final class AvailabilityDraft(
      stage0: AvailabilityStage,
      version0: String,
      backport0: Boolean,
      note0: String
  ):
    val stage    = Var(stage0)
    val version  = Var(version0)
    val backport = Var(backport0)
    val note     = Var(note0)

    /** Left when the version text doesn't parse; the shared rules validate the rest. */
    def toModel: Either[String, Availability] =
      val text = version.now().trim
      val parsed =
        if text.isEmpty then Right(None)
        else VersionId.parse(text).map(Some(_)).toRight(s"invalid version '$text' (want e.g. 3.8)")
      parsed.map(v =>
        Availability(stage.now(), v, backport.now(), Some(note.now()).filter(_.trim.nonEmpty))
      )

  def apply(slugOpt: Option[String]): HtmlElement =
    val creating = slugOpt.isEmpty

    val slug         = Var(slugOpt.getOrElse(""))
    val title        = Var("")
    val tagline      = Var("")
    val tagsText     = Var("")
    val archived     = Var(false)
    val sections     = Var(List.empty[SectionDraft])
    val links        = Var(List.empty[LinkDraft])
    val timeline     = Var(List.empty[TimelineDraft])
    val availability = Var(List.empty[AvailabilityDraft])

    val sipEnabled = Var(false)
    val sipNumber  = Var("")
    val sipTitle   = Var("")
    val sipUrl     = Var("")
    val sipState   = Var[SipState](SipState.PreSipSubmitted)

    val loadState = Var[Loaded[Unit]](if creating then Loaded.Ok(()) else Loaded.Loading)
    val error     = Var[Option[String]](None)
    val saving    = Var(false)

    slugOpt.foreach { s =>
      Api.getEntry(s).foreach {
        case Right(e) =>
          title.set(e.title)
          tagline.set(e.tagline)
          tagsText.set(e.tags.mkString(", "))
          archived.set(e.archived)
          sections.set(e.sections.map(sec => SectionDraft(sec.heading, sec.body)))
          links.set(e.links.map(l => LinkDraft(l.kind, l.title, l.url, l.watch)))
          timeline
            .set(e.timeline.map(t => TimelineDraft(t.date, t.summary, t.sourceUrl.getOrElse(""))))
          availability.set(
            e.availability.map(a =>
              AvailabilityDraft(
                a.stage,
                a.version.fold("")(_.render),
                a.backport,
                a.note.getOrElse("")
              )
            )
          )
          e.sip.foreach { sp =>
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

    def buildInput(): Either[List[String], EntryInput] =
      val (parseErrors, avs) = availability.now().map(_.toModel).partitionMap(identity)
      if parseErrors.nonEmpty then Left(parseErrors)
      else
        val input = EntryInput(
          title = title.now().trim,
          tagline = tagline.now().trim,
          sections = sections.now().map(_.toModel),
          links = links.now().map(_.toModel),
          timeline = timeline.now().map(_.toModel),
          tags = tagsText.now().split(",").map(_.trim).filter(_.nonEmpty).toList,
          archived = archived.now(),
          sip =
            if sipEnabled.now() then
              Some(
                Sip(
                  Some(sipNumber.now().trim).filter(_.nonEmpty),
                  sipTitle.now().trim,
                  sipUrl.now().trim,
                  sipState.now()
                )
              )
            else None,
          availability = avs
        )
        input.validate match
          case Nil    => Right(input)
          case errors => Left(errors)

    def save(): Unit =
      val s = slug.now().trim
      if s.isEmpty || title.now().trim.isEmpty then error.set(Some("Slug and title are required."))
      else if !saving.now() then
        buildInput() match
          case Left(errors) => error.set(Some(errors.mkString("; ")))
          case Right(input) =>
            saving.set(true)
            error.set(None)
            Api.putEntry(s, input).foreach {
              case Right(e) =>
                saving.set(false)
                Routes.router.pushState(Page.EntryView(e.slug))
              case Left(e) =>
                saving.set(false)
                error.set(Some(e.message))
            }

    def remove(): Unit =
      if dom.window.confirm(s"Delete '${slug.now()}'? This cannot be undone.") then
        Api.deleteEntry(slug.now()).foreach {
          case Right(_) => Routes.router.pushState(Page.Home())
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
            "Watch (a curating agent re-reads this link)"
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

    def availabilityRow(d: AvailabilityDraft): HtmlElement =
      div(
        cls := "border border-slate-200 rounded-lg p-3 space-y-2 bg-white",
        div(
          cls := "grid grid-cols-1 sm:grid-cols-[10rem_8rem_1fr] gap-2 items-end",
          Components.field(
            "Stage",
            Components.selectInput(
              d.stage,
              AvailabilityStage.values.toList,
              AvailabilityStage.label,
              _.toString
            )
          ),
          Components.field("Version", Components.textInput(d.version, "e.g. 3.8")),
          div(
            cls := "flex items-center justify-between pb-1.5",
            label(
              cls := "flex items-center gap-2 text-sm text-slate-600",
              Components.checkboxInput(d.backport),
              "Backport"
            ),
            button(
              cls := "text-rose-600 text-sm hover:underline",
              "Remove",
              onClick --> { _ => availability.update(_.filterNot(_ eq d)) }
            )
          )
        ),
        Components.multilineInput(d.note, 2, "How to enable in this state (markdown, optional)…")
      )

    def sectionBlock(
        heading: String,
        addLabel: String,
        rows: HtmlElement,
        onAdd: () => Unit
    ): HtmlElement =
      div(
        cls := "space-y-3",
        h2(cls := "text-lg font-semibold text-slate-800", heading),
        rows,
        button(cls := Components.btnSecondary, addLabel, onClick --> { _ => onAdd() })
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
                Components
                  .field("Number (optional)", Components.textInput(sipNumber, "e.g. SIP-58")),
                Components.field("Title", Components.textInput(sipTitle, "Proposal title"))
              ),
              Components.field("URL", Components.textInput(sipUrl, "https://…")),
              Components.field(
                "State",
                Components.selectInput(sipState, allSipStates, SipState.label, _.toString)
              )
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
        div(
          cls := "grid grid-cols-1 sm:grid-cols-2 gap-4 items-end",
          Components
            .field("Tags (comma-separated)", Components.textInput(tagsText, "syntax, types, …")),
          label(
            cls := "flex items-center gap-2 text-sm text-slate-600 pb-1.5",
            Components.checkboxInput(archived),
            "Archived (hidden from the boards)"
          )
        ),
        sectionBlock(
          "Sections",
          "+ Add section",
          div(cls := "space-y-3", children <-- sections.signal.map(_.map(sectionRow))),
          () => sections.update(_ :+ SectionDraft("", ""))
        ),
        sectionBlock(
          "Availability",
          "+ Add availability",
          div(cls := "space-y-3", children <-- availability.signal.map(_.map(availabilityRow))),
          () =>
            availability.update(
              _ :+ AvailabilityDraft(AvailabilityStage.Experimental, "", false, "")
            )
        ),
        sipBlock,
        sectionBlock(
          "Links",
          "+ Add link",
          div(cls := "space-y-3", children <-- links.signal.map(_.map(linkRow))),
          () => links.update(_ :+ LinkDraft(LinkKind.Doc, "", "", false))
        ),
        sectionBlock(
          "Timeline",
          "+ Add event",
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
            child.text <-- saving.signal.map(
              if _ then "Saving…" else if creating then "Create entry" else "Save changes"
            ),
            onClick --> { _ => save() }
          ),
          a(
            cls := Components.btnSecondary,
            "Cancel",
            href := Routes.urlFor(slugOpt.map(Page.EntryView(_)).getOrElse(Page.Home())),
            onClick.preventDefault --> { _ =>
              Routes.router.pushState(slugOpt.map(Page.EntryView(_)).getOrElse(Page.Home()))
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
      Components.pageLink(Page.Home(), "← All features", "text-sm"),
      div(
        cls := "mt-3 max-w-3xl",
        Components.pageTitle(if creating then "New entry" else s"Edit ${slugOpt.getOrElse("")}"),
        div(cls := "mt-4", Components.loaded(loadState.signal)(_ => formBody))
      )
    )
