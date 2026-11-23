package studio.knit.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The local calendar day of a moment. */
@OptIn(ExperimentalTime::class)
fun localDateOf(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

/** Noon on [date] in the device's time zone: a moment that stays on that day in any nearby zone. */
@OptIn(ExperimentalTime::class)
fun noonOf(date: LocalDate): Long = LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

/** A row showing a date; tapping it opens a calendar to change it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun DateRow(title: String, millis: Long?, onPick: (Long) -> Unit, subtitle: String? = null, emptyText: String = "Set date") {
    var open by remember { mutableStateOf(false) }
    ListRow(
        title, subtitle = subtitle,
        value = millis?.let { formatDay(localDateOf(it)) } ?: emptyText,
        chevron = true,
    ) { open = true }
    if (open) {
        // The calendar works in UTC days; convert to and from the local day.
        val initial = (millis?.let { localDateOf(it) } ?: localDateOf(studio.knit.util.nowMillis()))
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextBtn("OK", {
                    state.selectedDateMillis?.let { utc ->
                        onPick(noonOf(Instant.fromEpochMilliseconds(utc).toLocalDateTime(TimeZone.UTC).date))
                    }
                    open = false
                }, bold = true)
            },
            dismissButton = { TextBtn("Cancel", { open = false }) },
        ) { DatePicker(state) }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "21 Nov 2026". */
fun formatDay(d: LocalDate): String = "${d.day} ${MONTHS[d.month.ordinal]} ${d.year}"
