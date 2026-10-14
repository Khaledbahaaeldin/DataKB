package io.github.khaledbahaaeldin.emberbyte.plans

import android.annotation.SuppressLint
import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NonObservableLocale")
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val dateFormatted = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)

    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("$label: $dateFormatted")
    }

    if (open) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        open = false
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { open = false },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    time: LocalTime,
    onChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val timeFormatted = DateFormat.getTimeFormat(context).format(
        Date.from(time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()),
    )

    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("$label $timeFormatted")
    }

    if (open) {
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        open = false
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { open = false },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            },
        )
    }
}
