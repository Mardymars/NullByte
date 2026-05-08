package com.nullbyte.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class ExportFormat(val label: String) {
    AUTO("Auto"),
    JPEG("JPG"),
    PNG("PNG"),
    WEBP("WebP"),
}

enum class MediaKind(val label: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    FILE("File"),
}

enum class MetadataSignal(val label: String) {
    GPS("Location"),
    DEVICE("Device"),
    TIME("Time"),
    AUTHOR("Author"),
}

enum class VerificationStatus(val label: String) {
    VERIFIED_CLEAN("Verified clean"),
    PARTIALLY_CLEANED("Partially cleaned"),
    ALREADY_CLEAR("Already clear"),
    NEEDS_REVIEW("Needs review"),
    SKIPPED("Skipped"),
    FAILED("Failed"),
}

data class MediaInspection(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val kind: MediaKind,
    val gpsFound: Boolean,
    val deviceFound: Boolean,
    val timeFound: Boolean,
    val authorFound: Boolean,
    val tagCount: Int,
    val note: String,
    val supportedForSanitize: Boolean,
    val supportLabel: String,
)

data class SanitizedItemResult(
    val original: MediaInspection,
    val outputUri: Uri?,
    val outputInspection: MediaInspection?,
    val destinationLabel: String?,
    val status: VerificationStatus,
    val removedSignals: List<MetadataSignal>,
    val remainingSignals: List<MetadataSignal>,
    val newSignals: List<MetadataSignal>,
    val detail: String,
)

data class SanitizeSummary(
    val outputUris: List<Uri>,
    val savedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val destinationLabel: String,
    val itemResults: List<SanitizedItemResult>,
)

object MediaSanitizer {
    private const val DEFAULT_BUFFER_SIZE = 1_048_576
    private const val MP3_ID3V1_SIZE = 128
    private const val WAV_HEADER_SIZE = 12
    private const val WAV_CHUNK_HEADER_SIZE = 8

    private val gpsTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
    )

    private val deviceTags = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
    )

    private val timeTags = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    )

    private val authorTags = listOf(
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
    )

    suspend fun inspect(context: Context, uris: List<Uri>): List<MediaInspection> = withContext(Dispatchers.IO) {
        uris.distinct().map { uri -> inspectSingle(context, uri) }
    }

    suspend fun sanitize(
        context: Context,
        media: List<MediaInspection>,
        exportFormat: ExportFormat,
        onProgress: suspend (current: Int, total: Int, item: MediaInspection) -> Unit = { _, _, _ -> },
    ): SanitizeSummary = withContext(Dispatchers.IO) {
        val outputUris = mutableListOf<Uri>()
        val destinations = linkedSetOf<String>()
        val itemResults = mutableListOf<SanitizedItemResult>()
        var skippedCount = 0
        var failedCount = 0
        var supportedIndex = 0
        val totalSupported = media.count { it.supportedForSanitize }

        media.forEach { item ->
            if (!item.supportedForSanitize) {
                skippedCount += 1
                itemResults += skippedResult(item)
                return@forEach
            }

            supportedIndex += 1
            withContext(Dispatchers.Main) {
                onProgress(supportedIndex, totalSupported, item)
            }

            val sanitized = runCatching {
                sanitizeSingle(context, item, exportFormat, supportedIndex)
            }.getOrElse { error ->
                failedCount += 1
                itemResults += failedResult(item, error.message ?: "NullByte could not create a clean copy for this file.")
                null
            }

            if (sanitized != null) {
                outputUris += sanitized.uri
                destinations += sanitized.destinationLabel
                itemResults += verifyResult(context, item, sanitized)
            }
        }

        SanitizeSummary(
            outputUris = outputUris,
            savedCount = outputUris.size,
            skippedCount = skippedCount,
            failedCount = failedCount,
            destinationLabel = destinations.joinToString(),
            itemResults = itemResults,
        )
    }

    private fun inspectSingle(context: Context, uri: Uri): MediaInspection {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: "Imported media"
        val mimeType = resolver.getType(uri) ?: inferMimeType(displayName)
        val kind = kindFromMimeType(mimeType)

        return when (kind) {
            MediaKind.IMAGE -> inspectImage(context, uri, displayName, mimeType)
            MediaKind.VIDEO,
            MediaKind.AUDIO,
            MediaKind.FILE,
            -> inspectTimedMedia(context, uri, displayName, mimeType, kind)
        }
    }

    private fun inspectImage(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
    ): MediaInspection {
        val resolver = context.contentResolver

        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val gpsCount = countExifTags(exif, gpsTags)
                val deviceCount = countExifTags(exif, deviceTags)
                val timeCount = countExifTags(exif, timeTags)
                val authorCount = countExifTags(exif, authorTags)
                val total = gpsCount + deviceCount + timeCount + authorCount

                MediaInspection(
                    uri = uri,
                    displayName = displayName,
                    mimeType = mimeType,
                    kind = MediaKind.IMAGE,
                    gpsFound = gpsCount > 0,
                    deviceFound = deviceCount > 0,
                    timeFound = timeCount > 0,
                    authorFound = authorCount > 0,
                    tagCount = total,
                    note = if (total > 0) {
                        "Common share-sensitive image metadata detected."
                    } else {
                        "No common EXIF tags were detected in this image."
                    },
                    supportedForSanitize = true,
                    supportLabel = "Ready",
                )
            } ?: fallbackImageInspection(uri, displayName, mimeType)
        }.getOrElse {
            fallbackImageInspection(uri, displayName, mimeType)
        }
    }

    private fun fallbackImageInspection(
        uri: Uri,
        displayName: String,
        mimeType: String,
    ): MediaInspection {
        return MediaInspection(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            kind = MediaKind.IMAGE,
            gpsFound = false,
            deviceFound = false,
            timeFound = false,
            authorFound = false,
            tagCount = 0,
            note = "NullByte can still create a fresh clean image copy.",
            supportedForSanitize = true,
            supportLabel = "Ready",
        )
    }

    private fun inspectTimedMedia(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
        kind: MediaKind,
    ): MediaInspection {
        val retriever = MediaMetadataRetriever()

        return runCatching {
            retriever.setDataSource(context, uri)

            val gpsFound = !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION).isNullOrBlank()
            val timeFound = !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE).isNullOrBlank() ||
                !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR).isNullOrBlank()
            val authorFound = !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR).isNullOrBlank() ||
                !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).isNullOrBlank() ||
                !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).isNullOrBlank() ||
                !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER).isNullOrBlank()

            val support = supportDescriptor(kind, mimeType, displayName)
            val total = listOf(gpsFound, timeFound, authorFound).count { it }

            MediaInspection(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                kind = kind,
                gpsFound = gpsFound,
                deviceFound = false,
                timeFound = timeFound,
                authorFound = authorFound,
                tagCount = total,
                note = support.note,
                supportedForSanitize = support.supported,
                supportLabel = support.label,
            )
        }.getOrElse {
            val support = supportDescriptor(kind, mimeType, displayName)
            MediaInspection(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                kind = kind,
                gpsFound = false,
                deviceFound = false,
                timeFound = false,
                authorFound = false,
                tagCount = 0,
                note = support.note,
                supportedForSanitize = support.supported,
                supportLabel = support.label,
            )
        }.also {
            retriever.release()
        }
    }

    private fun sanitizeSingle(
        context: Context,
        item: MediaInspection,
        exportFormat: ExportFormat,
        index: Int,
    ): SanitizedOutput {
        return when (item.kind) {
            MediaKind.IMAGE -> sanitizeImage(context, item, exportFormat, index)
            MediaKind.VIDEO -> remuxMedia(context, item, index, isVideo = true)
            MediaKind.AUDIO -> sanitizeAudio(context, item, index)
            MediaKind.FILE -> error("This file type is not ready to clean in NullByte.")
        }
    }

    private fun sanitizeImage(
        context: Context,
        item: MediaInspection,
        exportFormat: ExportFormat,
        index: Int,
    ): SanitizedOutput {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(item.uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val sourceBitmap = resolver.openInputStream(item.uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("Unable to decode source image.")

        val rotatedBitmap = applyExifOrientation(sourceBitmap, orientation)
        val resolvedFormat = exportFormat.resolve(item.mimeType, rotatedBitmap)
        val displayName = buildTimestampedName(index, resolvedFormat.extension)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, resolvedFormat.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NullByte")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create destination image.")

        try {
            resolver.openOutputStream(outputUri)?.use { output ->
                check(rotatedBitmap.compress(resolvedFormat.compressFormat, resolvedFormat.quality, output)) {
                    "Unable to write clean image."
                }
            } ?: error("Unable to open destination output stream.")

            resolver.update(
                outputUri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        } finally {
            if (rotatedBitmap !== sourceBitmap) {
                rotatedBitmap.recycle()
            }
            sourceBitmap.recycle()
        }

        return SanitizedOutput(outputUri, "Pictures/NullByte")
    }

    private fun sanitizeAudio(
        context: Context,
        item: MediaInspection,
        index: Int,
    ): SanitizedOutput {
        val extension = extensionOf(item.displayName)
        return when {
            isM4aLikeAudio(item.mimeType, extension) -> remuxMedia(context, item, index, isVideo = false)
            isMp3Audio(item.mimeType, extension) -> stripAudioFile(
                context = context,
                item = item,
                index = index,
                output = rawAudioOutputDescriptor("audio/mpeg", ".mp3"),
                writer = ::writeMp3WithoutId3Tags,
            )

            isWavAudio(item.mimeType, extension) -> stripAudioFile(
                context = context,
                item = item,
                index = index,
                output = rawAudioOutputDescriptor("audio/wav", ".wav"),
                writer = ::writeWavWithoutMetadataChunks,
            )

            else -> error("This audio format is not ready to clean in NullByte.")
        }
    }

    private fun stripAudioFile(
        context: Context,
        item: MediaInspection,
        index: Int,
        output: RemuxOutput,
        writer: (ByteArray, OutputStream) -> Unit,
    ): SanitizedOutput {
        val resolver = context.contentResolver
        val sourceBytes = resolver.openInputStream(item.uri)?.use { input -> input.readBytes() }
            ?: error("Unable to read source audio.")
        val tempFile = File.createTempFile("nullbyte_audio_", output.extension, context.cacheDir)
        val displayName = buildTimestampedName(index, output.extension)
        val values = ContentValues().apply {
            put(output.displayColumn, displayName)
            put(output.mimeColumn, output.mimeType)
            put(output.relativePathColumn, output.relativePath)
            put(output.pendingColumn, 1)
        }

        try {
            tempFile.outputStream().use { tempOutput ->
                writer(sourceBytes, tempOutput)
            }

            check(tempFile.length() > 0L) {
                "NullByte could not create a cleaned audio copy."
            }

            val outputUri = resolver.insert(output.collectionUri, values)
                ?: error("Unable to create destination audio file.")

            try {
                resolver.openOutputStream(outputUri)?.use { mediaOutput ->
                    tempFile.inputStream().use { tempInput ->
                        tempInput.copyTo(mediaOutput, DEFAULT_BUFFER_SIZE)
                    }
                } ?: error("Unable to open destination audio output stream.")

                resolver.update(
                    outputUri,
                    ContentValues().apply { put(output.pendingColumn, 0) },
                    null,
                    null,
                )
            } catch (error: Throwable) {
                resolver.delete(outputUri, null, null)
                throw error
            }

            return SanitizedOutput(outputUri, output.relativePath)
        } finally {
            tempFile.delete()
        }
    }

    private fun remuxMedia(
        context: Context,
        item: MediaInspection,
        index: Int,
        isVideo: Boolean,
    ): SanitizedOutput {
        val resolver = context.contentResolver
        val output = remuxOutputDescriptor(isVideo)
        val displayName = buildTimestampedName(index, output.extension)
        val values = ContentValues().apply {
            put(output.displayColumn, displayName)
            put(output.mimeColumn, output.mimeType)
            put(output.relativePathColumn, output.relativePath)
            put(output.pendingColumn, 1)
        }

        val outputUri = resolver.insert(output.collectionUri, values)
            ?: error("Unable to create destination media file.")

        try {
            resolver.openFileDescriptor(outputUri, "rw")?.use { parcelFileDescriptor ->
                val extractor = MediaExtractor()
                val muxer = MediaMuxer(parcelFileDescriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                runCatching {
                    extractor.setDataSource(context, item.uri, emptyMap())
                    val trackSelection = configureTracks(item, extractor, muxer, isVideo)

                    if (isVideo) {
                        orientationHint(item, context)?.let { rotation ->
                            muxer.setOrientationHint(rotation)
                        }
                    }

                    muxer.start()
                    copySelectedTracks(extractor, muxer, trackSelection)
                }.also {
                    extractor.release()
                    runCatching { muxer.stop() }
                    muxer.release()
                }.getOrThrow()
            } ?: error("Unable to open destination media file.")

            resolver.update(
                outputUri,
                ContentValues().apply { put(output.pendingColumn, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        }

        return SanitizedOutput(outputUri, output.relativePath)
    }

    private fun configureTracks(
        item: MediaInspection,
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        isVideo: Boolean,
    ): TrackSelection {
        var selectedTrackCount = 0
        var maxBufferSize = DEFAULT_BUFFER_SIZE
        val destinationTracks = mutableMapOf<Int, Int>()

        repeat(extractor.trackCount) { trackIndex ->
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val includeTrack = if (isVideo) {
                mime.startsWith("video/") || mime.startsWith("audio/")
            } else {
                mime.startsWith("audio/")
            }

            if (includeTrack) {
                destinationTracks[trackIndex] = muxer.addTrack(format)
                extractor.selectTrack(trackIndex)
                selectedTrackCount += 1
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxBufferSize = max(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
        }

        check(selectedTrackCount > 0) {
            "No compatible ${item.kind.label.lowercase(Locale.US)} tracks were found."
        }

        return TrackSelection(
            destinationTracks = destinationTracks,
            bufferSize = maxBufferSize,
        )
    }

    private fun copySelectedTracks(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackSelection: TrackSelection,
    ) {
        val buffer = ByteBuffer.allocate(trackSelection.bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            buffer.clear()
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) {
                bufferInfo.size = 0
                break
            }

            val sourceTrackIndex = extractor.sampleTrackIndex
            val destinationTrackIndex = trackSelection.destinationTracks[sourceTrackIndex]
                ?: error("Missing destination track for source index $sourceTrackIndex.")
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(destinationTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }

    private fun orientationHint(item: MediaInspection, context: Context): Int? {
        if (item.kind != MediaKind.VIDEO) return null

        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, item.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        }.getOrNull().also {
            retriever.release()
        }
    }

    private fun supportDescriptor(
        kind: MediaKind,
        mimeType: String,
        displayName: String,
    ): SupportDescriptor {
        if (kind == MediaKind.IMAGE) {
            return SupportDescriptor(
                supported = true,
                label = "Ready",
                note = "NullByte can create a clean image copy locally on this device.",
            )
        }

        val extension = extensionOf(displayName)
        return when {
            kind == MediaKind.VIDEO && isMp4LikeVideo(mimeType, extension) -> SupportDescriptor(
                supported = true,
                label = "Ready",
                note = "NullByte can create a clean MP4 copy locally. Large videos can take longer, and the app shows progress while it works.",
            )

            kind == MediaKind.AUDIO && isM4aLikeAudio(mimeType, extension) -> SupportDescriptor(
                supported = true,
                label = "Ready",
                note = "NullByte can create a clean M4A audio copy locally.",
            )

            kind == MediaKind.AUDIO && isMp3Audio(mimeType, extension) -> SupportDescriptor(
                supported = true,
                label = "Ready",
                note = "NullByte can create a clean MP3 copy locally without changing the audio.",
            )

            kind == MediaKind.AUDIO && isWavAudio(mimeType, extension) -> SupportDescriptor(
                supported = true,
                label = "Ready",
                note = "NullByte can create a clean WAV copy locally without changing the audio.",
            )

            kind == MediaKind.VIDEO -> SupportDescriptor(
                supported = false,
                label = "Not ready",
                note = "This video format is not ready to clean. NullByte supports MP4, MOV, and 3GP video.",
            )

            kind == MediaKind.AUDIO -> SupportDescriptor(
                supported = false,
                label = "Not ready",
                note = "This audio format is not ready to clean. NullByte supports M4A, MP3, and WAV audio.",
            )

            else -> SupportDescriptor(
                supported = false,
                label = "Not ready",
                note = "This file type is not ready to clean in NullByte.",
            )
        }
    }

    private fun verifyResult(
        context: Context,
        original: MediaInspection,
        sanitized: SanitizedOutput,
    ): SanitizedItemResult {
        val outputInspection = runCatching {
            inspectSingle(context, sanitized.uri)
        }.getOrNull()

        if (outputInspection == null) {
            return SanitizedItemResult(
                original = original,
                outputUri = sanitized.uri,
                outputInspection = null,
                destinationLabel = sanitized.destinationLabel,
                status = VerificationStatus.NEEDS_REVIEW,
                removedSignals = emptyList(),
                remainingSignals = detectedSignals(original),
                newSignals = emptyList(),
                detail = "NullByte saved a clean copy, but could not check the result on this device.",
            )
        }

        val beforeSignals = detectedSignals(original)
        val afterSignals = detectedSignals(outputInspection)
        val removedSignals = beforeSignals.filterNot { signal -> signal in afterSignals }
        val remainingSignals = beforeSignals.filter { signal -> signal in afterSignals }
        val newSignals = afterSignals.filterNot { signal -> signal in beforeSignals }

        val status = when {
            beforeSignals.isEmpty() && afterSignals.isEmpty() -> VerificationStatus.ALREADY_CLEAR
            beforeSignals.isNotEmpty() && remainingSignals.isEmpty() && newSignals.isEmpty() -> VerificationStatus.VERIFIED_CLEAN
            removedSignals.isNotEmpty() -> VerificationStatus.PARTIALLY_CLEANED
            else -> VerificationStatus.NEEDS_REVIEW
        }

        val detail = when (status) {
            VerificationStatus.ALREADY_CLEAR ->
                "NullByte did not detect common share-sensitive metadata before or after export."

            VerificationStatus.VERIFIED_CLEAN ->
                "NullByte verified that ${joinSignals(removedSignals)} no longer appear in the clean copy."

            VerificationStatus.PARTIALLY_CLEANED ->
                "NullByte removed ${joinSignals(removedSignals)}, but ${joinSignals(remainingSignals)} still appear in the clean copy."

            VerificationStatus.NEEDS_REVIEW -> when {
                beforeSignals.isEmpty() && afterSignals.isNotEmpty() ->
                    "The clean copy was saved, but NullByte still sees ${joinSignals(afterSignals)} in the output. Review this result before sharing."

                remainingSignals.isNotEmpty() && newSignals.isNotEmpty() ->
                    "NullByte still sees ${joinSignals(remainingSignals)} and also detected ${joinSignals(newSignals)} in the output. Review this result before sharing."

                remainingSignals.isNotEmpty() ->
                    "NullByte still sees ${joinSignals(remainingSignals)} in the output. Review this result before sharing."

                else ->
                    "The clean copy was saved, but the verification result is inconclusive. Review this output before sharing."
            }

            VerificationStatus.SKIPPED,
            VerificationStatus.FAILED,
            -> original.note
        }

        return SanitizedItemResult(
            original = original,
            outputUri = sanitized.uri,
            outputInspection = outputInspection,
            destinationLabel = sanitized.destinationLabel,
            status = status,
            removedSignals = removedSignals,
            remainingSignals = remainingSignals,
            newSignals = newSignals,
            detail = detail,
        )
    }

    private fun skippedResult(item: MediaInspection): SanitizedItemResult {
        return SanitizedItemResult(
            original = item,
            outputUri = null,
            outputInspection = null,
            destinationLabel = null,
            status = VerificationStatus.SKIPPED,
            removedSignals = emptyList(),
            remainingSignals = detectedSignals(item),
            newSignals = emptyList(),
            detail = item.note,
        )
    }

    private fun failedResult(
        item: MediaInspection,
        message: String,
    ): SanitizedItemResult {
        return SanitizedItemResult(
            original = item,
            outputUri = null,
            outputInspection = null,
            destinationLabel = null,
            status = VerificationStatus.FAILED,
            removedSignals = emptyList(),
            remainingSignals = detectedSignals(item),
            newSignals = emptyList(),
            detail = message,
        )
    }

    private fun detectedSignals(inspection: MediaInspection): List<MetadataSignal> {
        return buildList {
            if (inspection.gpsFound) add(MetadataSignal.GPS)
            if (inspection.deviceFound) add(MetadataSignal.DEVICE)
            if (inspection.timeFound) add(MetadataSignal.TIME)
            if (inspection.authorFound) add(MetadataSignal.AUTHOR)
        }
    }

    private fun joinSignals(signals: List<MetadataSignal>): String {
        return signals.joinToString { signal -> signal.label }
    }

    private fun buildTimestampedName(index: Int, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "NullByte_${timestamp}_$index$extension"
    }

    private fun ExportFormat.resolve(sourceMimeType: String, bitmap: Bitmap): ResolvedFormat {
        return when (this) {
            ExportFormat.AUTO -> {
                when {
                    bitmap.hasAlpha() || sourceMimeType.contains("png", ignoreCase = true) ->
                        ResolvedFormat(".png", "image/png", Bitmap.CompressFormat.PNG, 100)

                    sourceMimeType.contains("webp", ignoreCase = true) ->
                        ResolvedFormat(".webp", "image/webp", webpFormat(), 95)

                    else ->
                        ResolvedFormat(".jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 95)
                }
            }

            ExportFormat.JPEG -> ResolvedFormat(".jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 95)
            ExportFormat.PNG -> ResolvedFormat(".png", "image/png", Bitmap.CompressFormat.PNG, 100)
            ExportFormat.WEBP -> ResolvedFormat(".webp", "image/webp", webpFormat(), 95)
        }
    }

    private fun webpFormat(): Bitmap.CompressFormat {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    private fun countExifTags(exif: ExifInterface, tags: List<String>): Int {
        return tags.count { tag -> !exif.getAttribute(tag).isNullOrBlank() }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }

        return null
    }

    private fun inferMimeType(displayName: String): String {
        val extension = extensionOf(displayName)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun extensionOf(displayName: String): String {
        return displayName.substringAfterLast('.', "").lowercase(Locale.US)
    }

    private fun isMp4LikeVideo(mimeType: String, extension: String): Boolean {
        val normalizedMime = mimeType.lowercase(Locale.US)
        return normalizedMime in setOf("video/mp4", "video/quicktime", "video/3gpp") ||
            normalizedMime.contains("mp4") ||
            extension in setOf("mp4", "m4v", "mov", "3gp", "3gpp")
    }

    private fun isM4aLikeAudio(mimeType: String, extension: String): Boolean {
        val normalizedMime = mimeType.lowercase(Locale.US)
        return normalizedMime in setOf("audio/mp4", "audio/m4a", "audio/x-m4a") ||
            normalizedMime.contains("mp4") ||
            extension in setOf("m4a", "mp4")
    }

    private fun isMp3Audio(mimeType: String, extension: String): Boolean {
        val normalizedMime = mimeType.lowercase(Locale.US)
        return normalizedMime in setOf("audio/mpeg", "audio/mp3", "audio/x-mpeg", "audio/x-mp3") ||
            extension == "mp3"
    }

    private fun isWavAudio(mimeType: String, extension: String): Boolean {
        val normalizedMime = mimeType.lowercase(Locale.US)
        return normalizedMime in setOf("audio/wav", "audio/x-wav", "audio/wave") ||
            extension == "wav"
    }

    private fun writeMp3WithoutId3Tags(
        sourceBytes: ByteArray,
        output: OutputStream,
    ) {
        var start = 0
        var end = sourceBytes.size

        if (sourceBytes.size >= 10 &&
            sourceBytes[0] == 'I'.code.toByte() &&
            sourceBytes[1] == 'D'.code.toByte() &&
            sourceBytes[2] == '3'.code.toByte()
        ) {
            val tagSize = synchsafeInt(sourceBytes, 6)
            val hasFooter = sourceBytes[5].toInt() and 0x10 != 0
            start = (10 + tagSize + if (hasFooter) 10 else 0).coerceAtMost(sourceBytes.size)
        }

        if (end - start >= MP3_ID3V1_SIZE &&
            sourceBytes[end - MP3_ID3V1_SIZE] == 'T'.code.toByte() &&
            sourceBytes[end - MP3_ID3V1_SIZE + 1] == 'A'.code.toByte() &&
            sourceBytes[end - MP3_ID3V1_SIZE + 2] == 'G'.code.toByte()
        ) {
            end -= MP3_ID3V1_SIZE
        }

        check(end > start) {
            "NullByte removed metadata tags, but no MP3 audio frames were left to save."
        }

        output.write(sourceBytes, start, end - start)
    }

    private fun writeWavWithoutMetadataChunks(
        sourceBytes: ByteArray,
        output: OutputStream,
    ) {
        check(sourceBytes.size >= WAV_HEADER_SIZE &&
            sourceBytes[0] == 'R'.code.toByte() &&
            sourceBytes[1] == 'I'.code.toByte() &&
            sourceBytes[2] == 'F'.code.toByte() &&
            sourceBytes[3] == 'F'.code.toByte() &&
            sourceBytes[8] == 'W'.code.toByte() &&
            sourceBytes[9] == 'A'.code.toByte() &&
            sourceBytes[10] == 'V'.code.toByte() &&
            sourceBytes[11] == 'E'.code.toByte()
        ) {
            "This WAV file does not have a standard RIFF/WAVE structure."
        }

        val keptChunks = mutableListOf<WavChunk>()
        var offset = WAV_HEADER_SIZE
        while (offset + WAV_CHUNK_HEADER_SIZE <= sourceBytes.size) {
            val chunkId = sourceBytes.decodeAscii(offset, 4)
            val chunkSize = sourceBytes.readLittleEndianInt(offset + 4)
            val dataStart = offset + WAV_CHUNK_HEADER_SIZE
            val dataEnd = dataStart + chunkSize
            if (chunkSize < 0 || dataEnd > sourceBytes.size) break

            if (chunkId in setOf("fmt ", "data", "fact", "cue ", "smpl")) {
                keptChunks += WavChunk(
                    id = chunkId,
                    bytes = sourceBytes.copyOfRange(dataStart, dataEnd),
                )
            }

            offset = dataEnd + (chunkSize % 2)
        }

        check(keptChunks.any { it.id == "fmt " } && keptChunks.any { it.id == "data" }) {
            "This WAV file is missing required audio chunks."
        }

        val riffSize = 4 + keptChunks.sumOf { WAV_CHUNK_HEADER_SIZE + it.bytes.size + (it.bytes.size % 2) }
        output.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        output.writeLittleEndianInt(riffSize)
        output.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))

        keptChunks.forEach { chunk ->
            output.write(chunk.id.toByteArray(Charsets.US_ASCII))
            output.writeLittleEndianInt(chunk.bytes.size)
            output.write(chunk.bytes)
            if (chunk.bytes.size % 2 == 1) {
                output.write(0)
            }
        }
    }

    private fun synchsafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String {
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun ByteArray.readLittleEndianInt(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun OutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun kindFromMimeType(mimeType: String): MediaKind {
        return when {
            mimeType.startsWith("image/") -> MediaKind.IMAGE
            mimeType.startsWith("video/") -> MediaKind.VIDEO
            mimeType.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.FILE
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    preScale(-1f, 1f)
                    postRotate(90f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    preScale(-1f, 1f)
                    postRotate(270f)
                }
            }
        }

        if (matrix.isIdentity) return bitmap

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private data class ResolvedFormat(
        val extension: String,
        val mimeType: String,
        val compressFormat: Bitmap.CompressFormat,
        val quality: Int,
    )

    private data class SupportDescriptor(
        val supported: Boolean,
        val label: String,
        val note: String,
    )

    private data class SanitizedOutput(
        val uri: Uri,
        val destinationLabel: String,
    )

    private data class RemuxOutput(
        val collectionUri: Uri,
        val displayColumn: String,
        val mimeColumn: String,
        val relativePathColumn: String,
        val pendingColumn: String,
        val relativePath: String,
        val mimeType: String,
        val extension: String,
    )

    private data class TrackSelection(
        val destinationTracks: Map<Int, Int>,
        val bufferSize: Int,
    )

    private data class WavChunk(
        val id: String,
        val bytes: ByteArray,
    )

    private fun remuxOutputDescriptor(isVideo: Boolean): RemuxOutput {
        return if (isVideo) {
            RemuxOutput(
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                displayColumn = MediaStore.Video.Media.DISPLAY_NAME,
                mimeColumn = MediaStore.Video.Media.MIME_TYPE,
                relativePathColumn = MediaStore.Video.Media.RELATIVE_PATH,
                pendingColumn = MediaStore.Video.Media.IS_PENDING,
                relativePath = "Movies/NullByte",
                mimeType = "video/mp4",
                extension = ".mp4",
            )
        } else {
            RemuxOutput(
                collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                displayColumn = MediaStore.Audio.Media.DISPLAY_NAME,
                mimeColumn = MediaStore.Audio.Media.MIME_TYPE,
                relativePathColumn = MediaStore.Audio.Media.RELATIVE_PATH,
                pendingColumn = MediaStore.Audio.Media.IS_PENDING,
                relativePath = "Music/NullByte",
                mimeType = "audio/mp4",
                extension = ".m4a",
            )
        }
    }

    private fun rawAudioOutputDescriptor(
        mimeType: String,
        extension: String,
    ): RemuxOutput {
        return RemuxOutput(
            collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            displayColumn = MediaStore.Audio.Media.DISPLAY_NAME,
            mimeColumn = MediaStore.Audio.Media.MIME_TYPE,
            relativePathColumn = MediaStore.Audio.Media.RELATIVE_PATH,
            pendingColumn = MediaStore.Audio.Media.IS_PENDING,
            relativePath = "Music/NullByte",
            mimeType = mimeType,
            extension = extension,
        )
    }
}
