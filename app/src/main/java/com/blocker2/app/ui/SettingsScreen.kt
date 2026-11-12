package com.blocker2.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    language: String,
    themeMode: String,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit
) {
    val english = language == "en"
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (english) "App language" else "زبان برنامه", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "Choose the language for the app navigation and settings." else "زبان منوها و تنظیمات برنامه را انتخاب کن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (language == "fa") Button(onClick = { onLanguageChange("fa") }) { Text("فارسی") }
                    else OutlinedButton(onClick = { onLanguageChange("fa") }) { Text("فارسی") }
                    if (language == "en") Button(onClick = { onLanguageChange("en") }) { Text("English") }
                    else OutlinedButton(onClick = { onLanguageChange("en") }) { Text("English") }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (english) "Appearance" else "پوستهٔ ظاهری", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (english) "System follows Android. Light and dark stay fixed." else "حالت سیستم از تنظیمات اندروید پیروی می‌کند؛ روشن و تاریک ثابت می‌مانند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        Triple("system", if (english) "System default" else "هماهنگ با سیستم", if (english) "Uses the device appearance" else "پیروی از ظاهر گوشی"),
                        Triple("light", if (english) "Light" else "روشن", if (english) "Always use light colors" else "همیشه پوستهٔ روشن"),
                        Triple("dark", if (english) "Dark" else "تاریک", if (english) "Always use dark colors" else "همیشه پوستهٔ تاریک")
                    )
                    modes.forEach { (value, label, description) ->
                        val selected = themeMode == value
                        if (selected) {
                            Button(onClick = { onThemeChange(value) }, modifier = Modifier.fillMaxWidth()) {
                                Text("$label  •  $description")
                            }
                        } else {
                            OutlinedButton(onClick = { onThemeChange(value) }, modifier = Modifier.fillMaxWidth()) {
                                Text("$label  •  $description")
                            }
                        }
                    }
                }
            }
        }
        Text(
            if (english) "The main navigation, dashboard and settings are available in English. Other feature screens are being translated in stages." else "منوی اصلی، داشبورد و تنظیمات به فارسی و انگلیسی در دسترس‌اند. ترجمهٔ سایر بخش‌ها به‌تدریج تکمیل می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
