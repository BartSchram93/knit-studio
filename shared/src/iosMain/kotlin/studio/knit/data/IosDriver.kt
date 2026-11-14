package studio.knit.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import studio.knit.db.KnitDatabase

/**
 * Where the database lives on iPhone: `Library/Application Support/databases/` inside the app's
 * own container. iOS keeps this folder when the app is updated or re-signed (AltStore refreshes),
 * and includes it in iCloud and computer backups of the phone.
 *
 * This is the SQLite driver's default location; it is set explicitly so that a future library
 * upgrade can never silently move the database and make existing patterns appear to be gone.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal fun databaseDirectory(): String {
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    val dir = "$support/databases"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

/** Opens (creating or migrating as needed) the app's database in the app's sandbox. */
fun iosDriver(name: String = DATABASE_NAME): SqlDriver = NativeSqliteDriver(
    schema = KnitDatabase.Schema,
    name = name,
    onConfiguration = { config -> config.copy(extendedConfig = config.extendedConfig.copy(basePath = databaseDirectory())) },
)
