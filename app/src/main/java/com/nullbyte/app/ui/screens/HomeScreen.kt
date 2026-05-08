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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    selectedBatchCount: Int,
    onPickMedia: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit,
    onReviewTutorial: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        val wideLayout = maxWidth >= 780.dp

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HeroCard()

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        StartCard(
                            selectedBatchCount = selectedBatchCount,
                            onPickMedia = onPickMedia,
                            onOpenReview = onOpenReview,
                            onOpenSettings = onOpenSettings,
                            onReviewTutorial = onReviewTutorial,
                        )
                        HowToUseCard()
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        SupportCard()
                        PromiseCard()
                    }
                }
            } else {
                StartCard(
                    selectedBatchCount = selectedBatchCount,
                    onPickMedia = onPickMedia,
                    onOpenReview = onOpenReview,
                    onOpenSettings = onOpenSettings,
                    onReviewTutorial = onReviewTutorial,
                )
                HowToUseCard()
                SupportCard()
                PromiseCard()
            }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
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
                    text = "Offline privacy utility",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "NullByte",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Clean share-ready photos, videos, and audio locally on your phone. NullByte creates fresh copies without common location, device, time, and author details.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }
    }
}

@Composable
private fun StartCard(
    selectedBatchCount: Int,
    onPickMedia: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit,
    onReviewTutorial: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Start here",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "Use the picker for a batch, or share media into NullByte from another app. The menu button in the top bar also takes you to your queue and settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )

            Button(
                onClick = onPickMedia,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pick media to clean")
            }

            if (selectedBatchCount > 0) {
                OutlinedButton(
                    onClick = onOpenReview,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open review queue ($selectedBatchCount)")
                }
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open settings")
            }

            TextButton(
                onClick = onReviewTutorial,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open the guide again")
            }

            QueueCard(selectedBatchCount = selectedBatchCount)
        }
    }
}

@Composable
private fun QueueCard(selectedBatchCount: Int) {
    val queueLine = if (selectedBatchCount == 0) {
        "No files are queued yet. NullByte is ready when you are."
    } else {
        "$selectedBatchCount item${if (selectedBatchCount == 1) " is" else "s are"} waiting in your review queue."
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Batch status",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = queueLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun HowToUseCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "How NullByte works",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            StepRow(number = "1", text = "Pick a batch of media or share it into NullByte from another app.")
            StepRow(number = "2", text = "Review what NullByte found and see which files are ready to clean.")
            StepRow(number = "3", text = "Tap clean. NullByte shows live progress while it works.")
            StepRow(number = "4", text = "Review the before-and-after check, then open or share the clean copies directly from NullByte.")
        }
    }
}

@Composable
private fun SupportCard() {
    FactCard(
        title = "Current format support",
        lines = listOf(
            "Photos and images are ready to clean.",
            "MP4, MOV, and 3GP videos are ready to clean.",
            "M4A, MP3, and WAV audio files are ready to clean.",
            "Before-and-after checks show whether common location, device, time, and author details were removed.",
        ),
    )
}

@Composable
private fun PromiseCard() {
    FactCard(
        title = "What NullByte does and does not do",
        lines = listOf(
            "Creates new clean copies instead of changing your originals.",
            "Works offline without an Internet permission.",
            "Lets you manage reminders and the guide in Settings.",
            "Does not silently watch every download or background file on the device.",
        ),
    )
}

@Composable
private fun FactCard(
    title: String,
    lines: List<String>,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            lines.forEach { line ->
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
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    number: String,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
    }
}
