package studio.knit.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import studio.knit.db.KnitDatabase
import java.util.Properties

/**
 * JVM driver used by tests and the desktop preview. [path] of null gives an in-memory database.
 * Creates the schema on first use and migrates older files, like the mobile drivers do.
 */
fun jvmDriver(path: String? = null): SqlDriver {
    val url = if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path"
    return JdbcSqliteDriver(url, Properties(), KnitDatabase.Schema)
}
