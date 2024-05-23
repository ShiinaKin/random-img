package io.sakurasou.dao

import io.sakurasou.config.RandomImgConfig
import io.sakurasou.config.SnowFlakeIdGenerator
import io.sakurasou.entity.*
import io.sakurasou.entity.Images.authority
import io.sakurasou.entity.Images.deleted
import io.sakurasou.entity.Images.id
import io.sakurasou.entity.Images.mediumSizePath
import io.sakurasou.entity.Images.minimalSizePath
import io.sakurasou.entity.Images.originalSizePath
import io.sakurasou.entity.Images.originalWidth
import io.sakurasou.entity.Images.pid
import io.sakurasou.entity.Images.uid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ktorm.database.Database
import org.ktorm.database.asIterable
import org.ktorm.dsl.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * @author mashirot
 * 2024/5/21 20:20
 */
@Repository
class ImageDAO(
    private val database: Database,
    private val idGenerator: SnowFlakeIdGenerator,
    private val postImageDAO: PostImageDAO,
    private val config: RandomImgConfig
) {

    suspend fun batchInsert(list: List<ImageDTO>) {
        withContext(Dispatchers.IO) {
            database.batchInsert(Images) {
                list.map { image ->
                    val now = LocalDateTime.now()
                    item {
                        set(it.id, idGenerator.generate())
                        set(it.uid, image.uid)
                        set(it.pid, image.pid)
                        set(it.authority, image.authority)
                        set(it.originalWidth, image.originalWidth)
                        set(it.originalSizePath, image.originalSizePath)
                        set(it.mediumSizePath, image.mediumSizePath)
                        set(it.minimalSizePath, image.minimalSizePath)
                        set(it.createTime, now)
                        set(it.updateTime, now)
                        set(it.deleted, false)
                    }
                }
            }
        }
    }

    @Transactional
    suspend fun deleteImageByIds(images: List<ImageDTO>) {
        withContext(Dispatchers.IO) {
            val ids = images.map { it.id!! }
            database.batchUpdate(Images) {
                ids.map { id ->
                    item {
                        set(it.deleted, true)
                        set(it.updateTime, LocalDateTime.now())
                        where { it.id eq id }
                    }
                }
            }
            postImageDAO.deleteByImageIds(ids)
        }
    }

    suspend fun selectImageByIdOrUid(deleteDTO: ImageDeleteDTO): List<ImageDTO> {
        return withContext(Dispatchers.IO) {
            database.from(Images).select(uid, pid, authority, originalWidth, originalSizePath, mediumSizePath, minimalSizePath, id)
                .where { deleted eq false }
                .where {
                    when {
                        deleteDTO.id != null -> id eq deleteDTO.id
                        deleteDTO.uid != null -> uid eq deleteDTO.uid
                        else -> throw WrongThreadException("must have id or uid")
                    }
                }
                .asIterable()
                .map {
                    ImageDTO(
                        it[uid]!!,
                        it[pid]!!,
                        it[authority]!!,
                        it[originalWidth]!!,
                        it[originalSizePath]!!,
                        it[mediumSizePath]!!,
                        it[minimalSizePath]!!,
                        it[id]
                    )
                }
        }
    }

    suspend fun selectImage(imageQueryDTO: ImageQueryDTO): ImageDTO? {
        return withContext(Dispatchers.IO) {
            database.from(Images)
                .select(uid, pid, authority, originalWidth, originalSizePath, mediumSizePath, minimalSizePath, id)
                .where { id eq imageQueryDTO.id }
                .asIterable().firstOrNull()
                ?.let {
                    ImageDTO(
                        it[uid]!!,
                        it[pid]!!,
                        it[authority]!!,
                        it[originalWidth]!!,
                        it[originalSizePath]!!,
                        it[mediumSizePath]!!,
                        it[minimalSizePath]!!,
                        it[id]
                    )
                }
        }
    }

    suspend fun randomSelImage(randomQueryDTO: ImageRandomQueryDTO): ImageDTO? {
        return withContext(Dispatchers.IO) {
            database.useConnection { connection ->
                val condSql = """
                    select
                        uid, pid, authority, original_width, original_size_path, medium_size_path, minimal_size_path, id
                    from images
                    where uid = ?
                        and rand() < ${config.probability} and is_deleted = 0
                    limit 1
                """.trimIndent()
                val noCondSql = """
                    select
                        uid, pid, authority, original_width, original_size_path, medium_size_path, minimal_size_path, id
                    from images
                    where rand() < ${config.probability} and is_deleted = 0
                    limit 1
                """.trimIndent()
                val sql = if (randomQueryDTO.uid != null) condSql else noCondSql
                connection.prepareStatement(sql).use { statement ->
                    if (randomQueryDTO.uid != null) statement.setLong(1, randomQueryDTO.uid)
                    statement.executeQuery().asIterable()
                        .firstOrNull()?.let {
                            ImageDTO(
                                it.getLong(1),
                                it.getString(2),
                                it.getString(3),
                                it.getInt(4),
                                it.getString(5),
                                it.getString(6),
                                it.getString(7),
                                it.getLong(8)
                            )
                        }
                }
            }
        }
    }

}