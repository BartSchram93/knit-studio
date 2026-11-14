package studio.knit.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import studio.knit.db.KnitDatabase

/** Opens (creating or migrating as needed) the app's database in private storage. */
fun androidDriver(context: Context, name: String = DATABASE_NAME): SqlDriver =
    AndroidSqliteDriver(KnitDatabase.Schema, context.applicationContext, name)
