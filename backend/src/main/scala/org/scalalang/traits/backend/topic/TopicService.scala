package org.scalalang.traits.backend.topic

import org.scalalang.traits.backend.Db
import org.scalalang.traits.shared.*
import com.augustnagro.magnum.{connect, transact}
import upickle.default.{read, write}

import java.time.Instant
import javax.sql.DataSource

class TopicService(ds: DataSource):

  def list(): List[FeatureSummary] =
    connect(ds) { TopicRepo.findAll }.map(parse).map(_.summary)

  def get(slug: String): Option[Topic] =
    connect(ds) { TopicRepo.find(slug) }.map(parse)

  def search(q: String): List[FeatureSummary] =
    val term = q.trim.toLowerCase
    if term.isEmpty then list()
    else connect(ds) { TopicRepo.search(s"%$term%") }.map(parse).map(_.summary)

  /** All timeline entries across topics, newest first. */
  def changelog(limit: Option[Int]): List[ChangelogEntry] =
    val entries = connect(ds) { TopicRepo.findAll }
      .map(parse)
      .flatMap(t =>
        t.timeline.map(e => ChangelogEntry(e.date, t.slug, t.title, e.summary, e.sourceUrl, t.lane))
      )
      .sortBy(_.date)(using Ordering[String].reverse)
    limit.fold(entries)(entries.take)

  /** Create or replace. `updatedAt` is stamped here so it can't be spoofed by the client. */
  def put(slug: String, input: TopicInput): Topic =
    val topic = Topic(
      slug = slug,
      title = input.title,
      tagline = input.tagline,
      sections = input.sections,
      availability = input.availability,
      sip = input.sip,
      links = input.links,
      timeline = input.timeline,
      tags = input.tags,
      updatedAt = Instant.now.toString
    )
    transact(ds) { TopicRepo.upsert(slug, topic.updatedAt, searchText(topic), write(topic)) }
    topic

  def delete(slug: String): Boolean =
    transact(ds) { TopicRepo.delete(slug) } > 0

  def count(): Long =
    connect(ds) { Db.topicCount }

  private def parse(json: String): Topic = read[Topic](json)

  private def searchText(t: Topic): String =
    (List(t.title, t.tagline) ++ t.tags ++ t.sections.flatMap(s => List(s.heading, s.body)))
      .mkString(" ")
      .toLowerCase
