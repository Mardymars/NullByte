package com.nullbyte.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    notificationsEnabled: Boolean,
    remindersEnabled: Boolean,
    showTutorialOnLaunch: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onShowTutorialOnLaunchChanged: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        val wideLayout = maxWidth >= 780.dp

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            IntroCard()

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        NotificationCard(
                            notificationsEnabled = notificationsEnabled,
                            remindersEnabled = remindersEnabled,
                            onNotificationsChanged = onNotificationsChanged,
                            onRemindersChanged = onRemindersChanged,
                        )
                        TutorialCard(
                            showTutorialOnLaunch = showTutorialOnLaunch,
                            onShowTutorialOnLaunchChanged = onShowTutorialOnLaunchChanged,
                            onOpenTutorial = onOpenTutorial,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        SupportCard()
                        PurchaseCard()
                        PrivacyCard()
                    }
                }
            } else {
                NotificationCard(
                    notificationsEnabled = notificationsEnabled,
                    remindersEnabled = remindersEnabled,
                    onNotificationsChanged = onNotificationsChanged,
                    onRemindersChanged = onRemindersChanged,
                )
                TutorialCard(
                    showTutorialOnLaunch = showTutorialOnLaunch,
                    onShowTutorialOnLaunchChanged = onShowTutorialOnLaunchChanged,
                    onOpenTutorial = onOpenTutorial,
                )
                SupportCard()
                PurchaseCard()
                PrivacyCard()
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = CircleShape,
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Local controls",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Choose how NullByte behaves on this device. Everything here is optional, private, and easy to change.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notificationsEnabled: Boolean,
    remindersEnabled: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Notifications and reminders",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            SwitchRow(
                title = "Result notifications",
                body = "Show a local message after NullByte finishes exporting clean copies.",
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsChanged,
            )

            SwitchRow(
                title = "Daily reminder",
                body = "Send an optional local reminder to clean media before you share it. Turning this on also requires notifications.",
                checked = remindersEnabled,
                onCheckedChange = onRemindersChanged,
            )
        }
    }
}

@Composable
private fun TutorialCard(
    showTutorialOnLaunch: Boolean,
    onShowTutorialOnLaunchChanged: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Getting started",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            SwitchRow(
                title = "Show guide on launch",
                body = "Show the quick guide when NullByte opens.",
                checked = showTutorialOnLaunch,
                onCheckedChange = onShowTutorialOnLaunchChanged,
            )

            TextButton(
                onClick = onOpenTutorial,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open guide now")
            }
        }
    }
}

@Composable
private fun SupportCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Format support",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            SupportRow(name = "Photos and images", status = "Ready", note = "Creates clean copies locally.")
            SupportRow(name = "MP4 / MOV / 3GP video", status = "Ready", note = "Creates clean MP4 copies when the video can be read safely on this device.")
            SupportRow(name = "M4A audio", status = "Ready", note = "Creates clean audio copies locally.")
            SupportRow(name = "MP3 audio", status = "Ready", note = "Removes common MP3 title, artist, album, and similar tags without changing the audio.")
            SupportRow(name = "WAV audio", status = "Ready", note = "Removes common WAV info tags while keeping the audio.")
            SupportRow(name = "Before-and-after check", status = "Ready", note = "Shows whether common location, device, time, and author details were removed.")
        }
    }
}

@Composable
private fun PurchaseCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Purchase",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            PrivacyLine("NullByte is a one-time purchase utility.")
            PrivacyLine("There are no accounts, subscriptions, ads, analytics, or cloud processing services in this app.")
            PrivacyLine("Your app store handles the purchase. NullByte does not process payment details inside the app.")
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Privacy promise",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            PrivacyLine("NullByte declares no Internet permission.")
            PrivacyLine("Files are processed locally from the items you pick or share into NullByte.")
            PrivacyLine("Your original files stay untouched.")
            PrivacyLine("Notifications and reminders are optional and stay on-device.")
            PrivacyLine("NullByte does not silently monitor every file or download in the background.")
            PrivacyLine("Verified clean means the common location, device, time, and author details NullByte checks were not found after cleaning.")
            PrivacyLine("Some file formats can store unusual hidden details, so review sensitive files before sharing.")
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SupportRow(
    name: String,
    status: String,
    note: String,
) {
    val ready = status == "Ready"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            Box(
                modifier = Modifier
                    .background(
                        color = if (ready) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = status,
                    color = if (ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
    }
}
