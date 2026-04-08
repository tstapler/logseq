package com.logseq.kmp.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.logseq.kmp.db.kmp.newInstance
import com.logseq.kmp.db.kmp.schema
import kotlin.Unit

public interface LogseqDatabase : Transacter {
  public val logseqDatabaseQueries: LogseqDatabaseQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = LogseqDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): LogseqDatabase = LogseqDatabase::class.newInstance(driver)
  }
}
