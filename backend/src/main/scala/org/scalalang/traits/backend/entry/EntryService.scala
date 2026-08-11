package org.scalalang.traits.backend.entry

import org.scalalang.traits.backend.Db
import org.scalalang.traits.shared.*
import com.augustnagro.magnum.{connect, transact}
import upickle.default.{read, write}

import java.time.Instant
import javax.sql.DataSource

class EntryService(ds: DataSource):

  def list(): List[EntrySummary] =
    connect(ds) { EntryRepo.findAll }.map(parse).map(_.summary)

  def get(slug: String): Option[Entry] =
    connect(ds) { EntryRepo.find(slug) }.map(parse)

  def search(q: String): List[EntrySummary] =
    val term = q.trim.toLowerCase
    if term.isEmpty then list()
    else connect(ds) { EntryRepo.search(s"%$term%") }.map(parse).map(_.summary)

  /** Every entry with a status in `v`, with that status. */
  def statusIn(v: VersionId): List[EntryStatus] =
    connect(ds) { EntryRepo.findAll }
      .map(parse)
      .flatMap(e => e.statusIn(v).map(EntryStatus(e.slug, e.title, e.tagline, e.archived, _)))

  /** Create or replace. `updatedAt` is stamped here so it can't be spoofed by the client. */
  def put(slug: String, input: EntryInput): Entry =
    val entry = Entry(
      slug = slug,
      title = input.title,
      tagline = input.tagline,
      sections = input.sections,
      links = input.links,
      timeline = input.timeline,
      tags = input.tags,
      archived = input.archived,
      sip = input.sip,
      availability = input.availability,
      updatedAt = Instant.now.toString
    )
    transact(ds) { EntryRepo.upsert(slug, entry.updatedAt, searchText(entry), write(entry)) }
    entry

  def delete(slug: String): Boolean =
    transact(ds) { EntryRepo.delete(slug) } > 0

  def count(): Long =
    connect(ds) { Db.entryCount }

  private def parse(json: String): Entry = read[Entry](json)

  private def searchText(e: Entry): String =
    (List(e.title, e.tagline) ++ e.tags ++ e.sections.flatMap(s => List(s.heading, s.body)))
      .mkString(" ")
      .toLowerCase
