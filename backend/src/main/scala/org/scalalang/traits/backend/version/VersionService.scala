package org.scalalang.traits.backend.version

import org.scalalang.traits.shared.{Version, VersionId, VersionInput}
import com.augustnagro.magnum.{connect, transact}
import upickle.default.{read, write}

import javax.sql.DataSource

class VersionService(ds: DataSource):

  def list(): List[Version] =
    connect(ds) { VersionRepo.findAll }.map(read[Version](_))

  def put(v: VersionId, input: VersionInput): Version =
    val version = Version(v, input.lts, input.released, input.releaseDate, input.releaseNotesUrl)
    transact(ds) { VersionRepo.upsert(v, write(version)) }
    version

  def delete(v: VersionId): Boolean =
    transact(ds) { VersionRepo.delete(v) } > 0
