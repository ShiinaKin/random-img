package io.sakurasou.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.dao.ImageDAO
import io.sakurasou.dao.PostImageDAO
import io.sakurasou.entity.*
import io.sakurasou.exception.ImageFetchException
import kotlinx.coroutines.runBlocking
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.*
import kotlin.math.absoluteValue

/**
 * @author mashirot
 * 2024/5/21 20:02
 */
@Service
class ImageService(
    private val imageDAO: ImageDAO,
    private val postImageDAO: PostImageDAO,
    private val s3Service: S3Service,
    private val redisTemplate: StringRedisTemplate,
    private val config: RandomImgConfig
) {

    private val logger = KotlinLogging.logger { this::class.java }
    private val imageDeleteThreadPool =
        ThreadPoolExecutor(
            1, 2, 1, TimeUnit.MINUTES, LinkedBlockingQueue(),
            { Thread(it, "async-image-upload-thread") }, ThreadPoolExecutor.AbortPolicy()
        )

    suspend fun batchInsertImage(list: List<ImageDTO>) {
        imageDAO.batchInsert(list)
    }

    suspend fun deleteImage(deleteDTO: ImageDeleteDTO) {
        imageDeleteThreadPool.submit {
            runBlocking {
                val needDeletedImages = imageDAO.selectImageByIdOrUid(deleteDTO)
                imageDAO.deleteImageByIds(needDeletedImages)
                s3Service.deleteFileFromS3(needDeletedImages)
            }
        }
    }

    suspend fun selectImage(query: ImageQuery): String {
        val defaultExpire = Duration.ofHours(3)
        val key = "random_img:select:${query.id}:${query.queryCondition}"
        return redisTemplate.opsForValue().get(key) ?: run {
            logger.debug { "select img id: ${query.id}, cache miss" }
            val imageDTO = imageDAO.selectImage(ImageQueryDTO(query.id)) ?: throw ImageFetchException("no such image")
            val url = chooseSimilarSize(imageDTO, query.queryConditionMap, query.queryCondition)
            redisTemplate.opsForValue().set(key, url, defaultExpire)
            url
        }
    }

    suspend fun randomSelectImage(query: ImageRandomQuery): String {
        val defaultExpire = Duration.ofHours(3)
        val randomQueryDTO = ImageRandomQueryDTO(query.uid)
        if (query.postID.isNullOrBlank()) {
            logger.debug { "random img, not taken referer and postId" }
            val imageDTO = imageDAO.randomSelImage(randomQueryDTO) ?: throw ImageFetchException("no such image")
            logger.info { "random img, origin: ${query.origin} fetch random image successfully, imgId: ${imageDTO.id}" }
            return chooseSimilarSize(imageDTO, query.queryConditionMap, query.queryCondition)
        }
        val key = "random_img:random:${query.origin}:${query.postID}:${query.queryCondition}"
        logger.debug { "random img, taken referer and post id, redis key: $key" }
        return redisTemplate.opsForValue().get(key) ?: run {
            logger.debug { "random img, cache miss, key: $key" }
            postImageDAO.selectImageByPostId(
                PostImageQueryByPostIdDTO(
                    query.origin,
                    query.postID,
                    query.queryCondition
                )
            )?.url ?: run {
                logger.debug { "random img, database miss, key: $key" }
                val imageDTO = imageDAO.randomSelImage(randomQueryDTO) ?: throw ImageFetchException("no such image")
                logger.info {
                    "random img, origin: ${query.origin} postId: ${query.postID} " +
                            "fetch random image successfully, imgId: ${imageDTO.id}"
                }
                val url = chooseSimilarSize(imageDTO, query.queryConditionMap, query.queryCondition)
                if (config.persistenceReferer.contains(query.origin)) {
                    postImageDAO.insert(
                        PostImageDTO(
                            query.origin,
                            query.postID,
                            imageDTO.id!!,
                            query.queryCondition,
                            url
                        )
                    )
                    logger.debug { "random img, persistence success" }
                }
                redisTemplate.opsForValue().set(key, url, defaultExpire)
                url
            }
        }
    }

    private fun chooseSimilarSize(
        imageDTO: ImageDTO,
        queryConditionMap: Map<String, Any?>,
        queryCondition: String
    ): String {
        var url = imageDTO.authority + "/"
        val th = queryConditionMap["th"]?.toString()?.toInt()
        url += if (th != null) {
            val i1 = (th - imageDTO.originalWidth).absoluteValue
            val i2 = (th - ImageSize.MEDIUM_SIZE.size).absoluteValue
            val i3 = (th - ImageSize.MINIMAL_SIZE.size).absoluteValue
            if (i2 == 0) imageDTO.mediumSizePath
            else if (i3 == 0) imageDTO.minimalSizePath
            else when {
                i1 <= i2 && i1 <= i3 -> "${imageDTO.originalSizePath}?$queryCondition"
                i2 <= i1 && i2 <= i3 -> "${imageDTO.mediumSizePath}?$queryCondition"
                else -> "${imageDTO.minimalSizePath}?$queryCondition"
            }
        } else imageDTO.mediumSizePath
        return url
    }

}