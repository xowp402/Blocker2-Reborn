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
import com.blocker2.app.accessibility.TimedBlockTime
import com.blocker2.app.data.local.dao.ActiveAppBlockDao
import com.blocker2.app.data.local.entity.ActiveAppBlockEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun ActiveBlocksScreen(dao: ActiveAppBlockDao, language: String) {
    val english = language == "en";
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
            Text(if (english) "No apps are currently blocked." else "هیچ برنامه‌ای در حال حاضر مسدود نیست.",  fontWeight = FontWeight.SemiBold)
            Text(
                if (english) "When a word rule triggers a timed block, its status appears on this screen." else "وقتی یک قانون واژه باعث مسدودسازی زمان‌دار شود، وضعیت آن در این صفحه نمایش داده می‌شود.",
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
                language = language,
                now = now,
                onExtend = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val updated = dao.extendActive(
                            packageName = block.packageName,
                            extensionMillis = EXTENSION_MILLIS,
                            now = now
                        )
                        if (updated > 0) {
                            val extendedBlock = dao.findActive(
                                packageName = block.packageName,
                                now = System.currentTimeMillis()
                            ) ?: return@launch
                            BlockNotificationManager.showTimedBlock(
                                context = context,
                                packageName = block.packageName,
                                expiresAt = extendedBlock.expiresAt
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
    language: String,
    now: Long,
    onExtend: () -> Unit,
    onClear: () -> Unit
) {
    val english = language == "en"
    val appName = remember(block.packageName) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(block.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(block.packageName)
    }
    val remaining = TimedBlockTime.remainingMillis(block.expiresAt, now)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                (if (english) "Time remaining: %02d:%02d" else "زمان باقی‌مانده: %02d:%02d").format(minutes, seconds),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (block.reason.isNotBlank()) {
                Text(
                    if (english) "Reason: ${block.reason}" else "دلیل: ${block.reason}",
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
                Button(onClick = onExtend) { Text(if (english) "Extend 5 min" else "تمدید ۵ دقیقه") }
                OutlinedButton(onClick = onClear) { Text(if (english) "Clear block" else "لغو مسدودی") }
            }
        }
    }
}

private const val EXTENSION_MILLIS = 5 * 60 * 1_000L
