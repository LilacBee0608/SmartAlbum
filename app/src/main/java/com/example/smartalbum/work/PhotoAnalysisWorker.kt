package com.example.smartalbum.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartalbum.analysis.BitmapLoader
import com.example.smartalbum.analysis.ColorAnalyzer
import com.example.smartalbum.data.AppDatabase
import com.example.smartalbum.data.ColorCategory
import com.example.smartalbum.data.PhotoDao
import com.example.smartalbum.data.PhotoEntity
import com.example.smartalbum.media.GalleryScanner
import kotlinx.coroutines.delay

class PhotoAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).photoDao()
        GalleryScanner.syncNewPhotos(applicationContext, dao)

        val singleId = inputData.getLong(KEY_MEDIA_ID, -1L)
        if (singleId >= 0L) {
            val row = dao.getByMediaId(singleId)
            if (row != null && !row.analyzed) {
                analyzeOne(applicationContext, dao, row)
            }
            return Result.success()
        }

        val batch = dao.loadUnanalyzed(BATCH_SIZE)
        if (batch.isEmpty()) return Result.success()
        for (row in batch) {
            analyzeOne(applicationContext, dao, row)
            delay(DELAY_MS_PER_ITEM)
        }
        return Result.success()
    }

    private suspend fun analyzeOne(ctx: Context, dao: PhotoDao, row: PhotoEntity) {
        val uri = Uri.parse(row.uriString)
        val bmp = BitmapLoader.loadThumbnail(ctx, uri)
        if (bmp == null) {
            dao.update(
                row.copy(
                    analyzed = true,
                    colorCategory = ColorCategory.UNKNOWN.name,
                    dominantColorHex = null,
                    analyzedAtMs = System.currentTimeMillis()
                )
            )
            return
        }
        val result = try {
            ColorAnalyzer.analyze(bmp)
        } catch (_: Exception) {
            dao.update(
                row.copy(
                    analyzed = true,
                    colorCategory = ColorCategory.UNKNOWN.name,
                    dominantColorHex = null,
                    color1Hex = null,
                    color2Hex = null,
                    color3Hex = null,
                    analyzedAtMs = System.currentTimeMillis()
                )
            )
            if (!bmp.isRecycled) bmp.recycle()
            return
        }
        if (!bmp.isRecycled) bmp.recycle()
        
        // 将颜色转换为十六进制字符串
        val color1Hex = if (result.dominantColors.isNotEmpty()) {
            String.format("#%06X", 0xFFFFFF and result.dominantColors[0])
        } else null
        val color2Hex = if (result.dominantColors.size > 1) {
            String.format("#%06X", 0xFFFFFF and result.dominantColors[1])
        } else null
        val color3Hex = if (result.dominantColors.size > 2) {
            String.format("#%06X", 0xFFFFFF and result.dominantColors[2])
        } else null
        
        // 获取颜色分类
        val color1Category = if (result.colorCategories.isNotEmpty()) {
            result.colorCategories[0].name
        } else null
        val color2Category = if (result.colorCategories.size > 1) {
            result.colorCategories[1].name
        } else null
        val color3Category = if (result.colorCategories.size > 2) {
            result.colorCategories[2].name
        } else null
        
        dao.update(
            row.copy(
                analyzed = true,
                colorCategory = result.category.name,
                dominantColorHex = color1Hex,  // 保持向后兼容
                color1Hex = color1Hex,
                color2Hex = color2Hex,
                color3Hex = color3Hex,
                color1Category = color1Category,
                color2Category = color2Category,
                color3Category = color3Category,
                analyzedAtMs = System.currentTimeMillis()
            )
        )
    }

    companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val UNIQUE_SLOW_WORK = "slow_color_analysis"
        private const val BATCH_SIZE = 6
        private const val DELAY_MS_PER_ITEM = 150L
    }
}
