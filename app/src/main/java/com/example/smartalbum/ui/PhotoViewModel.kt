package com.example.smartalbum.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartalbum.analysis.BitmapLoader
import com.example.smartalbum.analysis.ColorAnalyzer
import com.example.smartalbum.data.AppDatabase
import com.example.smartalbum.data.ColorCategory
import com.example.smartalbum.data.MonthGroup
import com.example.smartalbum.data.PhotoEntity
import com.example.smartalbum.media.GalleryScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 四元组数据类
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

data class AlbumUiState(
    val pendingCount: Int = 0,
    val categoryCounts: Map<ColorCategory, Int> = emptyMap(),
    val photos: List<PhotoEntity> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
    val collapsedMonths: Set<String> = emptySet(),
    val selectedCategories: Set<ColorCategory> = emptySet(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val analyzingMediaIds: Set<Long> = emptySet(),
    val isFavoritesOnly: Boolean = false,
    val favoritesVersion: Int = 0,
    val isRefreshing: Boolean = false
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).photoDao()

    private val selectedCategories = MutableStateFlow<Set<ColorCategory>>(emptySet())
    private val searchQuery = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val analyzingMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    private val collapsedMonths = MutableStateFlow<Set<String>>(emptySet())
    private val isFavoritesOnly = MutableStateFlow(false)
    private val favoritesVersion = MutableStateFlow(0)
    private val isRefreshing = MutableStateFlow(false)

    fun refreshGallery() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                GalleryScanner.syncPhotos(getApplication(), dao)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private val searchAndCategoryFlow = combine(selectedCategories, searchQuery, isSearching, isFavoritesOnly) {
        categories, query, searching, isFav ->
        Quadruple(categories, query, searching, isFav)
    }

    private val photosFlow = searchAndCategoryFlow.flatMapLatest { (categories, query, searching, isFav) ->
        when {
            searching && query.isNotEmpty() -> {
                if (isFav) dao.observeFavoritesByTag(query) else dao.observeByTag(query)
            }
            categories.isEmpty() -> {
                if (isFav) dao.observeFavorites() else dao.observeAll()
            }
            else -> {
                val categoryNames = categories.map { it.name }
                if (isFav) {
                    dao.observeFavoritesByCategories(categoryNames, categoryNames.size)
                } else {
                    dao.observeByMultipleCategories(categoryNames, categoryNames.size)
                }
            }
        }
    }

    // 中间状态数据类
    private data class CombinedState(
        val pending: Int,
        val counts: Map<ColorCategory, Int>,
        val photos: List<PhotoEntity>,
        val selected: Set<ColorCategory>,
        val query: String,
        val searching: Boolean,
        val analyzing: Set<Long>,
        val isFav: Boolean,
        val refreshing: Boolean,
        val favVersion: Int
    )

    // 先组合前5个flow的中间状态
    private data class FirstFiveState(
        val pending: Int,
        val counts: Map<ColorCategory, Int>,
        val photos: List<PhotoEntity>,
        val selected: Set<ColorCategory>,
        val query: String,
        val searching: Boolean,
        val isFav: Boolean,
        val analyzing: Set<Long>,
        val refreshing: Boolean
    )

    // 先组合前4个flow
    private val firstFourFlow = combine(
        dao.observeUnanalyzedCount(),
        dao.observeCategoryCounts().map { rows ->
            rows.mapNotNull { row ->
                runCatching { ColorCategory.valueOf(row.colorCategory) to row.total }.getOrNull()
            }.toMap()
        },
        photosFlow,
        searchAndCategoryFlow
    ) { pending, counts, photos, searchCat ->
        FirstFourState(pending, counts, photos, searchCat.first, searchCat.second, searchCat.third, searchCat.fourth)
    }

    // 再组合剩下的flow
    private data class FirstFourState(
        val pending: Int,
        val counts: Map<ColorCategory, Int>,
        val photos: List<PhotoEntity>,
        val selected: Set<ColorCategory>,
        val query: String,
        val searching: Boolean,
        val isFav: Boolean
    )

    // 先组合 analyzingMediaIds 和 isRefreshing
    private val analyzingAndRefreshFlow = combine(analyzingMediaIds, isRefreshing) { analyzing, refreshing ->
        Pair(analyzing, refreshing)
    }

    // 然后组合 firstFourFlow 和 analyzingAndRefreshFlow
    private val firstFiveFlow = combine(firstFourFlow, analyzingAndRefreshFlow) { firstFour, analyzingRefresh ->
        FirstFiveState(
            firstFour.pending,
            firstFour.counts,
            firstFour.photos,
            firstFour.selected,
            firstFour.query,
            firstFour.searching,
            firstFour.isFav,
            analyzingRefresh.first,
            analyzingRefresh.second
        )
    }

    // 再和favoritesVersion组合
    private val combinedFlow = combine(firstFiveFlow, favoritesVersion) { firstFive, favVer ->
        CombinedState(
            firstFive.pending,
            firstFive.counts,
            firstFive.photos,
            firstFive.selected,
            firstFive.query,
            firstFive.searching,
            firstFive.analyzing,
            firstFive.isFav,
            firstFive.refreshing,
            favVer
        )
    }

    val uiState: StateFlow<AlbumUiState> = combine(combinedFlow, collapsedMonths) { combined, collapsed ->
        // 按年月分组
        val monthGroups = combined.photos
            .groupBy { photo -> photo.getYearMonth() }
            .map { (yearMonth, monthPhotos) ->
                val displayName = monthPhotos.firstOrNull()?.getYearMonthDisplay() ?: yearMonth
                MonthGroup(
                    yearMonth = yearMonth,
                    displayName = displayName,
                    photos = monthPhotos
                )
            }
            .sortedByDescending { group -> group.yearMonth }

        AlbumUiState(
            pendingCount = combined.pending,
            categoryCounts = combined.counts,
            photos = combined.photos,
            monthGroups = monthGroups,
            collapsedMonths = collapsed,
            selectedCategories = combined.selected,
            searchQuery = combined.query,
            isSearching = combined.searching,
            analyzingMediaIds = combined.analyzing,
            isFavoritesOnly = combined.isFav,
            favoritesVersion = combined.favVersion,
            isRefreshing = combined.refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState())

    fun updateTags(photoId: Long, tags: String?) {
        viewModelScope.launch {
            dao.updateTags(photoId, tags)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSearching(searching: Boolean) {
        isSearching.value = searching
    }

    fun toggleCategory(category: ColorCategory) {
        selectedCategories.update { current ->
            if (current.contains(category)) {
                current - category
            } else {
                current + category
            }
        }
    }

    fun clearCategorySelection() {
        selectedCategories.value = emptySet()
    }

    fun toggleMonthCollapse(yearMonth: String) {
        collapsedMonths.update { current ->
            if (current.contains(yearMonth)) {
                current - yearMonth
            } else {
                current + yearMonth
            }
        }
    }

    // 收藏功能
    fun toggleFavorite(photoId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            dao.updateFavorite(photoId, isFavorite)
            favoritesVersion.update { it + 1 }
        }
    }

    fun addToFavorites(photoIds: List<Long>) {
        viewModelScope.launch {
            dao.updateFavorites(photoIds, true)
            favoritesVersion.update { it + 1 }
        }
    }

    fun removeFromFavorites(photoIds: List<Long>) {
        viewModelScope.launch {
            dao.updateFavorites(photoIds, false)
            favoritesVersion.update { it + 1 }
        }
    }

    fun toggleFavoritesFilter() {
        isFavoritesOnly.update { !it }
    }

    fun setFavoritesFilter(isFav: Boolean) {
        isFavoritesOnly.value = isFav
    }

    fun syncGalleryOnly() {
        viewModelScope.launch {
            GalleryScanner.syncPhotos(getApplication(), dao)
        }
    }

    /**
     * 在前台（应用进程内）分析单张，不使用 WorkManager。
     */
    fun analyzePhoto(photo: PhotoEntity) {
        if (photo.analyzed) return
        val id = photo.mediaId
        if (analyzingMediaIds.value.contains(id)) return
        viewModelScope.launch {
            analyzePhotoInternal(photo)
        }
    }

    /**
     * 一键分析指定数量的未分析照片
     */
    fun analyzeAllPhotos(count: Int = 50) {
        viewModelScope.launch {
            val unanalyzedPhotos = dao.loadUnanalyzed(count)
            for (photo in unanalyzedPhotos) {
                if (!analyzingMediaIds.value.contains(photo.mediaId)) {
                    analyzePhotoInternal(photo)
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    private suspend fun analyzePhotoInternal(photo: PhotoEntity) {
        val id = photo.mediaId
        analyzingMediaIds.update { it + id }
        try {
            val ctx = getApplication<Application>()
            val uri = Uri.parse(photo.uriString)
            val bmp = BitmapLoader.loadThumbnail(ctx, uri)
            if (bmp == null) {
                dao.update(
                    photo.copy(
                        analyzed = true,
                        colorCategory = ColorCategory.UNKNOWN.name,
                        dominantColorHex = null,
                        color1Hex = null,
                        color2Hex = null,
                        color3Hex = null,
                        color1Category = null,
                        color2Category = null,
                        color3Category = null,
                        analyzedAtMs = System.currentTimeMillis()
                    )
                )
                return
            }
            val result = try {
                ColorAnalyzer.analyze(bmp)
            } catch (_: Exception) {
                dao.update(
                    photo.copy(
                        analyzed = true,
                        colorCategory = ColorCategory.UNKNOWN.name,
                        dominantColorHex = null,
                        color1Hex = null,
                        color2Hex = null,
                        color3Hex = null,
                        color1Category = null,
                        color2Category = null,
                        color3Category = null,
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
                photo.copy(
                    analyzed = true,
                    colorCategory = result.category.name,
                    dominantColorHex = color1Hex,
                    color1Hex = color1Hex,
                    color2Hex = color2Hex,
                    color3Hex = color3Hex,
                    color1Category = color1Category,
                    color2Category = color2Category,
                    color3Category = color3Category,
                    analyzedAtMs = System.currentTimeMillis()
                )
            )
        } finally {
            analyzingMediaIds.update { it - id }
        }
    }
}