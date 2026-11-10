package com.blocker2.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blocker2.app.data.local.dao.CommitmentSessionDao
import com.blocker2.app.data.local.entity.CommitmentSessionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private data class CommitmentDuration(val label: String, val millis: Long)

private val commitmentDurations = listOf(
    CommitmentDuration("۱ ساعت", TimeUnit.HOURS.toMillis(1)),
    CommitmentDuration("۳ ساعت", TimeUnit.HOURS.toMillis(3)),
    CommitmentDuration("۶ ساعت", TimeUnit.HOURS.toMillis(6)),
    CommitmentDuration("۱۲ ساعت", TimeUnit.HOURS.toMillis(12)),
    CommitmentDuration("۲۴ ساعت", TimeUnit.HOURS.toMillis(24))
)

@Composable
fun CommitmentScreen(dao: CommitmentSessionDao) {
    val scope = rememberCoroutineScope()
    val session by dao.observeSession().collectAsState(initial = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDuration by remember { mutableLongStateOf(commitmentDurations.first().millis) }
    var showStartConfirmation by remember { mutableStateOf(false) }
    var showEmergencyWarning by remember { mutableStateOf(false) }
    var showEmergencyFinal by remember { mutableStateOf(false) }
    var acknowledgedEmergency by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    val activeSession = session?.takeIf { it.expiresAt > now }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("حالت تعهد شخصی", style = MaterialTheme.typography.headlineSmall)
        Text(
            "در طول تعهد، برنامه‌هایی که در بخش «برنامه‌ها» برای نظارت فعال کرده‌ای، محدود می‌شوند. Blocker2 برای مدیریت جلسه از این محدودیت مستثناست.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (activeSession != null) {
            val remaining = (activeSession.expiresAt - now).coerceAtLeast(0L)
            val hours = TimeUnit.MILLISECONDS.toHours(remaining)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("تعهد فعال است", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "%02d:%02d:%02d".format(hours, minutes, seconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "پایان: ${java.text.DateFormat.getDateTimeInstance().format(activeSession.expiresAt)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "برای پایان زودهنگام باید دو مرحله تأیید خروج اضطراری را انجام بدهی.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = {
                        acknowledgedEmergency = false
                        showEmergencyWarning = true
                    }) {
                        Text("درخواست خروج اضطراری")
                    }
                }
            }
        } else {
            Text("مدت تعهد را انتخاب کن", style = MaterialTheme.typography.titleMedium)
            commitmentDurations.forEach { duration ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDuration == duration.millis,
                        onClick = { selectedDuration = duration.millis }
                    )
                    Text(duration.label)
                }
            }
            Button(
                onClick = { showStartConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("شروع تعهد")
            }
        }

        Text(
            "محدودیت مهم: اندروید اجازه نمی‌دهد یک برنامه عادی جلوی Force Stop، حذف برنامه یا تغییر مجوز Accessibility از تنظیمات سیستم را به‌طور قطعی بگیرد. برای اجرای محدودیت‌ها، سرویس دسترس‌پذیری باید فعال بماند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showStartConfirmation) {
        AlertDialog(
            onDismissRequest = { showStartConfirmation = false },
            title = { Text("شروع حالت تعهد؟") },
            text = {
                Text("برای مدت انتخاب‌شده، برنامه‌های تحت نظارت محدود می‌شوند. پایان عادی پیش از موعد در دسترس نیست؛ فقط خروج اضطراری چندمرحله‌ای وجود دارد.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val started = System.currentTimeMillis()
                    scope.launch {
                        dao.saveSession(
                            CommitmentSessionEntity(
                                startedAt = started,
                                expiresAt = started + selectedDuration
                            )
                        )
                    }
                    showStartConfirmation = false
                }) { Text("تأیید و شروع") }
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirmation = false }) { Text("انصراف") }
            }
        )
    }

    if (showEmergencyWarning) {
        AlertDialog(
            onDismissRequest = { showEmergencyWarning = false },
            title = { Text("مرحله اول خروج اضطراری") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("خروج اضطراری تعهد فعلی را فوراً پایان می‌دهد. فقط در صورت ضرورت ادامه بده.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = acknowledgedEmergency,
                            onCheckedChange = { acknowledgedEmergency = it }
                        )
                        Text("پیامد پایان زودهنگام تعهد را می‌پذیرم.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = acknowledgedEmergency,
                    onClick = {
                        showEmergencyWarning = false
                        showEmergencyFinal = true
                    }
                ) { Text("ادامه به مرحله دوم") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyWarning = false }) { Text("بازگشت") }
            }
        )
    }

    if (showEmergencyFinal) {
        AlertDialog(
            onDismissRequest = { showEmergencyFinal = false },
            title = { Text("تأیید نهایی خروج") },
            text = { Text("آیا مطمئنی که می‌خواهی جلسه تعهد را همین حالا پایان بدهی؟ این عمل قابل بازگردانی نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.endSession() }
                    showEmergencyFinal = false
                }) { Text("بله، پایان تعهد") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyFinal = false }) { Text("ادامه تعهد") }
            }
        )
    }
}
