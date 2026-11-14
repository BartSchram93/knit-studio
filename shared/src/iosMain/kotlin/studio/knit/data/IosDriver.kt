package studio.knit.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import studio.knit.db.KnitDatabase

/** Opens (creating or migrating as needed) the app's database in the app's sandbox. */
fun iosDriver(name: String = DATABASE_NAME): SqlDriver = NativeSqliteDriver(KnitDatabase.Schema, name)
