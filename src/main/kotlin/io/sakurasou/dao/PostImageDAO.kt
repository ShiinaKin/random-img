package io.sakurasou.dao

import io.sakurasou.config.SnowFlakeIdGenerator
import io.sakurasou.entity.PostImageDTO
import io.sakurasou.entity.PostImageDeleteByOriginDTO
import io.sakurasou.entity.PostImageQueryByPostIdDTO
import io.sakurasou.entity.PostImages
import io.sakurasou.entity.PostImages.deleted
import io.sakurasou.entity.PostImages.imageId
import io.sakurasou.entity.PostImages.origin
import io.sakurasou.entity.PostImages.postId
import io.sakurasou.entity.PostImages.queryCondition
import io.sakurasou.entity.PostImages.url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * @author mashirot
 * 2024/5/21 21:11
 */
@Repository
class PostImageDAO(
    private val database: Database,
    private val idGenerator: SnowFlakeIdGenerator
) {

    suspend fun insert(postImageDTO: PostImageDTO) {
        withContext(Dispatchers.IO) {
            database.insert(PostImages) {
                val now = LocalDateTime.now()
                set(it.id, idGenerator.generate())
                set(it.origin, postImageDTO.origin)
                set(it.postId, postImageDTO.postId)
                set(it.imageId, postImageDTO.imageId)
                set(it.queryCondition, postImageDTO.queryCondition)
                set(it.url, postImageDTO.url)
                set(it.createTime, now)
                set(it.updateTime, now)
                set(it.deleted, false)
            }
        }
    }

    suspend fun deleteByOrigin(postImageDeleteByOriginDTO: PostImageDeleteByOriginDTO) {
        withContext(Dispatchers.IO) {
            database.update(PostImages) {
                set(it.deleted, true)
                set(it.updateTime, LocalDateTime.now())
                where { it.origin eq postImageDeleteByOriginDTO.origin }
                where { it.deleted eq false }
            }
        }
    }

    suspend fun deleteByImageId(imageId: Long) {
        withContext(Dispatchers.IO) {
            database.update(PostImages) {
                set(it.deleted, true)
                set(it.updateTime, LocalDateTime.now())
                where { it.imageId eq imageId }
                where { it.deleted eq false }
            }
        }
    }

    suspend fun deleteByImageIds(ids: List<Long>) {
        withContext(Dispatchers.IO) {
            database.batchUpdate(PostImages) {
                ids.map { id ->
                    item {
                        set(it.deleted, true)
                        set(it.updateTime, LocalDateTime.now())
                        where { it.imageId eq id }
                        where { it.deleted eq false }
                    }
                }
            }
        }
    }

    suspend fun selectImageByPostId(postImageQueryByPostIdDTO: PostImageQueryByPostIdDTO): PostImageDTO? {
        return withContext(Dispatchers.IO) {
            database.from(PostImages).select(origin, postId, imageId, queryCondition, url)
                .where(origin eq postImageQueryByPostIdDTO.origin)
                .where(postId eq postImageQueryByPostIdDTO.postId)
                .where(deleted eq false)
                .asIterable().firstOrNull()
                ?.let {
                    PostImageDTO(
                        it[origin]!!,
                        it[postId]!!,
                        it[imageId]!!,
                        it[queryCondition]!!,
                        it[url]!!
                    )
                }
        }
    }
}