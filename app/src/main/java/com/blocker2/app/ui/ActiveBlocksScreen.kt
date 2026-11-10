package com.blocker2.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blocker2.app.accessibility.BlockNotificationManager
import com.blocker2.app.data.local.dao.ActiveAppBlockDao
import com.blocker2.app.data.local.entity.ActiveAppBlockEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun ActiveBlocksScreen(dao: ActiveAppBlockDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
            dao.deleteExpired(now)
        }
    }

    val blocks by remember(dao, now) { dao.observeActive(now) }
        .collectAsState(initial = emptyList())

    if (blocks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("هیچ برنامه‌ای در حال حاضر مسدود نیست.", fontWeight = FontWeight.SemiBold)
            Text(
                "وقتی یک قانون واژه باعث مسدودسازی زمان‌دار شود، وضعیت آن در این صفحه نمایش داده می‌شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(blocks, key = { it.packageName }) { block ->
            ActiveBlockCard(
                context = context,
                block = block,
                now = now,
                onExtend = {
                    scope.launch {
                        val newExpiry = block.expiresAt + EXTENSION_MILLIS
                        val updated = dao.extendActive(
                            packageName = block.packageName,
                            newExpiresAt = newExpiry,
                            now = System.currentTimeMillis()
                        )
                        if (updated > 0) {
                            BlockNotificationManager.showTimedBlock(
                                context = context,
                                packageName = block.packageName,
                                expiresAt = newExpiry
                            )
                        }
                    }
                },
                onClear = {
                    scope.launch {
                        dao.clearForPackage(block.packageName)
                        BlockNotificationManager.cancelTimedBlock(context, block.packageName)
                    }
                }
            )
        }
    }
}

@Composable
private fun ActiveBlockCard(
    context: Context,
    block: ActiveAppBlockEntity,
    now: Long,
    onExtend: () -> Unit,
    onClear: () -> Unit
) {
    val appName = remember(block.packageName) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(block.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(block.packageName)
    }
    val remaining = (block.expiresAt - now).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "زمان باقی‌مانده: %02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (block.reason.isNotBlank()) {
                Text(
                    "دلیل: ${block.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                block.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExtend) { Text("تمدید ۵ دقیقه") }
                OutlinedButton(onClick = onClear) { Text("لغو مسدودی") }
            }
        }
    }
}

private const val EXTENSION_MILLIS = 5 * 60 * 1_000L
