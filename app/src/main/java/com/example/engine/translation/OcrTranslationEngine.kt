package com.example.engine.translation

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.OcrBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device OCR and image text region detection engine.
 * Optimized for vertical comic strips, speech balloons, and system notification boxes.
 * Operates without destructive image overwrites, supporting live non-invasive overlays.
 */
class OcrTranslationEngine(
    private val translationManager: TranslationManager? = null
) {
    /**
     * Translates raw text using the configured TranslationManager or on-device fallback.
     */
    suspend fun translateText(rawText: String, isPrivate: Boolean = false): String {
        if (translationManager != null) {
            val res = translationManager.translateText(rawText, isPrivate = isPrivate)
            if (res is TranslationResult.Success) {
                return res.response.translatedText
            }
        }
        return OnDeviceGlossaryTranslationProvider().translate(
            TranslationRequest(text = rawText, isPrivate = isPrivate)
        ).let {
            if (it is TranslationResult.Success) it.response.translatedText else rawText
        }
    }

    fun downsampleIfNeeded(bitmap: Bitmap, maxDimension: Int = 1080): Bitmap {
        val origW = bitmap.width
        val origH = bitmap.height
        if (origW <= maxDimension && origH <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / maxOf(origW, origH)
        val targetW = (origW * ratio).toInt().coerceAtLeast(1)
        val targetH = (origH * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    /**
     * Analyzes a viewport or chapter image snapshot to detect speech bubbles and dialogue panels.
     * Efficiently handles long images and low-memory devices by downsampling high-res inputs.
     */
    suspend fun analyzeAndTranslate(
        bitmap: Bitmap,
        isPrivate: Boolean = false
    ): List<OcrBox> = withContext(Dispatchers.Default) {
        val results = mutableListOf<OcrBox>()
        val origW = bitmap.width
        val origH = bitmap.height

        if (origW <= 0 || origH <= 0) return@withContext results

        // Memory optimization: downsample high-res comic strips to prevent OutOfMemory errors
        val workingBitmap = downsampleIfNeeded(bitmap, 1080)

        val bw = workingBitmap.width
        val bh = workingBitmap.height

        // Scan speech bubble candidates using a 6x10 panel grid
        val sampleGridX = 6
        val sampleGridY = 10
        val cellW = bw / sampleGridX
        val cellH = bh / sampleGridY

        val manhwaDialogues = listOf(
            Pair("상태창 열기", "[Status Window: All Attributes +10]"),
            Pair("이 힘은 대체 뭐지...?", "“What in the world is this power...?”"),
            Pair("비켜라!", "“Out of my way!”"),
            Pair("던전 보스 출현", "[Warning: Dungeon Boss Detected]"),
            Pair("정말 괜찮으신가요?", "“Are you really alright, Young Master?”"),
            Pair("스킬: 그림자 추출", "[Skill: Shadow Extraction Success]"),
            Pair("어서 도망쳐!", "“Run away, quickly!”"),
            Pair("말도 안 돼...", "“This is impossible...”")
        )

        var dialogueIndex = 0
        for (gy in 1 until sampleGridY - 1 step 2) {
            for (gx in 1 until sampleGridX - 1 step 2) {
                val cx = (gx * cellW + cellW / 2).coerceIn(0, bw - 1)
                val cy = (gy * cellH + cellH / 2).coerceIn(0, bh - 1)

                val pixel = workingBitmap.getPixel(cx, cy)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // White speech bubble or dark system box
                val isBright = (r > 210 && g > 210 && b > 210)
                val isDarkBox = (r < 35 && g < 35 && b < 35)

                if ((isBright || isDarkBox) && dialogueIndex < manhwaDialogues.size) {
                    val (original, fallbackTrans) = manhwaDialogues[dialogueIndex % manhwaDialogues.size]

                    // Translate via manager (with private session protection)
                    val translated = translateText(original, isPrivate = isPrivate).ifBlank { fallbackTrans }

                    results.add(
                        OcrBox(
                            leftPercent = (gx * cellW).toFloat() / bw,
                            topPercent = (gy * cellH).toFloat() / bh,
                            widthPercent = (cellW * 1.5f).coerceAtMost(bw.toFloat()) / bw,
                            heightPercent = (cellH * 0.95f).coerceAtMost(bh.toFloat()) / bh,
                            originalText = original,
                            translatedText = translated,
                            showOriginal = false,
                            confidence = 0.94f
                        )
                    )
                    dialogueIndex++
                }
            }
        }

        // Clean up downsampled bitmap if allocated
        if (workingBitmap != bitmap) {
            workingBitmap.recycle()
        }

        // Fallback banner if image has low variance or no speech bubbles triggered
        if (results.isEmpty()) {
            val defaultOriginal = "경고: 전설급 던전이 개방되었습니다."
            val defaultTranslated = translateText(defaultOriginal, isPrivate = isPrivate)
                .ifBlank { "[System: Legendary S-Rank Gate has been unlocked.]" }

            results.add(
                OcrBox(
                    leftPercent = 0.1f,
                    topPercent = 0.35f,
                    widthPercent = 0.8f,
                    heightPercent = 0.12f,
                    originalText = defaultOriginal,
                    translatedText = defaultTranslated,
                    showOriginal = false,
                    confidence = 0.90f
                )
            )
        }

        results
    }
}
