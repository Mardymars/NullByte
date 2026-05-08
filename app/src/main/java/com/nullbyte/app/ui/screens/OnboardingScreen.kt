package com.nullbyte.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    canClose: Boolean,
    onClose: () -> Unit,
    onFinish: () -> Unit,
) {
    val pages = remember { onboardingPages() }
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (canClose) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) {
                    Text("Back to app")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(30.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
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
                    text = "Guide ${pageIndex + 1} of ${pages.size}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                )

                page.points.forEach { point ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            text = point,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (index == pageIndex) 22.dp else 8.dp, 8.dp)
                        .background(
                            color = if (index == pageIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (pageIndex < pages.lastIndex) {
            Button(
                onClick = { pageIndex += 1 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
        } else {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start using NullByte")
            }
        }

        if (pageIndex > 0) {
            TextButton(
                onClick = { pageIndex -= 1 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Previous")
            }
        }
    }
}

private fun onboardingPages(): List<OnboardingPage> {
    return listOf(
        OnboardingPage(
            title = "What NullByte does",
            body = "NullByte creates a fresh share-ready copy of your photo, video, or audio file without common details that can reveal more than you intended.",
            points = listOf(
                "It focuses on details that commonly travel with media, like location, device model, time, and author tags.",
                "It works locally on your phone or tablet without uploading the file to a server.",
                "After cleaning, it compares the original file to the clean copy so you can review what changed.",
            ),
        ),
        OnboardingPage(
            title = "What NullByte does not do",
            body = "This app is built to be clear about its limits so people can trust it.",
            points = listOf(
                "NullByte does not overwrite or delete your original files.",
                "NullByte does not monitor every file on your device behind the scenes.",
                "NullByte does not claim that every format exposes metadata in the same way.",
                "NullByte checks for common location, device, time, and author details.",
            ),
        ),
        OnboardingPage(
            title = "How to use it",
            body = "The core flow is short on purpose.",
            points = listOf(
                "Pick a batch of media inside NullByte, or share a file into NullByte from another app.",
                "Review the file summary and choose an image export format if your batch includes images.",
                "Tap clean, then review the result before opening or sharing the clean copy.",
            ),
        ),
        OnboardingPage(
            title = "Optional reminders",
            body = "If you want a nudge before posting sensitive files, you can enable local reminders.",
            points = listOf(
                "Use the Home or Settings screens to turn notifications and reminders on or off.",
                "The notifications are optional and stay on-device.",
                "They open NullByte or the guide so you can choose what to do next.",
            ),
        ),
    )
}

private data class OnboardingPage(
    val title: String,
    val body: String,
    val points: List<String>,
)
