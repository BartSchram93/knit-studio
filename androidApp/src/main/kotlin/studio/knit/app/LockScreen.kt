package studio.knit.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import studio.knit.companion.KnitGlance
import studio.knit.ui.app.LockScreenKnitting

/**
 * The row being knitted as a quiet, ongoing notification that is fully visible on the lock
 * screen, with "Back" and "Next row" buttons that work without unlocking the phone.
 */
class AndroidLockScreen(private val context: Context) : LockScreenKnitting {
    /** Called when the notification permission (Android 13+) is still missing. */
    var onPermissionNeeded: (() -> Unit)? = null
    private var asked = false

    override fun show(glance: KnitGlance) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!asked) { asked = true; onPermissionNeeded?.invoke() }
            return
        }
        ensureChannel(context)
        val n = build(context, glance)
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    override fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "knitting"
        const val NOTIFICATION_ID = 7
        const val ACTION_STEP = "studio.knit.app.STEP"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_DELTA = "delta"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(CHANNEL_ID, "Knitting on the lock screen", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The row you are knitting, with Back and Next buttons, while Knit mode is open"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        fun build(context: Context, g: KnitGlance): android.app.Notification {
            fun step(delta: Int, code: Int) = PendingIntent.getBroadcast(
                context, code,
                Intent(context, KnitStepReceiver::class.java).setAction(ACTION_STEP)
                    .putExtra(EXTRA_PATTERN, g.patternId).putExtra(EXTRA_DELTA, delta),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // "Row 3 of 15 (RS)" → badge "ROW 3/15" and side "RS".
            val m = Regex("""^(\w+) (\d+) of (\d+)(?: \((RS|WS)\))?$""").find(g.row)
            val badge = m?.let { "${it.groupValues[1].uppercase()} ${it.groupValues[2]}/${it.groupValues[3]}" } ?: g.row.uppercase()
            val side = m?.groupValues?.get(4).orEmpty()
            val detail = listOf(g.part, g.detail).filter { it.isNotBlank() }.joinToString(" · ")
            val big = android.widget.RemoteViews(context.packageName, R.layout.notification_row).apply {
                setTextViewText(R.id.row, badge)
                setTextViewText(R.id.side, side)
                setTextViewText(R.id.today, g.today)
                setTextViewText(R.id.detail, detail)
                setTextViewText(R.id.instruction, g.instruction)
                val f = g.fraction
                if (f == null) setViewVisibility(R.id.progress, android.view.View.GONE)
                else setProgressBar(R.id.progress, 1000, (f * 1000).toInt(), false)
            }
            val small = android.widget.RemoteViews(context.packageName, R.layout.notification_row_small).apply {
                setTextViewText(R.id.row, if (side.isEmpty()) badge else "$badge · $side")
                setTextViewText(R.id.instruction, g.instruction)
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_knit)
                // Title and text stay set for accessibility, watches and the tests.
                .setContentTitle("${g.row} · ${g.part}")
                .setContentText(g.instruction)
                .setSubText(g.patternName)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(big)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(open)
                .apply {
                    if (g.canGoBack) addAction(0, "Back", step(-1, 1))
                    if (!g.finished) addAction(0, "Next row", step(1, 2))
                }
                .build()
        }
    }
}

/** Handles the lock-screen buttons, also when the app is no longer on screen. */
class KnitStepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidLockScreen.ACTION_STEP) return
        val id = intent.getStringExtra(AndroidLockScreen.EXTRA_PATTERN) ?: return
        val delta = intent.getIntExtra(AndroidLockScreen.EXTRA_DELTA, 0).coerceIn(-1, 1)
        if (delta == 0) return
        val app = context.applicationContext as KnitApplication
        val pending = goAsync()
        app.container.stepFromLockScreen(id, delta) { pending.finish() }
    }
}
