package no.skasti.skynvaettr.reporting

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Small dependency-free heatmap renderer for CI/report diagnostics. */
class HeatmapRenderer {
    fun render(
        title: String,
        rowLabels: List<String>,
        columnLabels: List<String>,
        values: List<DoubleArray>,
        output: Path,
        valueFormatter: (Double) -> String = { "%.2f".format(it) },
    ) {
        require(rowLabels.isNotEmpty()) { "rowLabels must not be empty" }
        require(columnLabels.isNotEmpty()) { "columnLabels must not be empty" }
        require(values.size == rowLabels.size) { "one value row is required per row label" }
        require(values.all { it.size == columnLabels.size }) { "all value rows must match column labels" }

        val labelFont = Font(Font.SANS_SERIF, Font.PLAIN, 15)
        val metricsImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val metricsGraphics = metricsImage.createGraphics()
        metricsGraphics.font = labelFont
        val metrics = metricsGraphics.fontMetrics
        val cellWidth = maxOf(110, columnLabels.maxOf { metrics.stringWidth(it) } + 28)
        val leftMargin = maxOf(180, rowLabels.maxOf { metrics.stringWidth(it) } + 36)
        metricsGraphics.dispose()

        val cellHeight = 58
        val topMargin = 125
        val rightMargin = 45
        val bottomMargin = 80
        val width = leftMargin + columnLabels.size * cellWidth + rightMargin
        val height = topMargin + rowLabels.size * cellHeight + bottomMargin
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
            graphics.color = Color(30, 30, 30)
            graphics.drawString(title, 28, 40)

            graphics.font = labelFont
            columnLabels.forEachIndexed { column, label ->
                val x = leftMargin + column * cellWidth
                graphics.color = Color(45, 45, 45)
                val labelWidth = graphics.fontMetrics.stringWidth(label)
                graphics.drawString(label, x + (cellWidth - labelWidth) / 2, topMargin - 18)
            }

            val max = values.flatMap { row -> row.asIterable() }.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
            rowLabels.forEachIndexed { row, label ->
                val y = topMargin + row * cellHeight
                graphics.color = Color(45, 45, 45)
                val labelWidth = graphics.fontMetrics.stringWidth(label)
                graphics.drawString(label, leftMargin - labelWidth - 18, y + 35)

                values[row].forEachIndexed { column, rawValue ->
                    val normalized = (rawValue / max).coerceIn(0.0, 1.0)
                    val x = leftMargin + column * cellWidth
                    graphics.color = heatColor(normalized)
                    graphics.fillRect(x, y, cellWidth - 2, cellHeight - 2)
                    graphics.color = if (normalized > 0.55) Color.WHITE else Color(20, 20, 20)
                    val text = valueFormatter(rawValue)
                    val textWidth = graphics.fontMetrics.stringWidth(text)
                    graphics.drawString(text, x + (cellWidth - textWidth) / 2, y + 34)
                }
            }
        } finally {
            graphics.dispose()
        }

        Files.createDirectories(output.parent)
        ImageIO.write(image, "png", output.toFile())
    }

    private fun heatColor(value: Double): Color {
        val hue = (0.60 - value * 0.50).toFloat()
        val saturation = (0.25 + value * 0.65).toFloat()
        val brightness = (1.0 - value * 0.35).toFloat()
        return Color.getHSBColor(hue, saturation, brightness)
    }
}
