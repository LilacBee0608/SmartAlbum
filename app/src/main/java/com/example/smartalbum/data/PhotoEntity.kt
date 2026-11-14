package com.example.smartalbum.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["mediaId"], unique = true),
        Index(value = ["analyzed"]),
        Index(value = ["colorCategory"]),
        Index(value = ["tags"]),
        Index(value = ["isFavorite"])
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val uriString: String,
    val dateAddedSec: Long,
    val analyzed: Boolean = false,
    val colorCategory: String? = null,
    val dominantColorHex: String? = null,
    val color1Hex: String? = null,  // 主要颜色1
    val color2Hex: String? = null,  // 主要颜色2
    val color3Hex: String? = null,  // 主要颜色3
    val color1Category: String? = null,  // 主要颜色1的分类
    val color2Category: String? = null,  // 主要颜色2的分类
    val color3Category: String? = null,  // 主要颜色3的分类
    val tags: String? = null,  // 图片标签，以逗号分隔
    val analyzedAtMs: Long? = null,
    val isFavorite: Boolean = false  // 收藏标记
) {
    fun getYearMonth(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return sdf.format(Date(dateAddedSec * 1000))
    }
    
    fun getYearMonthDisplay(): String {
        val sdf = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
        return sdf.format(Date(dateAddedSec * 1000))
    }
}

data class MonthGroup(
    val yearMonth: String,
    val displayName: String,
    val photos: List<PhotoEntity>
)
