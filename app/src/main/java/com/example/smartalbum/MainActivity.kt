package com.example.smartalbum

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smartalbum.data.ColorCategory
import com.example.smartalbum.data.MonthGroup
import com.example.smartalbum.data.PhotoEntity
import com.example.smartalbum.ui.PhotoViewModel
import com.example.smartalbum.ui.theme.SmartAlbumTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartAlbumTheme {
                val vm: PhotoViewModel = viewModel()
                val context = LocalContext.current
                var viewerUri by remember { mutableStateOf<String?>(null) }

                val permissionContract = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted ->
                    if (granted.values.any { it }) {
                        vm.syncGalleryOnly()
                    }
                }

                LaunchedEffect(Unit) {
                    val perms = imageReadPermissions()
                    val allOk = perms.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allOk) {
                        vm.syncGalleryOnly()
                    } else {
                        permissionContract.launch(perms)
                    }
                }

                val state by vm.uiState.collectAsState()

                viewerUri?.let { uriStr ->
                    FullImageDialog(uriString = uriStr, onDismiss = { viewerUri = null })
                }

                var showAnalysisDialog by remember { mutableStateOf(false) }

                if (showAnalysisDialog) {
                    var analysisCount by remember { mutableStateOf(100f) }
                    var analysisCountInput by remember { mutableStateOf("1") }
                    val maxCount = state.pendingCount.coerceAtLeast(1)
                    
                    Dialog(
                        onDismissRequest = { showAnalysisDialog = false }
                    ) {
                        Card(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "一键分析",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = "这可能会耗费不少时间，请少量多次分析",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = "当前未分析: ${state.pendingCount} 张",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                                
                                Text(
                                    text = "选择分析数量",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                Slider(
                                    value = analysisCount,
                                    onValueChange = { 
                                        analysisCount = it
                                        analysisCountInput = it.toInt().toString()
                                    },
                                    valueRange = 1f..maxCount.toFloat(),
                                    steps = if (maxCount > 1) maxCount - 1 else 0,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "数量: ",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextField(
                                        value = analysisCountInput,
                                        onValueChange = { input ->
                                            val num = input.toIntOrNull()
                                            if (num != null && num >= 1 && num <= maxCount) {
                                                analysisCountInput = input
                                                analysisCount = num.toFloat()
                                            } else if (input.isEmpty()) {
                                                analysisCountInput = input
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.width(100.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                    Text(
                                        text = " / $maxCount",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { showAnalysisDialog = false }
                                    ) {
                                        Text("取消")
                                    }
                                    TextButton(
                                        onClick = {
                                            val count = analysisCountInput.toIntOrNull() ?: analysisCount.toInt()
                                            vm.analyzeAllPhotos(count.coerceIn(1, maxCount))
                                            showAnalysisDialog = false
                                        }
                                    ) {
                                        Text("开始分析")
                                    }
                                }
                            }
                        }
                    }
                }

                var showTagDialog by remember { mutableStateOf(false) }
                var selectedPhoto by remember { mutableStateOf<PhotoEntity?>(null) }
                var tagInput by remember { mutableStateOf("") }

                // 多选功能
                var isMultiSelectMode by remember { mutableStateOf(false) }
                var selectedPhotos by remember { mutableStateOf<Set<PhotoEntity>>(emptySet()) }

                if (showTagDialog && selectedPhoto != null) {
                    // 确保每次显示对话框时都使用最新的标签值
                    tagInput = selectedPhoto!!.tags ?: ""
                    AlertDialog(
                        onDismissRequest = { showTagDialog = false },
                        title = { Text("编辑标签") },
                        text = {
                            TextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                placeholder = { Text("输入标签，以逗号分隔") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    selectedPhoto?.let { photo ->
                                        vm.updateTags(photo.id, tagInput)
                                    }
                                    showTagDialog = false
                                }
                            ) {
                                Text("保存")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showTagDialog = false }
                            ) {
                                Text("取消")
                            }
                        }
                    )
                }

                // 长按弹出菜单
                var showLongPressMenu by remember { mutableStateOf(false) }
                var longPressedMediaId by remember { mutableStateOf<Long?>(null) }

                // 根据长按的媒体ID，从当前照片列表中获取最新状态
                // 依赖于 mediaId 和 favoritesVersion，确保收藏状态变化时能获取最新数据
                val currentLongPressedPhoto: PhotoEntity? = remember(longPressedMediaId, state.favoritesVersion) {
                    longPressedMediaId?.let { mediaId ->
                        state.photos.find { it.mediaId == mediaId }
                    }
                }

                if (showLongPressMenu && currentLongPressedPhoto != null) {
                    AlertDialog(
                        onDismissRequest = { showLongPressMenu = false },
                        title = { Text("选择操作") },
                        text = { Text("请选择要执行的操作") },
                        confirmButton = {
                            Column {
                                TextButton(
                                    onClick = {
                                        selectedPhoto = currentLongPressedPhoto
                                        tagInput = currentLongPressedPhoto?.tags ?: ""
                                        showTagDialog = true
                                        showLongPressMenu = false
                                    }
                                ) {
                                    Text("编辑标签")
                                }
                                TextButton(
                                    onClick = {
                                        currentLongPressedPhoto?.let { photo ->
                                            val newFavoriteState = !photo.isFavorite
                                            vm.toggleFavorite(photo.id, newFavoriteState)
                                        }
                                        showLongPressMenu = false
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (currentLongPressedPhoto?.isFavorite == true) {
                                                Icons.Default.Favorite
                                            } else {
                                                Icons.Default.FavoriteBorder
                                            },
                                            contentDescription = if (currentLongPressedPhoto?.isFavorite == true) "取消收藏" else "收藏",
                                            tint = if (currentLongPressedPhoto?.isFavorite == true) {
                                                Color.Red
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.padding(4.dp))
                                        // 已收藏的图片显示"取消收藏"，未收藏的显示"收藏"
                                        Text(if (currentLongPressedPhoto?.isFavorite == true) "取消收藏" else "收藏")
                                    }
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    isMultiSelectMode = true
                                    currentLongPressedPhoto?.let { photo ->
                                        selectedPhotos = setOf(photo)
                                    }
                                    showLongPressMenu = false
                                }
                            ) {
                                Text("多选")
                            }
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isMultiSelectMode) {
                                    Text("已选择 ${selectedPhotos.size} 项")
                                } else if (state.isSearching) {
                                    TextField(
                                        value = state.searchQuery,
                                        onValueChange = { vm.setSearchQuery(it) },
                                        placeholder = { Text("搜索标签...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                } else {
                                    // 显示应用Logo图片
                                    Image(
                                        painter = painterResource(id = R.drawable.app_logo),
                                        contentDescription = "应用Logo",
                                        modifier = Modifier.height(48.dp)
                                    )
                                }
                            },
                            actions = {
                                if (isMultiSelectMode) {
                                    // 多选模式下的操作
                                    IconButton(
                                        onClick = {
                                            // 分享选中的图片
                                            if (selectedPhotos.isNotEmpty()) {
                                                val uris = selectedPhotos.map { Uri.parse(it.uriString) }
                                                val shareIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND_MULTIPLE
                                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                                    type = "image/*"
                                                }
                                                startActivity(Intent.createChooser(shareIntent, "分享图片"))
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "分享")
                                    }
                                    // 收藏按钮
                                    IconButton(
                                        onClick = {
                                            if (selectedPhotos.isNotEmpty()) {
                                                val photoIds = selectedPhotos.map { it.id }
                                                vm.addToFavorites(photoIds)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = "收藏",
                                            tint = Color.Red
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isMultiSelectMode = false
                                            selectedPhotos = emptySet()
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "取消选择")
                                    }
                                } else if (!state.isSearching) {
                                    // 收藏夹筛选按钮
                                    IconButton(
                                        onClick = {
                                            vm.toggleFavoritesFilter()
                                        }
                                    ) {
                                        Icon(
                                            if (state.isFavoritesOnly) {
                                                Icons.Default.Favorite
                                            } else {
                                                Icons.Default.FavoriteBorder
                                            },
                                            contentDescription = "收藏夹",
                                            tint = if (state.isFavoritesOnly) {
                                                Color.Red
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            vm.setSearching(true)
                                        }
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "搜索")
                                    }
                                    if (state.pendingCount > 0) {
                                        TextButton(
                                            onClick = { showAnalysisDialog = true }
                                        ) {
                                            Text("一键分析")
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            vm.setSearching(false)
                                            vm.setSearchQuery("")
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "取消搜索")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        Text(
                            text = "未分析：${state.pendingCount} 张 · 点击缩略图查看原图",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        CategoryRow(
                            counts = state.categoryCounts,
                            selected = state.selectedCategories,
                            onToggle = { vm.toggleCategory(it) },
                            onClear = { vm.clearCategorySelection() }
                        )
                        PhotoGrid(
                            photos = state.photos,
                            monthGroups = state.monthGroups,
                            collapsedMonths = state.collapsedMonths,
                            selectedCategories = state.selectedCategories,
                            analyzingIds = state.analyzingMediaIds,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedPhotos = selectedPhotos,
                            onAnalyze = { vm.analyzePhoto(it) },
                            onOpenImage = { viewerUri = it },
                            onLongClick = { photo ->
                                if (isMultiSelectMode) {
                                    // 在多选模式下，长按也用于选择
                                    selectedPhotos = if (selectedPhotos.contains(photo)) {
                                        selectedPhotos - photo
                                    } else {
                                        selectedPhotos + photo
                                    }
                                } else {
                                    // 显示长按菜单，只保存媒体ID
                                    longPressedMediaId = photo.mediaId
                                    showLongPressMenu = true
                                }
                            },
                            onPhotoClick = { photo ->
                                // 在多选模式下，点击切换选择状态
                                selectedPhotos = if (selectedPhotos.contains(photo)) {
                                    selectedPhotos - photo
                                } else {
                                    selectedPhotos + photo
                                }
                            },
                            onToggleMonthCollapse = { vm.toggleMonthCollapse(it) },
                            isRefreshing = state.isRefreshing,
                            onRefresh = { vm.refreshGallery() },
                            onToggleFavorite = { photo, isFavorite ->
                                vm.toggleFavorite(photo.id, isFavorite)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun imageReadPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@Composable
private fun CategoryRow(
    counts: Map<ColorCategory, Int>,
    selected: Set<ColorCategory>,
    onToggle: (ColorCategory) -> Unit,
    onClear: () -> Unit
) {
    val orderedCategories = ColorCategory.entries
        .filter { it != ColorCategory.UNKNOWN }
        .sortedByDescending { counts[it] ?: 0 }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected.isEmpty(),
                onClick = onClear,
                label = { Text("全部") }
            )
        }
        items(orderedCategories, key = { it.name }) { cat ->
            val n = counts[cat] ?: 0
            FilterChip(
                selected = selected.contains(cat),
                onClick = { onToggle(cat) },
                label = { Text("${cat.labelZh} ($n)") }
            )
        }
        val unknownN = counts[ColorCategory.UNKNOWN] ?: 0
        if (unknownN > 0) {
            item {
                FilterChip(
                    selected = selected.contains(ColorCategory.UNKNOWN),
                    onClick = { onToggle(ColorCategory.UNKNOWN) },
                    label = { Text("${ColorCategory.UNKNOWN.labelZh} ($unknownN)") }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun SwipeRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberPullRefreshState(isRefreshing, onRefresh)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        content()
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PhotoGrid(
    photos: List<PhotoEntity>,
    monthGroups: List<MonthGroup>,
    collapsedMonths: Set<String>,
    selectedCategories: Set<ColorCategory>,
    analyzingIds: Set<Long>,
    isMultiSelectMode: Boolean,
    selectedPhotos: Set<PhotoEntity>,
    onAnalyze: (PhotoEntity) -> Unit,
    onOpenImage: (String) -> Unit,
    onLongClick: (PhotoEntity) -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    onToggleMonthCollapse: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: ((PhotoEntity, Boolean) -> Unit)? = null
) {
    if (photos.isEmpty()) {
        val emptyHint = if (selectedCategories.isEmpty()) {
            "相册中暂无照片，请授予读取权限后等待同步"
        } else {
            "没有同时包含所选颜色的照片，可切换其他颜色组合"
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 8.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    SwipeRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            monthGroups.forEach { monthGroup ->
                item(key = "header_${monthGroup.yearMonth}") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = monthGroup.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { onToggleMonthCollapse(monthGroup.yearMonth) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (collapsedMonths.contains(monthGroup.yearMonth)) {
                                    Icons.Default.ExpandMore
                                } else {
                                    Icons.Default.ExpandLess
                                },
                                contentDescription = if (collapsedMonths.contains(monthGroup.yearMonth)) "展开" else "折叠",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!collapsedMonths.contains(monthGroup.yearMonth)) {
                    item(key = "photos_${monthGroup.yearMonth}") {
                        PhotoGridContent(
                            photos = monthGroup.photos,
                            analyzingIds = analyzingIds,
                            isMultiSelectMode = isMultiSelectMode,
                            selectedPhotos = selectedPhotos,
                            onAnalyze = onAnalyze,
                            onOpenImage = onOpenImage,
                            onLongClick = onLongClick,
                            onPhotoClick = onPhotoClick,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridContent(
    photos: List<PhotoEntity>,
    analyzingIds: Set<Long>,
    isMultiSelectMode: Boolean,
    selectedPhotos: Set<PhotoEntity>,
    onAnalyze: (PhotoEntity) -> Unit,
    onOpenImage: (String) -> Unit,
    onLongClick: (PhotoEntity) -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    onToggleFavorite: ((PhotoEntity, Boolean) -> Unit)? = null
) {
    val chunks = photos.chunked(4)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chunks.forEachIndexed { rowIndex, rowPhotos ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowPhotos.forEachIndexed { colIndex, photo ->
                        key(photo.mediaId) {
                            PhotoCard(
                                photo = photo,
                                analyzing = analyzingIds.contains(photo.mediaId),
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = selectedPhotos.contains(photo),
                                onAnalyze = onAnalyze,
                                onOpenImage = onOpenImage,
                                onLongClick = { onLongClick(photo) },
                                onPhotoClick = { onPhotoClick(photo) },
                                onToggleFavorite = onToggleFavorite,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    repeat(4 - rowPhotos.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoEntity,
    analyzing: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onAnalyze: (PhotoEntity) -> Unit,
    onOpenImage: (String) -> Unit,
    onLongClick: () -> Unit,
    onPhotoClick: (PhotoEntity) -> Unit,
    onToggleFavorite: ((PhotoEntity, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cat = photo.colorCategory?.let { runCatching { ColorCategory.valueOf(it) }.getOrNull() }

    // 解析颜色值
    val color1 = photo.color1Hex?.let { Color(AndroidColor.parseColor(it)) }
    val color2 = photo.color2Hex?.let { Color(AndroidColor.parseColor(it)) }
    val color3 = photo.color3Hex?.let { Color(AndroidColor.parseColor(it)) }

    // 解析颜色分类
    val color1Category = photo.color1Category?.let { runCatching { ColorCategory.valueOf(it) }.getOrNull() }
    val color2Category = photo.color2Category?.let { runCatching { ColorCategory.valueOf(it) }.getOrNull() }
    val color3Category = photo.color3Category?.let { runCatching { ColorCategory.valueOf(it) }.getOrNull() }

    Column(
        modifier = modifier
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photo.uriString)
                        .crossfade(true)
                        .memoryCacheKey(photo.mediaId.toString())
                        .diskCacheKey(photo.mediaId.toString())
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(photo.mediaId, isMultiSelectMode) {
                            detectTapGestures(
                                onTap = {
                                    if (isMultiSelectMode) {
                                        // 在多选模式下，点击切换选择状态
                                        onPhotoClick(photo)
                                    } else {
                                        // 在普通模式下，点击打开原图
                                        onOpenImage(photo.uriString)
                                    }
                                },
                                onLongPress = { onLongClick() }
                            )
                        },
                    contentScale = ContentScale.Crop
                )

                // 选中状态的勾号
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选择",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // 收藏标识
                if (photo.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(Color.Red.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "已收藏",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (!photo.analyzed) {
                    Button(
                        onClick = { onAnalyze(photo) },
                        enabled = !analyzing,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.small,
                        elevation = ButtonDefaults.buttonElevation()
                    ) {
                        Text("分析", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (analyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 显示颜色色块和分类
        if (photo.analyzed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val colorInfo = listOf(
                    Pair(color1, color1Category),
                    Pair(color2, color2Category),
                    Pair(color3, color3Category)
                )
                colorInfo.forEach { (color: Color?, category: ColorCategory?) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color ?: Color.LightGray,
                                    MaterialTheme.shapes.small
                                )
                                .border(1.dp, Color.Gray.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                        ) {}
                        Text(
                            text = category?.labelZh ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullImageDialog(uriString: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(uriString))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
        }
    }
}