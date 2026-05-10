package eu.kanade.tachiyomi.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

data class TextBlock(
    val text: String,
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float,
    val isVertical: Boolean = false,
    val lineCount: Int = 1,
    val meetsThreshold: Boolean = true,
)

class PageTranslator {
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )

    /** Blocking — call from a background thread only. */
    fun prepare() {
        Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
    }

    /** Blocking — call from a background thread only. OCR only, no translation. */
    fun detectBlocks(bitmap: Bitmap): List<TextBlock> = detect(bitmap)

    /** Blocking — call from a background thread only. */
    fun translate(block: TextBlock): String {
        logcat { "translate: '${block.text.take(40)}'" }
        val translated = Tasks.await(translator.translate(block.text))
        logcat { "translate: → '${translated.take(40)}'" }
        return translated
    }

    /**
     * OCR → merge nearby fragments → threshold filter.
     * All returned blocks have [TextBlock.meetsThreshold] set; filtered-out blocks are still
     * included so bounding-box visualisation can show them in a different colour.
     */
    private fun detect(bitmap: Bitmap): List<TextBlock> {
        val scaled = bitmap.scaleToMax(MAX_OCR_DIM)
        val visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(scaled, 0)))

        val detections = visionText.textBlocks.mapIndexedNotNull { i, block ->
            val box = block.boundingBox ?: run {
                logcat(LogPriority.WARN) { "detect: block[$i] null box, skipping" }
                return@mapIndexedNotNull null
            }
            Detection(Rect(box), block.text, block.lines.size.coerceAtLeast(1))
        }
        if (detections.isEmpty()) return emptyList()

        // Vertical JP columns are taller than wide; horizontal lines are wider than tall.
        val dims = detections.map { it.box.width().toFloat() to it.box.height().toFloat() }
        val isVertical = dims.count { (w, h) -> h > w } > dims.size / 2

        val merged = mergeNearby(detections, isVertical)

        // Key dimension: column breadth for vertical, line height for horizontal.
        val keyDims = merged.map { if (isVertical) it.box.width().toFloat() else it.box.height().toFloat() }
        val median = keyDims.sorted()[keyDims.size / 2]
        val threshold = median * 0.5f

        return merged.mapIndexed { i, det ->
            TextBlock(
                text = det.text,
                leftFrac = det.box.left.toFloat() / scaled.width,
                topFrac = det.box.top.toFloat() / scaled.height,
                rightFrac = det.box.right.toFloat() / scaled.width,
                bottomFrac = det.box.bottom.toFloat() / scaled.height,
                isVertical = det.box.height() > det.box.width(),
                lineCount = det.lineCount,
                meetsThreshold = keyDims[i] >= threshold,
            )
        }
    }

    /**
     * Merge OCR fragments that belong to the same text column/paragraph.
     */
    private fun mergeNearby(detections: List<Detection>, isVertical: Boolean): List<Detection> {
        val n = detections.size
        if (n <= 1) return detections

        val keyDims = detections.map { if (isVertical) it.box.width().toFloat() else it.box.height().toFloat() }
        val median = keyDims.sorted()[n / 2]
        val alignTolerance = median * 0.5f  // blocks can be this far apart on the alignment axis
        val gapLimit = median * 2.0f        // max gap on the reading axis

        // Union-find with path compression.
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (c != r) { val t = parent[c]; parent[c] = r; c = t }
            return r
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val a = detections[i].box
                val b = detections[j].box
                val shouldMerge = if (isVertical) {
                    // Same column: x-separation within tolerance, y-gap within limit.
                    val xSep = (maxOf(a.left, b.left) - minOf(a.right, b.right)).toFloat()
                    val yGap = maxOf(0, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom)).toFloat()
                    xSep < alignTolerance && yGap <= gapLimit
                } else {
                    // Same paragraph: y-separation within tolerance, x-gap within limit.
                    val ySep = (maxOf(a.top, b.top) - minOf(a.bottom, b.bottom)).toFloat()
                    val xGap = maxOf(0, maxOf(a.left, b.left) - minOf(a.right, b.right)).toFloat()
                    ySep < alignTolerance && xGap <= gapLimit
                }
                if (shouldMerge) parent[find(i)] = find(j)
            }
        }

        return (0 until n).groupBy { find(it) }.values.map { group ->
            val boxes = group.map { detections[it].box }
            // Sort sub-blocks in reading order before joining text.
            val ordered = group.sortedBy { detections[it].box.top }
            Detection(
                box = Rect(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom }),
                text = ordered.joinToString("\n") { detections[it].text },
                lineCount = group.sumOf { detections[it].lineCount },
            )
        }
    }

    private data class Detection(val box: Rect, val text: String, val lineCount: Int)

    fun drawBoundingBoxes(bitmap: Bitmap, blocks: List<TextBlock>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        blocks.forEach { block ->
            canvas.drawRect(
                RectF(
                    block.leftFrac * result.width,
                    block.topFrac * result.height,
                    block.rightFrac * result.width,
                    block.bottomFrac * result.height,
                ),
                if (block.meetsThreshold) redPaint else bluePaint,
            )
        }
        return result
    }

    fun annotate(bitmap: Bitmap, blocks: List<TextBlock>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.FILL
        }
        blocks.filter { it.meetsThreshold }.forEach { block ->
            val translatedText = translate(block)
            val rect = RectF(
                block.leftFrac * result.width,
                block.topFrac * result.height,
                block.rightFrac * result.width,
                block.bottomFrac * result.height,
            )
            canvas.drawRect(rect, bgPaint)
            val lineHeight = if (block.isVertical) rect.width() / block.lineCount else rect.height() / block.lineCount
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (lineHeight * 0.7f).coerceIn(12f, 48f)
            }
            if (block.isVertical) {
                // Rotate 90° CW: translate(right, top) + rotate(90°) makes local +x = screen down,
                // local +y = screen left. Layout width = block height fills the column top-to-bottom.
                val layout = StaticLayout.Builder
                    .obtain(translatedText, 0, translatedText.length, textPaint, rect.height().coerceAtLeast(1f).toInt())
                    .build()
                canvas.save()
                canvas.translate(rect.right, rect.top)
                canvas.rotate(90f)
                canvas.translate(0f, (rect.width() - layout.height).coerceAtLeast(0f) / 2f)
                layout.draw(canvas)
                canvas.restore()
            } else {
                val layout = StaticLayout.Builder
                    .obtain(translatedText, 0, translatedText.length, textPaint, rect.width().coerceAtLeast(1f).toInt())
                    .build()
                canvas.save()
                canvas.translate(rect.left, rect.top + (rect.height() - layout.height) / 2f)
                layout.draw(canvas)
                canvas.restore()
            }
        }
        return result
    }

    companion object {
        private const val MAX_OCR_DIM = 3840
    }

    fun close() {
        recognizer.close()
        translator.close()
    }
}

private fun Bitmap.scaleToMax(maxDim: Int): Bitmap {
    if (width <= maxDim && height <= maxDim) return this
    val scale = maxDim.toFloat() / maxOf(width, height)
    val matrix = Matrix().apply { postScale(scale, scale) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
