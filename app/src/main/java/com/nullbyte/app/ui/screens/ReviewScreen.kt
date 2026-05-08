package com.nullbyte.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nullbyte.app.data.ExportFormat
import com.nullbyte.app.data.MediaInspection
import com.nullbyte.app.data.MediaKind
import com.nullbyte.app.data.MediaSanitizer
import com.nullbyte.app.data.MetadataSignal
import com.nullbyte.app.data.SanitizeSummary
import com.nullbyte.app.data.SanitizedItemResult
import com.nullbyte.app.data.VerificationStatus
import com.nullbyte.app.notifications.NotificationScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    selectedUris: List<Uri>,
    notificationsEnabled: Boolean,
    onPickAnotherBatch: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inspections by remember(selectedUris) { mutableStateOf<List<MediaInspection>>(emptyList()) }
    var exportFormat by rememberSaveable(selectedUris) { mutableStateOf(ExportFormat.AUTO) }
    var isInspecting by remember(selectedUris) { mutableStateOf(true) }
    var isSanitizing by remember(selectedUris) { mutableStateOf(false) }
    var sanitizeProgress by remember(selectedUris) { mutableStateOf<SanitizeProgress?>(null) }
    var summary by remember(selectedUris) { mutableStateOf<SanitizeSummary?>(null) }
    var errorMessage by remember(selectedUris) { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable(selectedUris) { mutableStateOf(ReviewTab.ALL) }

    LaunchedEffect(selectedUris) {
        exportFormat = ExportFormat.AUTO
        summary = null
        errorMessage = null
        isInspecting = true
        isSanitizing = false
        sanitizeProgress = null
        selectedTab = ReviewTab.ALL
        inspections = if (selectedUris.isNotEmpty()) {
            MediaSanitizer.inspect(context, selectedUris)
        } else {
            emptyList()
        }
        isInspecting = false
    }

    val cleanableCount = inspections.count { it.supportedForSanitize }
    val imageCount = inspections.count { it.kind == MediaKind.IMAGE }
    val unsupportedCount = inspections.count { !it.supportedForSanitize }
    val imageTabCount = inspections.count { it.kind == MediaKind.IMAGE }
    val videoTabCount = inspections.count { it.kind == MediaKind.VIDEO }
    val audioTabCount = inspections.count { it.kind == MediaKind.AUDIO }
    val availableTabs = ReviewTab.entries.filter { tab ->
        when (tab) {
            ReviewTab.ALL -> inspections.isNotEmpty()
            ReviewTab.IMAGES -> imageTabCount > 0
            ReviewTab.VIDEOS -> videoTabCount > 0
            ReviewTab.AUDIO -> audioTabCount > 0
        }
    }
    val filteredInspections = inspections.filter { inspection -> selectedTab.matches(inspection.kind) }
    val filteredProofResults = summary?.itemResults?.filter { result -> selectedTab.matches(result.original.kind) }.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 380.dp) 14.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(
                totalCount = selectedUris.size,
                cleanableCount = cleanableCount,
                isInspecting = isInspecting,
            )

            summary?.let {
                SummaryCard(
                    summary = it,
                    onOpenLatestOutput = { openLatestOutput(context, it) },
                    onShareOutputs = { shareOutputs(context, it) },
                )
            }
            errorMessage?.let { ErrorCard(message = it) }

            if (isInspecting) {
                InspectingCard()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        BatchOverviewCard(
                            totalCount = inspections.size,
                            cleanableCount = cleanableCount,
                            unsupportedCount = unsupportedCount,
                            imageCount = imageTabCount,
                            videoCount = videoTabCount,
                            audioCount = audioTabCount,
                        )
                    }

                    if (availableTabs.isNotEmpty()) {
                        item {
                            MediaTabCard(
                                tabs = availableTabs,
                                selectedTab = selectedTab,
                                imageCount = imageTabCount,
                                videoCount = videoTabCount,
                                audioCount = audioTabCount,
                                onTabSelected = { tab -> selectedTab = tab },
                            )
                        }
                    }

                    if (summary == null && imageCount > 0) {
                        item {
                            ExportCard(
                                exportFormat = exportFormat,
                                imageCount = imageCount,
                                onFormatSelected = { exportFormat = it },
                            )
                        }
                    }

                    if (summary != null) {
                        item {
                            ProofIntroCard()
                        }

                        if (filteredProofResults.isEmpty()) {
                            item {
                                FilterEmptyCard(selectedTab = selectedTab, hasProof = true)
                            }
                        } else {
                            items(filteredProofResults, key = { result -> result.original.uri.toString() }) { result ->
                                ProofCard(result = result)
                            }
                        }
                    } else if (inspections.isEmpty()) {
                        item {
                            EmptyReviewCard()
                        }
                    } else if (filteredInspections.isEmpty()) {
                        item {
                            FilterEmptyCard(selectedTab = selectedTab, hasProof = false)
                        }
                    } else {
                        items(filteredInspections, key = { inspection -> inspection.uri.toString() }) { inspection ->
                            InspectionCard(inspection = inspection)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        summary = null
                        errorMessage = null
                        isSanitizing = true

                        val result = runCatching {
                            MediaSanitizer.sanitize(context, inspections, exportFormat) { current, total, item ->
                                sanitizeProgress = SanitizeProgress(
                                    current = current,
                                    total = total,
                                    displayName = item.displayName,
                                )
                            }
                        }.getOrElse { error ->
                            errorMessage = error.message ?: "NullByte could not finish this cleaning run."
                            null
                        }

                        sanitizeProgress = null
                        isSanitizing = false

                        if (result != null) {
                            summary = result
                            if (notificationsEnabled) {
                                NotificationScheduler.showSanitizedNotification(context, result.savedCount)
                            }
                        }
                    }
                },
                enabled = cleanableCount > 0 && !isSanitizing && !isInspecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSanitizing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    val buttonText = if (cleanableCount > 0) {
                        "Clean $cleanableCount ready item${if (cleanableCount == 1) "" else "s"}"
                    } else {
                        "Nothing ready to clean"
                    }
                    Text(buttonText)
                }
            }

            OutlinedButton(
                onClick = onPickAnotherBatch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pick another batch")
            }
        }

        if (isSanitizing) {
            ProgressOverlay(progress = sanitizeProgress)
        }
    }
}

@Composable
private fun HeaderCard(
    totalCount: Int,
    cleanableCount: Int,
    isInspecting: Boolean,
) {
    val queueLine = when {
        isInspecting -> "NullByte is scanning this batch locally before export."
        totalCount == 0 -> "Pick a batch or share media into NullByte to start a review."
        cleanableCount == totalCount -> "Everything in this batch is ready to clean."
        cleanableCount == 0 -> "Nothing in this batch is ready to clean."
        else -> "$cleanableCount of $totalCount item${if (totalCount == 1) "" else "s"} are ready to clean."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Review queue",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
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
private fun InspectingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Scanning media locally",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "This check stays on your device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun BatchOverviewCard(
    totalCount: Int,
    cleanableCount: Int,
    unsupportedCount: Int,
    imageCount: Int,
    videoCount: Int,
    audioCount: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Batch summary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryPill("$totalCount selected")
                SummaryPill("$cleanableCount ready")
                if (imageCount > 0) SummaryPill("$imageCount images")
                if (videoCount > 0) SummaryPill("$videoCount videos")
                if (audioCount > 0) SummaryPill("$audioCount audio")
                if (unsupportedCount > 0) SummaryPill("$unsupportedCount not ready")
            }
        }
    }
}

@Composable
private fun MediaTabCard(
    tabs: List<ReviewTab>,
    selectedTab: ReviewTab,
    imageCount: Int,
    videoCount: Int,
    audioCount: Int,
    onTabSelected: (ReviewTab) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Media",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Jump straight to videos, images, or audio when the batch is mixed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tabs.forEach { tab ->
                    val count = when (tab) {
                        ReviewTab.ALL -> imageCount + videoCount + audioCount
                        ReviewTab.IMAGES -> imageCount
                        ReviewTab.VIDEOS -> videoCount
                        ReviewTab.AUDIO -> audioCount
                    }

                    TogglePill(
                        text = "${tab.label} ($count)",
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: SanitizeSummary,
    onOpenLatestOutput: () -> Unit,
    onShareOutputs: () -> Unit,
) {
    val verifiedCount = summary.itemResults.count { it.status == VerificationStatus.VERIFIED_CLEAN }
    val alreadyClearCount = summary.itemResults.count { it.status == VerificationStatus.ALREADY_CLEAR }
    val partialCount = summary.itemResults.count { it.status == VerificationStatus.PARTIALLY_CLEANED }
    val needsReviewCount = summary.itemResults.count { it.status == VerificationStatus.NEEDS_REVIEW }

    val summaryLine = buildString {
        if (summary.savedCount > 0) {
            append("${summary.savedCount} clean ")
            append(if (summary.savedCount == 1) "copy" else "copies")
            append(" saved")
            if (summary.destinationLabel.isNotBlank()) {
                append(" to ${summary.destinationLabel}")
            }
            append(".")
        } else {
            append("No clean copies were saved.")
        }

        if (verifiedCount > 0) {
            append(" $verifiedCount verified clean.")
        }
        if (alreadyClearCount > 0) {
            append(" $alreadyClearCount already clear.")
        }
        if (partialCount > 0) {
            append(" $partialCount partially cleaned.")
        }
        if (needsReviewCount > 0) {
            append(" $needsReviewCount need review.")
        }
        if (summary.skippedCount > 0) {
            append(" ${summary.skippedCount} skipped.")
        }
        if (summary.failedCount > 0) {
            append(" ${summary.failedCount} failed.")
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Export complete",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summaryLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )

            if (summary.outputUris.isNotEmpty()) {
                Button(
                    onClick = onOpenLatestOutput,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open latest clean copy")
                }

                OutlinedButton(
                    onClick = onShareOutputs,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (summary.outputUris.size == 1) {
                            "Share clean copy"
                        } else {
                            "Share clean copies"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProofIntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Before and after",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Each result compares the original file to the clean copy so you can see what changed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Cleaning stopped",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ExportCard(
    exportFormat: ExportFormat,
    imageCount: Int,
    onFormatSelected: (ExportFormat) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Image export format",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This batch includes $imageCount image${if (imageCount == 1) "" else "s"}. Auto is the safest default and keeps PNG transparency when possible.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExportFormat.entries.forEach { format ->
                    TogglePill(
                        text = format.label,
                        selected = format == exportFormat,
                        onClick = { onFormatSelected(format) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectionCard(inspection: MediaInspection) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = inspection.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = inspection.kind.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SupportPill(
                    text = inspection.supportLabel,
                    active = inspection.supportedForSanitize,
                )
            }

            Text(
                text = "${inspection.tagCount} common detail${if (inspection.tagCount == 1) "" else "s"} found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlagPill("Location", inspection.gpsFound)
                FlagPill("Device", inspection.deviceFound)
                FlagPill("Time", inspection.timeFound)
                FlagPill("Author", inspection.authorFound)
            }

            Text(
                text = inspection.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ProofCard(result: SanitizedItemResult) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = result.original.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = result.original.kind.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                VerificationPill(status = result.status)
            }

            result.destinationLabel?.let { label ->
                Text(
                    text = "Saved to $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BeforeAfterSection(
                title = "Before",
                inspection = result.original,
            )

            if (result.outputInspection != null) {
                BeforeAfterSection(
                    title = "After",
                    inspection = result.outputInspection,
                )
            } else if (result.outputUri == null) {
                Text(
                    text = "After: No clean copy was created for this item.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            } else {
                Text(
                    text = "After: NullByte could not check this output on the device. Review it before sharing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }

            if (result.outputUri != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            openUri(
                                context = context,
                                uri = result.outputUri,
                                mimeType = result.outputInspection?.mimeType ?: result.original.mimeType,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open clean copy")
                    }

                    OutlinedButton(
                        onClick = {
                            shareSingleOutput(
                                context = context,
                                uri = result.outputUri,
                                mimeType = result.outputInspection?.mimeType ?: result.original.mimeType,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share clean copy")
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                result.removedSignals.forEach { signal ->
                    ProofSignalPill(text = "${signal.label} removed", tone = ProofTone.POSITIVE)
                }
                result.remainingSignals.forEach { signal ->
                    ProofSignalPill(text = "${signal.label} still present", tone = ProofTone.CAUTION)
                }
                result.newSignals.forEach { signal ->
                    ProofSignalPill(text = "${signal.label} in output", tone = ProofTone.REVIEW)
                }
            }

            Text(
                text = result.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun BeforeAfterSection(
    title: String,
    inspection: MediaInspection,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${inspection.tagCount} common detail${if (inspection.tagCount == 1) "" else "s"} found",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlagPill("Location", inspection.gpsFound)
            FlagPill("Device", inspection.deviceFound)
            FlagPill("Time", inspection.timeFound)
            FlagPill("Author", inspection.authorFound)
        }
    }
}

@Composable
private fun EmptyReviewCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "No media selected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pick a batch from the system picker or share media into NullByte from another app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun FilterEmptyCard(
    selectedTab: ReviewTab,
    hasProof: Boolean,
) {
    val body = if (hasProof) {
        "There are no ${selectedTab.label.lowercase()} results in this batch."
    } else {
        "There are no ${selectedTab.label.lowercase()} items in this batch."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = selectedTab.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ProgressOverlay(progress: SanitizeProgress?) {
    val primaryLine = if (progress != null) {
        "Cleaning ${progress.current} of ${progress.total}"
    } else {
        "Cleaning media"
    }

    val secondaryLine = if (progress != null) {
        progress.displayName
    } else {
        "NullByte is still working locally."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = primaryLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = secondaryLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
                Text(
                    text = "Large videos can take longer while NullByte creates the clean copy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun FlagPill(
    text: String,
    active: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Text(
            text = if (active) "$text found" else "$text clear",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SupportPill(
    text: String,
    active: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun VerificationPill(status: VerificationStatus) {
    val containerColor = when (status) {
        VerificationStatus.VERIFIED_CLEAN,
        VerificationStatus.ALREADY_CLEAR,
        -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

        VerificationStatus.PARTIALLY_CLEANED ->
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)

        VerificationStatus.NEEDS_REVIEW,
        VerificationStatus.SKIPPED,
        -> MaterialTheme.colorScheme.surfaceVariant

        VerificationStatus.FAILED ->
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
    }

    val textColor = when (status) {
        VerificationStatus.VERIFIED_CLEAN,
        VerificationStatus.ALREADY_CLEAR,
        -> MaterialTheme.colorScheme.primary

        VerificationStatus.PARTIALLY_CLEANED ->
            MaterialTheme.colorScheme.onBackground

        VerificationStatus.NEEDS_REVIEW,
        VerificationStatus.SKIPPED,
        -> MaterialTheme.colorScheme.onSurfaceVariant

        VerificationStatus.FAILED ->
            MaterialTheme.colorScheme.error
    }

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProofSignalPill(
    text: String,
    tone: ProofTone,
) {
    val containerColor = when (tone) {
        ProofTone.POSITIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ProofTone.CAUTION -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
        ProofTone.REVIEW -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (tone) {
        ProofTone.POSITIVE -> MaterialTheme.colorScheme.primary
        ProofTone.CAUTION -> MaterialTheme.colorScheme.onBackground
        ProofTone.REVIEW -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SummaryPill(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TogglePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private enum class ReviewTab(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    IMAGES("Images"),
    AUDIO("Audio");

    fun matches(kind: MediaKind): Boolean {
        return when (this) {
            ALL -> true
            IMAGES -> kind == MediaKind.IMAGE
            VIDEOS -> kind == MediaKind.VIDEO
            AUDIO -> kind == MediaKind.AUDIO
        }
    }
}

private enum class ProofTone {
    POSITIVE,
    CAUTION,
    REVIEW,
}

private data class SanitizeProgress(
    val current: Int,
    val total: Int,
    val displayName: String,
)

private fun openLatestOutput(
    context: Context,
    summary: SanitizeSummary,
) {
    val latest = summary.outputUris.lastOrNull() ?: return
    val latestMimeType = summary.itemResults.lastOrNull { it.outputUri == latest }?.outputInspection?.mimeType
        ?: context.contentResolver.getType(latest)
        ?: "*/*"
    openUri(context, latest, latestMimeType)
}

private fun shareOutputs(
    context: Context,
    summary: SanitizeSummary,
) {
    val uris = ArrayList(summary.outputUris)
    if (uris.isEmpty()) return

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uris.first()) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    launchChooser(context, intent, if (uris.size == 1) "Share clean copy" else "Share clean copies")
}

private fun shareSingleOutput(
    context: Context,
    uri: Uri,
    mimeType: String,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    launchChooser(context, intent, "Share clean copy")
}

private fun openUri(
    context: Context,
    uri: Uri,
    mimeType: String,
) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app was found to open this clean copy.", Toast.LENGTH_SHORT).show()
    }
}

private fun launchChooser(
    context: Context,
    intent: Intent,
    title: String,
) {
    try {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app was found for this action.", Toast.LENGTH_SHORT).show()
    }
}
