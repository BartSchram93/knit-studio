package studio.knit.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import studio.knit.companion.knitGlance
import studio.knit.model.Pattern
import studio.knit.model.Row
import studio.knit.model.Section
import studio.knit.model.SizedInt

/** The lock-screen notification is posted with its buttons, and "Next row" moves the knitting on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = KnitApplication::class)
class LockScreenTest {
    @Test fun notificationShowsRowAndNextButtonAdvances() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KnitApplication>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val c = app.container
        c.repo.create(Pattern(id = "lock-1", name = "Cowl", sections = listOf(
            Section("s1", "Rib", castOn = SizedInt(20), rows = listOf(Row("r1", text = "K2, P2, rep to end."), Row("r2", text = "P2, K2, rep to end."))),
        )))

        c.lockScreen.show(knitGlance(c.repo.getPattern("lock-1")!!, c.library.value)!!)
        val manager = app.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Row 1 of 2 (RS) · Rib", posted.extras.getString("android.title"))
        assertEquals(listOf("Next row"), posted.actions.map { it.title.toString() })
        assertEquals(android.app.Notification.VISIBILITY_PUBLIC, posted.visibility)
        // The custom layouts inflate and show the row and the instruction.
        val big = posted.bigContentView.apply(app, android.widget.FrameLayout(app))
        assertEquals("ROW 1/2", big.findViewById<android.widget.TextView>(R.id.row).text.toString())
        assertEquals("RS", big.findViewById<android.widget.TextView>(R.id.side).text.toString())
        assertEquals("K2, P2, rep to end.", big.findViewById<android.widget.TextView>(R.id.instruction).text.toString())
        val small = posted.contentView.apply(app, android.widget.FrameLayout(app))
        assertEquals("ROW 1/2 · RS", small.findViewById<android.widget.TextView>(R.id.row).text.toString())

        // Press "Next row" the way the notification does.
        app.sendBroadcast(Intent(app, KnitStepReceiver::class.java).setAction(AndroidLockScreen.ACTION_STEP)
            .putExtra(AndroidLockScreen.EXTRA_PATTERN, "lock-1").putExtra(AndroidLockScreen.EXTRA_DELTA, 1))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        var saved = c.repo.getPattern("lock-1")!!
        for (i in 0 until 50) {
            if (saved.progress?.positions?.get("s1") == 1) break
            Thread.sleep(50); shadowOf(android.os.Looper.getMainLooper()).idle()
            saved = c.repo.getPattern("lock-1")!!
        }
        assertEquals(1, saved.progress!!.positions["s1"])
        val updated = shadowOf(manager).allNotifications.single()
        assertEquals("Row 2 of 2 (WS) · Rib", updated.extras.getString("android.title"))
        assertEquals(listOf("Back", "Next row"), updated.actions.map { it.title.toString() })

        c.lockScreen.clear()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
