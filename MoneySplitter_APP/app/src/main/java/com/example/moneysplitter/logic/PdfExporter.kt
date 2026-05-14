package com.example.moneysplitter.logic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.moneysplitter.data.Expense
import com.example.moneysplitter.data.Settlement
import com.example.moneysplitter.data.TripData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // Table column positions (x offsets from MARGIN)
    private const val COL_NAME = 0f           // Name — 0..130
    private const val COL_AMOUNT = 135f       // Amount — 135..230
    private const val COL_PAID = 235f         // Paid by — 235..320
    private const val COL_SPLIT = 325f        // Split among — 325..515
    private val COL_WIDTHS = floatArrayOf(130f, 95f, 85f, CONTENT_WIDTH - 325f)

    private const val ROW_HEIGHT = 18f
    private const val HEADER_HEIGHT = 20f
    private const val TEXT_SIZE = 9f
    private const val HEADER_TEXT_SIZE = 8.5f
    private val STRIPE_COLOR = Color.parseColor("#F2F2F2")

    fun export(
        context: Context,
        trip: TripData,
        balances: Map<String, Double>,
        settlements: List<Settlement>
    ): File {
        val document = PdfDocument()
        val writer = PageWriter(document)
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // ── Title ──
        writer.drawCenteredText("TRIP EXPENSE REPORT", 20f, bold = true)
        writer.advance(8f)
        writer.drawLine()
        writer.advance(16f)

        // ── Trip info ──
        writer.drawText("Trip: ${trip.name}", 14f, bold = true)
        writer.advance(4f)

        if (trip.startDate != null || trip.endDate != null) {
            val startStr = trip.startDate?.let {
                try { dateFormat.format(isoFormat.parse(it)!!) } catch (_: Exception) { it }
            } ?: "—"
            val endStr = trip.endDate?.let {
                try { dateFormat.format(isoFormat.parse(it)!!) } catch (_: Exception) { it }
            } ?: "—"
            writer.drawText("Period: $startStr  →  $endStr", 11f)
            writer.advance(4f)
        }

        writer.drawText("People: ${trip.people.joinToString(", ")}", 11f)
        writer.advance(4f)

        val settledCount = trip.expenses.count { it.settled }
        val totalCount = trip.expenses.size
        writer.drawText(
            "Expenses: $totalCount${if (settledCount > 0) " ($settledCount settled — excluded from balances)" else ""}",
            11f
        )
        writer.advance(20f)

        // ── Expenses table ──
        writer.drawSectionHeader("EXPENSES")
        writer.advance(12f)

        if (trip.expenses.isEmpty()) {
            writer.drawText("No expenses recorded.", 11f, color = Color.GRAY)
            writer.advance(12f)
        } else {
            // Sort: dated first (ascending), then undated
            val dated = trip.expenses.filter { it.date != null }
                .sortedBy { it.date }
            val undated = trip.expenses.filter { it.date == null }

            // Group dated expenses by date
            val groupedByDate = dated.groupBy { it.date!! }

            // Draw table for each date group
            val dateEntries = groupedByDate.entries.toList()
            dateEntries.forEachIndexed { groupIndex, (date, expenses) ->
                val displayDate = try {
                    dateFormat.format(isoFormat.parse(date)!!)
                } catch (_: Exception) { date }

                // Thin separator line between date groups (not before the first one)
                if (groupIndex > 0) {
                    writer.advance(16f)
                    writer.drawThinLine()
                    writer.advance(16f)
                }

                writer.ensureSpace(50f)
                writer.drawText("📅  $displayDate", 11f, bold = true)
                writer.advance(4f)
                drawExpenseTableHeader(writer)
                expenses.forEachIndexed { index, expense ->
                    drawExpenseTableRow(writer, expense, trip.people, index)
                }
            }

            // Undated expenses
            if (undated.isNotEmpty()) {
                if (dateEntries.isNotEmpty()) {
                    writer.advance(16f)
                    writer.drawThinLine()
                    writer.advance(16f)
                }

                writer.ensureSpace(50f)
                writer.drawText("📅  No date", 11f, bold = true, color = Color.GRAY)
                writer.advance(4f)
                writer.advance(8f)
                drawExpenseTableHeader(writer)
                undated.forEachIndexed { index, expense ->
                    drawExpenseTableRow(writer, expense, trip.people, index)
                }
            }

            writer.advance(16f)
        }

        // ── Exchange rates (only used currencies) ──
        val usedCurrencies = trip.expenses.map { it.currency }.toSet()
        val relevantRates = trip.conversionRates.filter { (currency, _) ->
            currency in usedCurrencies && currency != trip.baseCurrency
        }

        if (relevantRates.isNotEmpty()) {
            writer.ensureSpace(60f)
            writer.drawSectionHeader("EXCHANGE RATES")
            writer.advance(12f)

            writer.drawText("Base currency: ${trip.baseCurrency}", 11f, bold = true)
            writer.advance(6f)

            for ((currency, rate) in relevantRates) {
                writer.drawText("      1 $currency  =  ${"%.4f".format(rate)} ${trip.baseCurrency}", 10f)
                writer.advance(4f)
            }
            writer.advance(12f)
        }

        // ── Balances ──
        writer.ensureSpace(80f)
        writer.drawSectionHeader("BALANCES  (${trip.resultCurrency})")
        writer.advance(12f)

        if (balances.isEmpty() || balances.values.all { abs(it) < 0.01 }) {
            writer.drawText("Everyone is settled up!", 11f)
        } else {
            for ((person, balance) in balances.entries.sortedByDescending { it.value }) {
                val status = when {
                    balance > 0.01 -> "is owed"
                    balance < -0.01 -> "owes"
                    else -> "settled"
                }
                writer.drawText(
                    "      $person:  %+,.2f %s  (%s)".format(balance, trip.resultCurrency, status),
                    10f
                )
                writer.advance(4f)
            }
        }
        writer.advance(16f)

        // ── Settlements ──
        if (settlements.isNotEmpty()) {
            writer.ensureSpace(60f)
            writer.drawSectionHeader("SETTLEMENTS")
            writer.advance(12f)

            writer.drawText("Minimum transfers needed:", 10f, color = Color.DKGRAY)
            writer.advance(8f)

            for (settlement in settlements) {
                writer.drawText(
                    "      ${settlement.from}  →  ${settlement.to}:  %,.2f %s".format(
                        settlement.amount, trip.resultCurrency
                    ),
                    11f
                )
                writer.advance(5f)
            }
        }

        // ── Footer ──
        writer.advance(24f)
        writer.drawLine()
        writer.advance(8f)
        writer.drawText(
            "Generated on ${dateFormat.format(Date())} by MoneySplitter",
            9f,
            color = Color.GRAY
        )

        writer.finish()

        // Save to cache
        val exportDir = File(context.cacheDir, "pdf_exports")
        exportDir.mkdirs()
        val sanitizedName = trip.name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").take(40).trim()
        val file = File(exportDir, "${sanitizedName}_report.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return file
    }

    private fun drawExpenseTableHeader(writer: PageWriter) {
        writer.ensureSpace(HEADER_HEIGHT + 4f)
        val top = writer.currentY
        // Black header background
        writer.drawRect(MARGIN, top, CONTENT_WIDTH, HEADER_HEIGHT, Color.BLACK)
        // White text centered vertically in the header
        val textY = top + HEADER_HEIGHT - (HEADER_HEIGHT - HEADER_TEXT_SIZE) / 2f - 1f
        writer.drawTextAbsolute(MARGIN + COL_NAME + 6f, textY, "NAME", HEADER_TEXT_SIZE, bold = true, color = Color.WHITE)
        writer.drawTextAbsolute(MARGIN + COL_AMOUNT + 6f, textY, "AMOUNT", HEADER_TEXT_SIZE, bold = true, color = Color.WHITE)
        writer.drawTextAbsolute(MARGIN + COL_PAID + 6f, textY, "PAID BY", HEADER_TEXT_SIZE, bold = true, color = Color.WHITE)
        writer.drawTextAbsolute(MARGIN + COL_SPLIT + 6f, textY, "SPLIT AMONG", HEADER_TEXT_SIZE, bold = true, color = Color.WHITE)
        writer.setY(top + HEADER_HEIGHT)
    }

    private fun drawExpenseTableRow(writer: PageWriter, expense: Expense, allPeople: List<String>, rowIndex: Int) {
        val hasNotes = expense.notes?.isNotBlank() == true
        val mainRowH = ROW_HEIGHT
        val notesRowH = if (hasNotes) 14f else 0f
        val totalH = mainRowH + notesRowH

        writer.ensureSpace(totalH + 2f)
        val top = writer.currentY

        // Alternating stripe background
        val bgColor = if (rowIndex % 2 == 1) STRIPE_COLOR else Color.WHITE
        writer.drawRect(MARGIN, top, CONTENT_WIDTH, totalH, bgColor)

        // Text baseline for the main row (vertically centered)
        val textY = top + mainRowH - (mainRowH - TEXT_SIZE) / 2f - 1f

        // Name + settled badge
        val name = expense.name?.takeIf { it.isNotBlank() } ?: expense.description
        val displayName = if (expense.settled) "✓ ${name.ifBlank { "—" }}" else name.ifBlank { "—" }
        val nameColor = if (expense.settled) Color.GRAY else Color.BLACK

        // Amount
        val amountStr = formatAmount(expense.amount, expense.currency)

        // Paid by
        val paidBy = expense.paidBy.ifBlank { "—" }

        // Split among
        val splitText = when {
            expense.splitAmong.isEmpty() || expense.splitAmong.size == allPeople.size -> "Everyone"
            else -> expense.splitAmong.joinToString(", ")
        }

        writer.drawTextAbsolute(MARGIN + COL_NAME + 6f, textY, displayName, TEXT_SIZE, color = nameColor, maxWidth = COL_WIDTHS[0] - 12f)
        writer.drawTextAbsolute(MARGIN + COL_AMOUNT + 6f, textY, amountStr, TEXT_SIZE, maxWidth = COL_WIDTHS[1] - 12f)
        writer.drawTextAbsolute(MARGIN + COL_PAID + 6f, textY, paidBy, TEXT_SIZE, maxWidth = COL_WIDTHS[2] - 12f)
        writer.drawTextAbsolute(MARGIN + COL_SPLIT + 6f, textY, splitText, TEXT_SIZE, color = Color.DKGRAY, maxWidth = COL_WIDTHS[3] - 12f)

        // Notes on second line if present
        if (hasNotes) {
            val notesY = top + mainRowH + notesRowH - (notesRowH - 8f) / 2f - 1f
            writer.drawTextAbsolute(MARGIN + COL_NAME + 6f, notesY, "↳ ${expense.notes}", 8f, color = Color.GRAY, italic = true, maxWidth = CONTENT_WIDTH - 12f)
        }

        writer.setY(top + totalH)
    }

    private fun formatAmount(amount: Double, currency: String): String {
        return if (amount == amount.toLong().toDouble()) {
            "%,.0f %s".format(amount, currency)
        } else {
            "%,.2f %s".format(amount, currency)
        }
    }

    // ── Canvas page writer with automatic pagination ──

    private class PageWriter(private val document: PdfDocument) {
        private var pageNumber = 0
        var currentY = MARGIN
            private set
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null

        init {
            newPage()
        }

        private fun newPage() {
            page?.let { document.finishPage(it) }
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page!!.canvas
            currentY = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (currentY + needed > PAGE_HEIGHT - MARGIN) {
                newPage()
            }
        }

        fun advance(amount: Float) {
            currentY += amount
        }

        fun setY(y: Float) {
            currentY = y
        }

        fun drawText(
            text: String,
            size: Float,
            bold: Boolean = false,
            italic: Boolean = false,
            color: Int = Color.BLACK
        ) {
            ensureSpace(size + 4f)
            val paint = makePaint(size, bold, italic, color)

            // Word-wrap
            val words = text.split(" ")
            var line = ""

            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(testLine) > CONTENT_WIDTH && line.isNotEmpty()) {
                    canvas?.drawText(line, MARGIN, currentY, paint)
                    currentY += size + 3f
                    ensureSpace(size + 4f)
                    line = word
                } else {
                    line = testLine
                }
            }
            if (line.isNotEmpty()) {
                canvas?.drawText(line, MARGIN, currentY, paint)
                currentY += size + 3f
            }
        }

        fun drawTextAt(
            x: Float,
            text: String,
            size: Float,
            bold: Boolean = false,
            italic: Boolean = false,
            color: Int = Color.BLACK,
            maxWidth: Float = Float.MAX_VALUE
        ) {
            val paint = makePaint(size, bold, italic, color)
            var drawText = text
            if (maxWidth < Float.MAX_VALUE && paint.measureText(drawText) > maxWidth) {
                while (drawText.length > 1 && paint.measureText("$drawText…") > maxWidth) {
                    drawText = drawText.dropLast(1)
                }
                drawText = "$drawText…"
            }
            canvas?.drawText(drawText, x, currentY, paint)
        }

        fun drawTextAbsolute(
            x: Float,
            y: Float,
            text: String,
            size: Float,
            bold: Boolean = false,
            italic: Boolean = false,
            color: Int = Color.BLACK,
            maxWidth: Float = Float.MAX_VALUE
        ) {
            val paint = makePaint(size, bold, italic, color)
            var drawText = text
            if (maxWidth < Float.MAX_VALUE && paint.measureText(drawText) > maxWidth) {
                while (drawText.length > 1 && paint.measureText("$drawText…") > maxWidth) {
                    drawText = drawText.dropLast(1)
                }
                drawText = "$drawText…"
            }
            canvas?.drawText(drawText, x, y, paint)
        }

        fun drawCenteredText(text: String, size: Float, bold: Boolean = false) {
            ensureSpace(size + 4f)
            val paint = makePaint(size, bold, false, Color.BLACK).apply {
                textAlign = Paint.Align.CENTER
            }
            canvas?.drawText(text, PAGE_WIDTH / 2f, currentY, paint)
            currentY += size + 3f
        }

        fun drawLine() {
            val paint = Paint().apply {
                this.color = Color.DKGRAY
                strokeWidth = 2f
            }
            canvas?.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, paint)
            currentY += 3f
        }

        fun drawThinLine() {
            val paint = Paint().apply {
                this.color = Color.parseColor("#CCCCCC")
                strokeWidth = 0.5f
            }
            canvas?.drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, paint)
            currentY += 1f
        }

        fun drawRect(x: Float, top: Float, width: Float, height: Float, color: Int) {
            val paint = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas?.drawRect(x, top, x + width, top + height, paint)
        }

        fun drawSectionHeader(title: String) {
            drawLine()
            advance(12f)
            drawCenteredText(title, 14f, bold = true)
            advance(8f)
            drawLine()
            advance(4f)
        }

        fun finish() {
            page?.let { document.finishPage(it) }
        }

        private fun makePaint(
            size: Float,
            bold: Boolean,
            italic: Boolean,
            color: Int
        ): Paint = Paint().apply {
            this.color = color
            textSize = size
            isAntiAlias = true
            typeface = when {
                bold && italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }
    }
}
