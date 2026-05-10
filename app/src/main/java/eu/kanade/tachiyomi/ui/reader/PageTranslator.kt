package eu.kanade.tachiyomi.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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

data class TranslatedBlock(
    val text: String,
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float,
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

    /** Blocking — call from a background thread only. */
    fun translate(bitmap: Bitmap): List<TranslatedBlock> {
        val scaled = bitmap.scaleToMax(MAX_OCR_DIM)
        val visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(scaled, 0)))
        return visionText.textBlocks.mapIndexedNotNull { i, block ->
            val box = block.boundingBox ?: run {
                logcat(LogPriority.WARN) { "translate: block[$i] null box, skipping" }
                return@mapIndexedNotNull null
            }
            logcat { "translate: block[$i] '${block.text.take(40)}'" }
            val translated = Tasks.await(translator.translate(block.text))
            logcat { "translate: block[$i] → '${translated.take(40)}'" }
            TranslatedBlock(
                text = translated,
                leftFrac = box.left.toFloat() / scaled.width,
                topFrac = box.top.toFloat() / scaled.height,
                rightFrac = box.right.toFloat() / scaled.width,
                bottomFrac = box.bottom.toFloat() / scaled.height,
            )
        }
    }

    fun annotate(bitmap: Bitmap, blocks: List<TranslatedBlock>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            style = Paint.Style.FILL
        }
        blocks.forEach { block ->
            val rect = RectF(
                block.leftFrac * result.width,
                block.topFrac * result.height,
                block.rightFrac * result.width,
                block.bottomFrac * result.height,
            )
            val boxW = rect.width().coerceAtLeast(1f).toInt()
            canvas.drawRect(rect, bgPaint)
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (rect.height() / 2f).coerceIn(12f, 48f)
            }
            val layout = StaticLayout.Builder
                .obtain(block.text, 0, block.text.length, textPaint, boxW)
                .build()
            canvas.save()
            canvas.translate(rect.left, rect.top + (rect.height() - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
        return result
    }

    companion object {
        private const val MAX_OCR_DIM = 1920
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
