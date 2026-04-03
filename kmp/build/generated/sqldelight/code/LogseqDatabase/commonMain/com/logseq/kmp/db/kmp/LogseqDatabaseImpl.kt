package com.logseq.kmp.db.kmp

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.logseq.kmp.db.LogseqDatabase
import com.logseq.kmp.db.LogseqDatabaseQueries
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<LogseqDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = LogseqDatabaseImpl.Schema

internal fun KClass<LogseqDatabase>.newInstance(driver: SqlDriver): LogseqDatabase =
    LogseqDatabaseImpl(driver)

private class LogseqDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), LogseqDatabase {
  override val logseqDatabaseQueries: LogseqDatabaseQueries = LogseqDatabaseQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE pages (
          |    uuid TEXT NOT NULL PRIMARY KEY,
          |    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
          |    namespace TEXT,
          |    file_path TEXT,
          |    created_at INTEGER NOT NULL,
          |    updated_at INTEGER NOT NULL,
          |    properties TEXT, -- JSON string for page properties
          |    version INTEGER NOT NULL DEFAULT 0,
          |    is_favorite INTEGER DEFAULT 0,
          |    is_journal INTEGER DEFAULT 0,
          |    journal_date TEXT
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE blocks (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT, -- Hidden numeric ID for FTS5 internal use
          |    uuid TEXT NOT NULL UNIQUE,
          |    page_uuid TEXT NOT NULL,
          |    parent_uuid TEXT,
          |    left_uuid TEXT,
          |    content TEXT NOT NULL,
          |    level INTEGER NOT NULL DEFAULT 0,
          |    position INTEGER NOT NULL,
          |    created_at INTEGER NOT NULL,
          |    updated_at INTEGER NOT NULL,
          |    properties TEXT, -- JSON string for block properties
          |    version INTEGER NOT NULL DEFAULT 0,
          |    content_hash TEXT, -- SHA-256 hex digest of normalised content (used for deduplication)
          |    FOREIGN KEY (page_uuid) REFERENCES pages(uuid) ON DELETE CASCADE,
          |    FOREIGN KEY (parent_uuid) REFERENCES blocks(uuid) ON DELETE CASCADE,
          |    FOREIGN KEY (left_uuid) REFERENCES blocks(uuid) ON DELETE SET NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE properties (
          |    uuid TEXT NOT NULL PRIMARY KEY,
          |    block_uuid TEXT NOT NULL,
          |    key TEXT NOT NULL,
          |    value TEXT NOT NULL,
          |    created_at INTEGER NOT NULL,
          |    FOREIGN KEY (block_uuid) REFERENCES blocks(uuid) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE plugin_data (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    plugin_id TEXT NOT NULL,
          |    entity_type TEXT NOT NULL,
          |    entity_uuid TEXT NOT NULL,
          |    key TEXT NOT NULL,
          |    value TEXT NOT NULL,
          |    created_at INTEGER NOT NULL,
          |    updated_at INTEGER,
          |    UNIQUE(plugin_id, entity_type, entity_uuid, key)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE block_references (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    from_block_uuid TEXT NOT NULL,
          |    to_block_uuid TEXT NOT NULL,
          |    created_at INTEGER NOT NULL,
          |    UNIQUE(from_block_uuid, to_block_uuid),
          |    FOREIGN KEY (from_block_uuid) REFERENCES blocks(uuid) ON DELETE CASCADE,
          |    FOREIGN KEY (to_block_uuid) REFERENCES blocks(uuid) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, "CREATE INDEX idx_pages_uuid ON pages(uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_pages_name ON pages(name)", 0)
      driver.execute(null, "CREATE INDEX idx_pages_namespace ON pages(namespace)", 0)
      driver.execute(null, "CREATE INDEX idx_blocks_uuid ON blocks(uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_blocks_page_uuid ON blocks(page_uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_blocks_parent_uuid ON blocks(parent_uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_blocks_left_uuid ON blocks(left_uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_blocks_level ON blocks(level)", 0)
      driver.execute(null, "CREATE INDEX idx_blocks_content_hash ON blocks(content_hash)", 0)
      driver.execute(null, "CREATE INDEX idx_properties_block_uuid ON properties(block_uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_properties_key ON properties(key)", 0)
      driver.execute(null, "CREATE INDEX idx_plugin_data_plugin_id ON plugin_data(plugin_id)", 0)
      driver.execute(null,
          "CREATE INDEX idx_plugin_data_entity ON plugin_data(entity_type, entity_uuid)", 0)
      driver.execute(null, "CREATE INDEX idx_plugin_data_key ON plugin_data(key)", 0)
      driver.execute(null, "CREATE INDEX idx_references_from ON block_references(from_block_uuid)",
          0)
      driver.execute(null, "CREATE INDEX idx_references_to ON block_references(to_block_uuid)", 0)
      driver.execute(null, """
          |CREATE TRIGGER blocks_ai AFTER INSERT ON blocks BEGIN
          |    INSERT INTO blocks_fts(rowid, content) VALUES (new.id, new.content);
          |END
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TRIGGER blocks_ad AFTER DELETE ON blocks BEGIN
          |    INSERT INTO blocks_fts(blocks_fts, rowid, content)
          |    VALUES('delete', old.id, old.content);
          |END
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TRIGGER blocks_au AFTER UPDATE ON blocks BEGIN
          |    INSERT INTO blocks_fts(blocks_fts, rowid, content)
          |    VALUES('delete', old.id, old.content);
          |    INSERT INTO blocks_fts(rowid, content) VALUES (new.id, new.content);
          |END
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE VIRTUAL TABLE blocks_fts USING fts5(
          |    content,
          |    content=blocks,
          |    content_rowid=id,
          |    tokenize='porter unicode61'
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
