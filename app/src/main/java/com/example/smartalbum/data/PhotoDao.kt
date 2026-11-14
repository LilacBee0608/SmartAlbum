package com.example.smartalbum.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getByMediaId(mediaId: Long): PhotoEntity?

    @Query(
        """
        SELECT * FROM photos 
        WHERE analyzed = 0 
        ORDER BY dateAddedSec DESC 
        LIMIT :limit
        """
    )
    suspend fun loadUnanalyzed(limit: Int): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos WHERE analyzed = 0")
    suspend fun countUnanalyzed(): Int

    @Query("SELECT COUNT(*) FROM photos WHERE analyzed = 0")
    fun observeUnanalyzedCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM photos 
        WHERE analyzed = 1 AND colorCategory = :category 
        ORDER BY dateAddedSec DESC
        """
    )
    fun observeByCategory(category: String): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos 
        WHERE analyzed = 1 AND (color1Category = :category OR color2Category = :category OR color3Category = :category)
        ORDER BY dateAddedSec DESC
        """
    )
    fun observeByAnyCategory(category: String): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos 
        WHERE analyzed = 1
        AND (
            (:categoriesCount = 1 AND (color1Category IN (:categories) OR color2Category IN (:categories) OR color3Category IN (:categories))) OR
            (:categoriesCount = 2 AND ((color1Category IN (:categories) AND color2Category IN (:categories)) OR 
                                    (color1Category IN (:categories) AND color3Category IN (:categories)) OR 
                                    (color2Category IN (:categories) AND color3Category IN (:categories)))) OR
            (:categoriesCount = 3 AND color1Category IN (:categories) AND color2Category IN (:categories) AND color3Category IN (:categories))
        )
        ORDER BY dateAddedSec DESC
        """
    )
    fun observeByMultipleCategories(categories: List<String>, categoriesCount: Int = categories.size): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE analyzed = 1 ORDER BY dateAddedSec DESC")
    fun observeAllAnalyzed(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY dateAddedSec DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT DISTINCT colorCategory FROM (
            SELECT color1Category as colorCategory FROM photos WHERE analyzed = 1 AND color1Category IS NOT NULL
            UNION
            SELECT color2Category as colorCategory FROM photos WHERE analyzed = 1 AND color2Category IS NOT NULL
            UNION
            SELECT color3Category as colorCategory FROM photos WHERE analyzed = 1 AND color3Category IS NOT NULL
        ) AS all_categories
        """
    )
    fun observeDistinctCategories(): Flow<List<String>>

    @Query(
        """
        SELECT colorCategory, COUNT(*) AS total FROM (
            SELECT DISTINCT mediaId, color1Category as colorCategory FROM photos WHERE analyzed = 1 AND color1Category IS NOT NULL
            UNION
            SELECT DISTINCT mediaId, color2Category as colorCategory FROM photos WHERE analyzed = 1 AND color2Category IS NOT NULL
            UNION
            SELECT DISTINCT mediaId, color3Category as colorCategory FROM photos WHERE analyzed = 1 AND color3Category IS NOT NULL
        ) AS all_categories
        GROUP BY colorCategory
        """
    )
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>

    @Query("SELECT mediaId FROM photos")
    suspend fun getAllMediaIds(): List<Long>

    @Query("DELETE FROM photos WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByMediaIds(mediaIds: List<Long>)

    @Query("UPDATE photos SET tags = :tags WHERE id = :photoId")
    suspend fun updateTags(photoId: Long, tags: String?)

    @Query(
        """
        SELECT * FROM photos 
        WHERE analyzed = 1 AND tags LIKE '%' || :tag || '%'
        ORDER BY dateAddedSec DESC
        """
    )
    fun observeByTag(tag: String): Flow<List<PhotoEntity>>

    // 收藏功能
    @Query("UPDATE photos SET isFavorite = :isFavorite WHERE id = :photoId")
    suspend fun updateFavorite(photoId: Long, isFavorite: Boolean)

    @Query("UPDATE photos SET isFavorite = :isFavorite WHERE id IN (:photoIds)")
    suspend fun updateFavorites(photoIds: List<Long>, isFavorite: Boolean)

    @Query("SELECT * FROM photos WHERE isFavorite = 1 ORDER BY dateAddedSec DESC")
    fun observeFavorites(): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos 
        WHERE isFavorite = 1 
        AND (
            (:categoriesCount = 0) OR
            (:categoriesCount = 1 AND (color1Category IN (:categories) OR color2Category IN (:categories) OR color3Category IN (:categories))) OR
            (:categoriesCount = 2 AND ((color1Category IN (:categories) AND color2Category IN (:categories)) OR 
                                    (color1Category IN (:categories) AND color3Category IN (:categories)) OR 
                                    (color2Category IN (:categories) AND color3Category IN (:categories)))) OR
            (:categoriesCount = 3 AND color1Category IN (:categories) AND color2Category IN (:categories) AND color3Category IN (:categories))
        )
        ORDER BY dateAddedSec DESC
        """
    )
    fun observeFavoritesByCategories(categories: List<String>, categoriesCount: Int = categories.size): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos 
        WHERE isFavorite = 1 AND tags LIKE '%' || :tag || '%'
        ORDER BY dateAddedSec DESC
        """
    )
    fun observeFavoritesByTag(tag: String): Flow<List<PhotoEntity>>
}
