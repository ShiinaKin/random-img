package io.sakurasou.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.dao.ImageDAO
import io.sakurasou.dao.PostImageDAO
import io.sakurasou.entity.*
import io.sakurasou.exception.ImageFetchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.absoluteValue

/**
 * @author mashirot
 * 2024/5/21 20:02
 */
@Service
open class ImageService(
    private val imageDAO: ImageDAO,
    private val postImageDAO: PostImageDAO,
    private val s3Service: S3Service,
    private val redisTemplate: StringRedisTemplate,
    private val config: RandomImgConfig
) {

    private val logger = KotlinLogging.logger { this::class.java }
    private val imageDeleteThreadPool =
        ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
        ) { Thread(it, "image-delete-thread") }
    private val isDeleting = AtomicBoolean(false)

    suspend fun batchInsertImage(list: List<ImageDTO>) {
        imageDAO.batchInsert(list)
    }

    suspend fun deleteImage(deleteDTO: ImageDeleteDTO) {
        imageDeleteThreadPool.execute {
            runBlocking {
                isDeleting.set(true)
                val needDeletedImages = imageDAO.selectImageByIdOrUid(deleteDTO)
                    .ifEmpty { throw ImageFetchException("no such image") }
                imageDAO.deleteImageByIds(needDeletedImages)
                s3Service.deleteImageFromS3(needDeletedImages)
                isDeleting.set(false)
            }
        }
    }

    suspend fun deleteAllImage(token: String?) {
        val key = "random_img:clear"
        val defaultExpire = Duration.ofMinutes(2)
        fun generateToken() {
            val newToken = DigestUtils.md5Hex(UUID.randomUUID().toString())
            logger.info { "the new token: $newToken, it will expires in two minutes" }
            redisTemplate.opsForValue().set(key, newToken, defaultExpire)
        }
        if (token == null) {
            logger.info { "must taken token to exec clear" }
            generateToken()
            return
        }
        if (redisTemplate.opsForValue().get(key) != token) {
            logger.info { "wrong token, pls use it within the validity period of the token" }
            generateToken()
            return
        }
        redisTemplate.delete(key)
        if (isDeleting.get()) {
            logger.warn { "clearing is already being performed, please do not repeat request." }
            return
        }
        imageDeleteThreadPool.execute {
            runBlocking {
                isDeleting.set(true)
                imageDAO.deleteAllImagePhysically()
                s3Service.clearImageBucket()
                isDeleting.set(false)
            }
        }
    }

    @Transactional
    open suspend fun deleteDeletedRow() {
        imageDAO.physicalDeleteDeleted()
        postImageDAO.physicalDeleteDeleted()
    }

    suspend fun deleteImagePostMapping(origin: String, postId: String) {
        postImageDAO.deleteByOriginAndPostId(origin, postId)
        redisTemplate.keys("random_image:random:$origin:$postId:*").forEach {
            redisTemplate.delete(it)
        }
    }

    suspend fun clearSelectImageCache() {
        withContext(Dispatchers.IO) {
            redisTemplate.keys("random_img:select:*").forEach {
                redisTemplate.delete(it)
            }
            logger.debug { "select image cache clear" }
        }
    }

    suspend fun clearRandomImageCache() {
        withContext(Dispatchers.IO) {
            redisTemplate.keys("random_img:random:*").forEach {
                redisTemplate.delete(it)
            }
            logger.debug { "random image cache clear" }
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
        if (query.origin.isNullOrBlank() || query.postID.isNullOrBlank()) {
            val imageDTO = imageDAO.randomSelImage(randomQueryDTO) ?: throw ImageFetchException("no such image")
            logger.info { "random img, fetch random image successfully, imgId: ${imageDTO.id}" }
            return chooseSimilarSize(imageDTO, query.queryConditionMap, query.queryCondition)
        }
        val key = "random_img:random:${query.origin}:${query.postID}:${query.queryCondition}"
        logger.debug { "random img, taken referer and post id, redis key: $key" }
        return redisTemplate.opsForValue().get(key)?.let {
            logger.debug {
                "random img, origin: ${query.origin} postId: ${query.postID}, queryCondition: ${query.queryCondition} cache hit, " +
                        "imgUrl: $it"
            }
            it
        } ?: run {
            logger.debug { "random img, cache miss, key: $key" }
            postImageDAO.selectImageByPostId(
                PostImageQueryByPostIdDTO(
                    query.origin,
                    query.postID
                )
            )?.let { postImage ->
                logger.debug {
                    "random img, origin: ${query.origin} postId: ${query.postID}, database hit, " +
                            "imgId: ${postImage.imageId}"
                }
                imageDAO.selectImage(ImageQueryDTO(postImage.imageId))?.let {
                    val url = chooseSimilarSize(it, query.queryConditionMap, query.queryCondition)
                    redisTemplate.opsForValue().set(key, url, defaultExpire)
                    url
                } ?: run {
                    logger.warn {
                        "random img, origin: ${query.origin} postId: ${query.postID}, database hit, " +
                                "but image not found, imgId: ${postImage.imageId}"
                    }
                    postImageDAO.deleteByImageId(postImage.imageId)
                    throw ImageFetchException("no such image")
                }
            } ?: run {
                val imageDTO = imageDAO.randomSelImage(randomQueryDTO) ?: throw ImageFetchException("no such image")
                logger.debug {
                    "random img, origin: ${query.origin} postId: ${query.postID} " +
                            "database miss, fetch random image successfully, imgId: ${imageDTO.id}"
                }
                val url = chooseSimilarSize(imageDTO, query.queryConditionMap, query.queryCondition)
                if (config.persistenceReferer.contains(query.origin)) {
                    postImageDAO.insert(
                        PostImageDTO(
                            query.origin,
                            query.postID,
                            imageDTO.id!!,
                            url
                        )
                    )
                    logger.debug { "random img, origin: ${query.origin} postId: ${query.postID}, persistence success" }
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
        val baseUrl = imageDTO.authority + "/"
        val th = queryConditionMap["th"]?.toString()?.toInt()

        fun buildUrl(path: String, includeQuery: Boolean): String {
            val idQueryParam = "id=${imageDTO.id}"
            val additionalQueryParams = if (includeQuery && queryConditionMap.isNotEmpty()) "&$queryCondition" else ""
            return "$path?$idQueryParam$additionalQueryParams"
        }

        return baseUrl +
                if (th == null) buildUrl(imageDTO.w1280Path, false)
                else {
                    val (width, path) = mapOf(
                        imageDTO.originalWidth to imageDTO.originalPath,
                        ImageSize.W_1920.width to imageDTO.w1920Path,
                        ImageSize.W_1440.width to imageDTO.w1440Path,
                        ImageSize.W_1280.width to imageDTO.w1280Path,
                        ImageSize.W_960.width to imageDTO.w960Path,
                        ImageSize.W_640.width to imageDTO.w640Path,
                        ImageSize.W_320.width to imageDTO.w320Path
                    ).minBy { (width, _) -> (th - width).absoluteValue }

                    buildUrl(path, width != th)
                }
    }

}