package io.sakurasou.service

import com.amazonaws.services.s3.AmazonS3
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.entity.Image
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * @author mashirot
 * 2024/5/16 21:23
 */
@Service
class S3DeleteService(
    private val s3Client: AmazonS3,
    private val cloudreveService: CloudreveService,
    config: RandomImgConfig,
) {
    private val logger = KotlinLogging.logger { this::class.java }

    val bucketName = config.s3.bucketName

    fun deleteFileFromS3(imageList: List<Image>): Mono<String> {
        if (imageList.isEmpty()) return Mono.empty()
        var successCnt = 0
        imageList.forEach { image ->
            runCatching {
                successCnt++
                logger.debug { "s3 delete success: ${image.path}" }
                s3Client.deleteObject(bucketName, image.path)
            }.getOrElse {
                logger.error(it) { "s3 delete failed: ${image.path}" }
                return@forEach
            }
        }
        val result = "mysql deleted: ${imageList.size} row, s3 deleted $successCnt files"
        logger.info { result }
        cloudreveService.sync2Cloudreve()
        return Mono.just(result)
    }
}