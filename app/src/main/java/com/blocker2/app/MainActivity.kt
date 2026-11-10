package com.blocker2.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker2.app.ui.theme.Blocker2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Blocker2Theme {
                Blocker2App()
            }
        }
    }
}

private enum class MainSection(val title: String, val subtitle: String) {
    HOME("خانه", "وضعیت کلی محافظت"),
    WORDS("واژه‌ها", "مدیریت واژه‌ها و عبارت‌ها"),
    SITES("سایت‌ها", "مدیریت دامنه‌ها و نشانی‌ها"),
    APPS("برنامه‌ها", "انتخاب برنامه‌های تحت نظارت")
}

@androidx.compose.runtime.Composable
private fun Blocker2App() {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = MainSection.entries

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "BLOCKER2",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = "کنترل آگاهانه، تمرکز بیشتر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "نسخه ۱.۰.۰",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = sections[selectedSection].title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sections[selectedSection].subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (sections[selectedSection]) {
                    MainSection.HOME -> HomeOverview()
                    MainSection.WORDS -> EmptySection(
                        title = "مدیریت واژه‌ها",
                        description = "در این بخش قوانین واژه و عبارت، زبان، برنامه‌های هدف و واکنش هر قانون پیاده‌سازی می‌شوند."
                    )
                    MainSection.SITES -> EmptySection(
                        title = "فیلتر سایت‌ها",
                        description = "در این بخش قوانین دامنه، زیر دامنه، IP و فهرست مجاز مدیریت خواهند شد."
                    )
                    MainSection.APPS -> EmptySection(
                        title = "برنامه‌های تحت نظارت",
                        description = "در این بخش می‌توان برنامه‌های موردنظر را برای پایش انتخاب کرد و Blocker2 را مستثنا نگه داشت."
                    )
                }
            }

            NavigationBar {
                sections.forEachIndexed { index, section ->
                    NavigationBarItem(
                        selected = selectedSection == index,
                        onClick = { selectedSection = index },
                        icon = {},
                        label = { Text(section.title) }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HomeOverview() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard(
            title = "فیلتر واژه‌ها",
            status = "هنوز راه‌اندازی نشده"
        )
        StatusCard(
            title = "فیلتر سایت‌ها",
            status = "هنوز راه‌اندازی نشده"
        )
        StatusCard(
            title = "سرویس دسترس‌پذیری",
            status = "نیازمند پیاده‌سازی و مجوز"
        )
        StatusCard(
            title = "اتصال VPN محلی",
            status = "نیازمند پیاده‌سازی و مجوز"
        )
        Spacer(modifier = Modifier.padding(2.dp))
        Text(
            text = "این نسخه، پوسته اولیه پروژه است. وضعیت‌ها عمداً واقعی و شفاف نمایش داده می‌شوند؛ هیچ فیلتری تا زمان تکمیل موتور مربوطه فعال اعلام نمی‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@androidx.compose.runtime.Composable
private fun StatusCard(title: String, status: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptySection(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
