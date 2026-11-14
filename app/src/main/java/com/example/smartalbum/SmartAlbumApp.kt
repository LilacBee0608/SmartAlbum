package com.example.smartalbum

import android.app.Application
import android.os.Build
import android.provider.MediaStore
import androidx.work.WorkManager
import com.example.smartalbum.media.NewPhotoObserver
import com.example.smartalbum.work.PhotoAnalysisWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SmartAlbumApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    private var photoObserver: NewPhotoObserver? = null

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).cancelUniqueWork(PhotoAnalysisWorker.UNIQUE_SLOW_WORK)
        registerGalleryObserver()
    }

    private fun registerGalleryObserver() {
        val observer = NewPhotoObserver(applicationContext, applicationScope)
        photoObserver = observer
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        contentResolver.registerContentObserver(uri, true, observer)
    }
}
