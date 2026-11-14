package com.example.smartalbum.media

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.example.smartalbum.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewPhotoObserver(
    private val appContext: Context,
    private val scope: CoroutineScope,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean) {
        scope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(appContext).photoDao()
            GalleryScanner.syncNewPhotos(appContext, dao)
        }
    }
}
