package io.sakurasou.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.dao.ImageDAO
import io.sakurasou.dao.PostImageDAO
import io.sakurasou.entity.*
import io.sakurasou.exception.ImageFetchException
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.Duration

/**
 * @author mashirot
 * 2024/5/13 16:59
 */
@Service
class ImageService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val imageDAO: ImageDAO,
    private val postImageDAO: PostImageDAO,
    private val s3DeleteService: S3DeleteService,
    private val config: RandomImgConfig
) {
    private val logger = KotlinLogging.logger { this::class.java }

    fun batchInsertImage(dtoList: List<ImageDTO>): Mono<String> {
        return if (dtoList.isEmpty()) Mono.just("total img: 0, success upload: 0")
        else imageDAO.batchInsert(dtoList)
                .map { "total img: ${dtoList.size}, success upload: $it" }
    }

    fun deleteImage(deleteDTO: ImageDeleteDTO): Mono<String> {
        return imageDAO.deleteImageByIdOrUid(deleteDTO)
            .collectList()
            .flatMap { s3DeleteService.deleteFileFromS3(it) }
    }

    fun selectImage(imageQuery: ImageQuery): Mono<String> {
        val key = "random_img:select:${imageQuery.id}"
        logger.debug { "select img, key: $key" }
        val defaultExpire = Duration.ofHours(3)
        return redisTemplate.opsForValue().getAndExpire(key, defaultExpire)
            .switchIfEmpty {
                Mono.defer {
                    logger.debug { "select img, cache miss" }
                    imageDAO.selImage(ImageQueryDTO(imageQuery.id))
                        .flatMap {
                            logger.debug { "select img, cache miss" }
                            redisTemplate.opsForValue().set(key, it.url, defaultExpire)
                                .thenReturn(it.url)
                        }
                        .switchIfEmpty {
                            logger.debug { "select img, database miss, id: ${imageQuery.id}" }
                            throw ImageFetchException("no such img, id: ${imageQuery.id}")
                        }
                }
            }.map {
                handleUrl(it, "quality" to imageQuery.quality, "th" to imageQuery.th)
            }
    }

    fun randomGetImage(imageRandomDTO: ImageRandomDTO): Mono<String> {
        val defaultExpire = Duration.ofHours(3)
        val source = imageRandomDTO.source
        val postID = imageRandomDTO.postID
        return if (postID != null) {
            val key = "random_img:random:$source:$postID"
            logger.debug { "random img, taken referer and post id, redis key: $key" }
            redisTemplate.opsForValue().getAndExpire(key, defaultExpire)
                .map {
                    logger.debug { "random img, cache hit, key: $key" }
                    it
                }
                .switchIfEmpty {
                    Mono.defer {
                        postImageDAO.selImgByPostId(PostImagePostIdDTO(source, postID))
                            .map {
                                logger.debug { "random img, cache miss, key: $key" }
                                it.url
                            }
                            .switchIfEmpty {
                                Mono.defer {
                                    imageDAO.randomSelImage(ImageRandomQueryDTO(imageRandomDTO.uid))
                                        .switchIfEmpty { throw ImageFetchException("no random image fetched") }
                                        .flatMap { img ->
                                            logger.debug { "random img, database miss, key: $key" }
                                            logger.info { "random img, source: $source postId: $postID fetch random image successfully, imgId: ${img.id}" }
                                            if (config.persistenceHosts.contains(source)) {
                                                logger.debug { "random img, referer need to persistence" }
                                                postImageDAO.insert(
                                                    PostImageDTO(source, postID, img.id!!, img.url)
                                                ).doOnNext {
                                                    logger.debug { "random img, persistence success" }
                                                }.switchIfEmpty {
                                                    logger.debug { "random img, persistence failed" }
                                                    Mono.empty()
                                                }.thenReturn(img.url)
                                            } else {
                                                Mono.just(img.url)
                                            }
                                        }
                                }
                            }
                            .flatMap {
                                redisTemplate.opsForValue().set(key, it, defaultExpire)
                                    .thenReturn(it)
                            }
                    }
                }
                .map {
                    handleUrl(it, "quality" to imageRandomDTO.quality, "th" to imageRandomDTO.th)
                }
        } else {
            imageDAO.randomSelImage(ImageRandomQueryDTO(imageRandomDTO.uid))
                .switchIfEmpty { throw ImageFetchException("no random image fetched") }
                .map {
                    logger.debug { "random img, not taken referer and postId" }
                    logger.info { "random img, source: $source fetch random image successfully, imgId: ${it.id}" }
                    handleUrl(it.url, "quality" to imageRandomDTO.quality, "th" to imageRandomDTO.th)
                }
        }
    }

    private fun handleUrl(rawUrl: String, vararg params: Pair<String, Any?>): String {
        return rawUrl + "?" + params.filter { it.second != null }.joinToString("&") { "${it.first}=${it.second}" }
    }
}