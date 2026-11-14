package com.example.smartalbum.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.smartalbum.data.PhotoDao
import com.example.smartalbum.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GalleryScanner {

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED
    )

    suspend fun syncNewPhotos(context: Context, dao: PhotoDao): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        var inserted = 0
        resolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                if (dao.getByMediaId(mediaId) != null) continue
                val dateAdded = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val entity = PhotoEntity(
                    mediaId = mediaId,
                    uriString = uri.toString(),
                    dateAddedSec = dateAdded,
                    analyzed = false
                )
                if (dao.insert(entity) != -1L) inserted++
            }
        }
        inserted
    }

    /**
     * 同步媒体库，添加新照片并删除已不存在的照片
     */
    suspend fun syncPhotos(context: Context, dao: PhotoDao): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        
        // 获取所有媒体库中的照片ID
        val mediaStoreIds = mutableSetOf<Long>()
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                mediaStoreIds.add(cursor.getLong(idCol))
            }
        }
        
        // 获取数据库中的所有照片ID
        val dbIds = dao.getAllMediaIds().toSet()
        
        // 删除数据库中不存在于媒体库的照片
        val toDelete = dbIds - mediaStoreIds
        val deleted = toDelete.size
        if (deleted > 0) {
            dao.deleteByMediaIds(toDelete.toList())
        }
        
        // 添加新照片
        val inserted = syncNewPhotos(context, dao)
        
        Pair(inserted, deleted)
    }

    fun buildUri(mediaId: Long): Uri {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(collection, mediaId)
    }
}
