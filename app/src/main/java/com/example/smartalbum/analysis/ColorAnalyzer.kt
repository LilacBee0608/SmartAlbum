package com.example.smartalbum.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.example.smartalbum.data.ColorCategory
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ColorAnalysisResult(
    val category: ColorCategory,
    val dominantColors: List<Int>,
    val colorCategories: List<ColorCategory>,
    val dominantHsv: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ColorAnalysisResult
        if (category != other.category) return false
        if (dominantColors != other.dominantColors) return false
        if (colorCategories != other.colorCategories) return false
        if (dominantHsv != null) {
            if (other.dominantHsv == null) return false
            if (!dominantHsv.contentEquals(other.dominantHsv)) return false
        } else if (other.dominantHsv != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = category.hashCode()
        result = 31 * result + dominantColors.hashCode()
        result = 31 * result + colorCategories.hashCode()
        result = 31 * result + (dominantHsv?.contentHashCode() ?: 0)
        return result
    }
}

private data class ColorPoint(
    val r: Int,
    val g: Int,
    val b: Int,
    val weight: Int = 1
) {
    fun toRgb(): Int = Color.rgb(r, g, b)

    fun toHsv(): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(toRgb(), hsv)
        return hsv
    }

    fun perceptualDistance(other: ColorPoint): Double {
        val dr = (r - other.r).toDouble()
        val dg = (g - other.g).toDouble()
        val db = (b - other.b).toDouble()
        val dr2 = dr * dr
        val dg2 = dg * dg
        val db2 = db * db
        return sqrt(2 * dr2 + 4 * dg2 + 3 * db2)
    }

    fun labDistance(other: ColorPoint): Double {
        val lab1 = toLab()
        val lab2 = other.toLab()
        val dl = lab1[0] - lab2[0]
        val da = lab1[1] - lab2[1]
        val db = lab1[2] - lab2[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    fun toLab(): DoubleArray {
        val xyz = toXyz()
        var x = xyz[0] / 95.047
        var y = xyz[1] / 100.0
        var z = xyz[2] / 108.883

        x = if (x > 0.008856) x.pow(1.0 / 3.0) else (7.787 * x) + (16.0 / 116.0)
        y = if (y > 0.008856) y.pow(1.0 / 3.0) else (7.787 * y) + (16.0 / 116.0)
        z = if (z > 0.008856) z.pow(1.0 / 3.0) else (7.787 * z) + (16.0 / 116.0)

        return doubleArrayOf((116.0 * y) - 16, 500.0 * (x - y), 200.0 * (y - z))
    }

    fun toXyz(): DoubleArray {
        var r = r / 255.0
        var g = g / 255.0
        var b = b / 255.0

        r = if (r > 0.04045) ((r + 0.055) / 1.055).pow(2.4) else r / 12.92
        g = if (g > 0.04045) ((g + 0.055) / 1.055).pow(2.4) else g / 12.92
        b = if (b > 0.04045) ((b + 0.055) / 1.055).pow(2.4) else b / 12.92

        r *= 100.0
        g *= 100.0
        b *= 100.0

        return doubleArrayOf(
            r * 0.4124 + g * 0.3576 + b * 0.1805,
            r * 0.2126 + g * 0.7152 + b * 0.0722,
            r * 0.0193 + g * 0.1192 + b * 0.9505
        )
    }
}

object ColorAnalyzer {

    private const val K = 3
    private const val MAX_ITERATIONS = 100
    private const val THRESHOLD = 2.0
    private const val MIN_SATURATION = 0.08f
    private const val MIN_BRIGHTNESS = 0.05f
    private const val MAX_BRIGHTNESS = 0.95f
    private const val EDGE_MARGIN = 0.08f

    suspend fun analyze(bitmap: Bitmap): ColorAnalysisResult = withContext(Dispatchers.Default) {
        val samples = samplePixelsSmart(bitmap)

        val centroids = kMeansPerceptual(samples, K)

        val dominantColors = centroids
            .sortedByDescending { it.weight }
            .take(K)
            .map { it.toRgb() }

        val finalColors = if (dominantColors.size < K) {
            dominantColors + List(K - dominantColors.size) { Color.WHITE }
        } else {
            dominantColors
        }

        val uniqueColors = filterSimilarColors(finalColors)

        val mainColor = uniqueColors.firstOrNull() ?: Color.WHITE
        val hsv = FloatArray(3)
        Color.colorToHSV(mainColor, hsv)
        val category = ColorCategory.fromHsv(hsv[0], hsv[1], hsv[2])

        val colorCategories = uniqueColors.map { color ->
            val colorHsv = FloatArray(3)
            Color.colorToHSV(color, colorHsv)
            ColorCategory.fromHsv(colorHsv[0], colorHsv[1], colorHsv[2])
        }

        ColorAnalysisResult(category, uniqueColors, colorCategories, hsv)
    }

    private fun samplePixelsSmart(bitmap: Bitmap): List<ColorPoint> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()

        val maxSamples = 8000
        val step = max(1, sqrt((w * h).toDouble() / maxSamples).toInt())

        val marginX = (w * EDGE_MARGIN).toInt()
        val marginY = (h * EDGE_MARGIN).toInt()

        val samples = mutableListOf<ColorPoint>()

        for (y in marginY until h - marginY step step) {
            for (x in marginX until w - marginX step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)

                if (hsv[1] >= MIN_SATURATION && hsv[2] >= MIN_BRIGHTNESS && hsv[2] <= MAX_BRIGHTNESS) {
                    samples.add(ColorPoint(r, g, b))
                }
            }
        }

        if (samples.isEmpty()) {
            for (y in 0 until h step step) {
                for (x in 0 until w step step) {
                    val pixel = bitmap.getPixel(x, y)
                    samples.add(
                        ColorPoint(
                            Color.red(pixel),
                            Color.green(pixel),
                            Color.blue(pixel)
                        )
                    )
                }
            }
        }

        return samples
    }

    private fun filterSimilarColors(colors: List<Int>): List<Int> {
        if (colors.size <= 1) return colors

        val result = mutableListOf<Int>()
        val hsvValues = colors.map { color ->
            FloatArray(3).also { Color.colorToHSV(color, it) }
        }

        for (i in colors.indices) {
            val color = colors[i]
            val hsv1 = hsvValues[i]

            var isSimilar = false
            for (j in result.indices) {
                val existingColor = result[j]
                val existingHsv = FloatArray(3).also { Color.colorToHSV(existingColor, it) }

                val hueDiff = min(abs(hsv1[0] - existingHsv[0]), 360f - abs(hsv1[0] - existingHsv[0]))
                val satDiff = abs(hsv1[1] - existingHsv[1])
                val briDiff = abs(hsv1[2] - existingHsv[2])

                if (hueDiff < 15f && satDiff < 0.15f && briDiff < 0.15f) {
                    isSimilar = true
                    break
                }
            }

            if (!isSimilar) {
                result.add(color)
            }
        }

        return result.take(3)
    }

    private fun kMeansPerceptual(points: List<ColorPoint>, k: Int): List<ColorPoint> {
        if (points.isEmpty()) return List(k) { ColorPoint(255, 255, 255) }

        var centroids = initializeCentroids(points, k)
        var converged = false
        var iterations = 0

        while (!converged && iterations < MAX_ITERATIONS) {
            val clusters = MutableList(k) { mutableListOf<ColorPoint>() }

            for (point in points) {
                var minDistance = Double.MAX_VALUE
                var closestIndex = 0

                for (i in centroids.indices) {
                    val distance = point.labDistance(centroids[i])
                    if (distance < minDistance) {
                        minDistance = distance
                        closestIndex = i
                    }
                }
                clusters[closestIndex].add(point)
            }

            val newCentroids = mutableListOf<ColorPoint>()
            for (cluster in clusters) {
                if (cluster.isNotEmpty()) {
                    newCentroids.add(calculateCentroid(cluster))
                } else {
                    newCentroids.add(centroids[newCentroids.size.coerceAtMost(k - 1)])
                }
            }

            converged = isConverged(centroids, newCentroids)
            centroids = newCentroids
            iterations++
        }

        val mutableCentroids = centroids.toMutableList()
        for (i in mutableCentroids.indices) {
            val clusterPoints = mutableListOf<ColorPoint>()
            for (point in points) {
                var minDistance = Double.MAX_VALUE
                var closestIndex = 0
                for (j in mutableCentroids.indices) {
                    val distance = point.labDistance(mutableCentroids[j])
                    if (distance < minDistance) {
                        minDistance = distance
                        closestIndex = j
                    }
                }
                if (closestIndex == i) {
                    clusterPoints.add(point)
                }
            }
            if (clusterPoints.isNotEmpty()) {
                mutableCentroids[i] = calculateCentroid(clusterPoints)
            }
        }

        return mutableCentroids
    }

    private fun initializeCentroids(points: List<ColorPoint>, k: Int): List<ColorPoint> {
        if (points.isEmpty()) return List(k) { ColorPoint(255, 255, 255) }

        val centroids = mutableListOf<ColorPoint>()
        val point = points.random()
        centroids.add(point)

        for (i in 1 until k) {
            val distances = points.map { p ->
                centroids.minOf { centroid -> p.labDistance(centroid) }
            }
            val totalDistance = distances.sum()
            val random = Math.random() * totalDistance
            var cumulativeDistance = 0.0

            for (j in points.indices) {
                cumulativeDistance += distances[j]
                if (cumulativeDistance >= random) {
                    centroids.add(points[j])
                    break
                }
            }
        }

        return centroids
    }

    private fun calculateCentroid(points: List<ColorPoint>): ColorPoint {
        if (points.isEmpty()) return ColorPoint(128, 128, 128)

        val totalWeight = points.sumOf { it.weight }
        val r = (points.sumOf { it.r * it.weight } / totalWeight).toInt()
        val g = (points.sumOf { it.g * it.weight } / totalWeight).toInt()
        val b = (points.sumOf { it.b * it.weight } / totalWeight).toInt()

        return ColorPoint(
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255),
            totalWeight
        )
    }

    private fun isConverged(oldCentroids: List<ColorPoint>, newCentroids: List<ColorPoint>): Boolean {
        for (i in oldCentroids.indices) {
            if (oldCentroids[i].labDistance(newCentroids[i]) > THRESHOLD) {
                return false
            }
        }
        return true
    }
}