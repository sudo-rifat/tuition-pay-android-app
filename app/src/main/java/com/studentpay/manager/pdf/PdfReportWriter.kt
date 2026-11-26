package com.studentpay.manager.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.studentpay.manager.domain.CellAlign
import com.studentpay.manager.domain.PdfReportSpec
import java.io.OutputStream

/**
 * Renders a [PdfReportSpec] to an A4 PDF with the platform PdfDocument API (no library).
 * Text is drawn through the system font stack, so Bengali student names render correctly.
 * Rows that do not fit continue on new pages with the table header repeated.
 */
class PdfReportWriter {

    fun write(spec: PdfReportSpec, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Layout(doc, spec).run()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Layout(private val doc: PdfDocument, private val spec: PdfReportSpec) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 40f
        private val contentW = pageW - 2 * margin
        private val rowH = 24f
        private val pad = 8f

        private val accent = 0xFF3F51B5.toInt()
        private val ink = 0xFF1B1B1F.toInt()
        private val stripe = 0xFFF1F1F6.toInt()

        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f }
        private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val headText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val fill = Paint().apply { style = Paint.Style.FILL }
        private val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF74777F.toInt(); textSize = 9f; textAlign = Paint.Align.CENTER
        }

        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        private val totalWeight = spec.columns.sumOf { it.weight.toDouble() }.toFloat()
        private val colWidths = spec.columns.map { it.weight / totalWeight * contentW }

        fun run() {
            newPage(firstPage = true)

            for (line in spec.infoLines) {
                ensure(20f)
                canvas().drawText(fit(line, bold, contentW), margin, y + 14f, bold)
                y += 20f
            }
            if (spec.infoLines.isNotEmpty()) y += 8f

            drawTableHeader()
            if (spec.rows.isEmpty()) {
                ensure(rowH)
                canvas().drawText(spec.emptyMessage, margin + pad, y + 16f, text)
                y += rowH
            }
            spec.rows.forEachIndexed { index, row ->
                if (y + rowH > pageH - margin - 20f) {
                    newPage(firstPage = false)
                    drawTableHeader()
                }
                drawRow(row, striped = index % 2 == 1)
            }

            y += 14f
            for (line in spec.summaryLines) {
                ensure(20f)
                canvas().drawText(fit(line, bold, contentW), margin, y + 14f, bold)
                y += 20f
            }
            finishPage()
        }

        private fun canvas() = page!!.canvas

        private fun ensure(height: Float) {
            if (y + height > pageH - margin - 20f) newPage(firstPage = false)
        }

        private fun newPage(firstPage: Boolean) {
            if (page != null) finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            if (firstPage) {
                fill.color = accent
                canvas().drawRect(0f, 0f, pageW.toFloat(), 84f, fill)
                val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 12f; textAlign = Paint.Align.CENTER
                }
                canvas().drawText(spec.title, pageW / 2f, 38f, title)
                canvas().drawText(spec.subtitle, pageW / 2f, 62f, sub)
                y = 84f + 26f
            } else {
                y = margin
            }
        }

        private fun finishPage() {
            val p = page ?: return
            p.canvas.drawText("Page $pageNo", pageW / 2f, pageH - 20f, footer)
            doc.finishPage(p)
            page = null
        }

        private fun drawTableHeader() {
            fill.color = accent
            canvas().drawRect(margin, y, margin + contentW, y + rowH, fill)
            drawCells(spec.columns.map { it.title }, headText)
            y += rowH
        }

        private fun drawRow(cells: List<String>, striped: Boolean) {
            if (striped) {
                fill.color = stripe
                canvas().drawRect(margin, y, margin + contentW, y + rowH, fill)
            }
            drawCells(cells, text)
            y += rowH
        }

        private fun drawCells(cells: List<String>, paint: Paint) {
            var x = margin
            spec.columns.forEachIndexed { i, col ->
                val w = colWidths[i]
                val value = fit(cells.getOrElse(i) { "" }, paint, w - 2 * pad)
                when (col.align) {
                    CellAlign.START -> { paint.textAlign = Paint.Align.LEFT; canvas().drawText(value, x + pad, y + 16f, paint) }
                    CellAlign.CENTER -> { paint.textAlign = Paint.Align.CENTER; canvas().drawText(value, x + w / 2f, y + 16f, paint) }
                    CellAlign.END -> { paint.textAlign = Paint.Align.RIGHT; canvas().drawText(value, x + w - pad, y + 16f, paint) }
                }
                x += w
            }
            paint.textAlign = Paint.Align.LEFT
        }

        /** Truncates with an ellipsis so long names never overflow into the next column. */
        private fun fit(value: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(value) <= maxWidth) return value
            val ellipsis = "\u2026"
            val room = maxWidth - paint.measureText(ellipsis)
            if (room <= 0f) return ""
            val count = paint.breakText(value, true, room, null)
            return value.substring(0, count).trimEnd() + ellipsis
        }
    }
}
