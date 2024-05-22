package io.sakurasou.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.entity.ImageDTO
import io.sakurasou.entity.ImageDeleteResultDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.*

/**
 * @author mashirot
 * 2024/5/21 20:01
 */
@Service
class S3Service(
    private val s3Client: AmazonS3,
    private val cloudreveService: CloudreveService,
    config: RandomImgConfig,
) {

    private val logger = KotlinLogging.logger { this::class.java }

    val bucketName = config.s3.bucketName
    val uploadBucketName = config.s3.manualUploadBucketName

    suspend fun uploadFile2S3(uploadFiles: Map<ImageDTO, List<UploadService.UploadFile>>): List<ImageDTO> {
        return withContext(Dispatchers.IO) {
            uploadFiles.forEach { entry ->
                entry.value.forEach { uploadFile ->
                    val bis = ByteArrayInputStream(uploadFile.content)
                    val fileMetadata = ObjectMetadata()
                    fileMetadata.lastModified = Date.from(uploadFile.lastModifiedTime)
                    fileMetadata.contentLength = uploadFile.size

                    try {
                        val putRequest = PutObjectRequest(bucketName, uploadFile.path, bis, fileMetadata)
                        s3Client.putObject(putRequest)
                    } catch (e: Exception) {
                        logger.error(e) { "s3 upload failed ${uploadFile.path}" }
                    }
                }
            }
            uploadFiles.keys.toList()
        }
    }

    suspend fun deleteFileFromS3(imageList: List<ImageDeleteResultDTO>) {
        if (imageList.isEmpty()) return
        var successCnt = 0
        imageList.forEach { image ->
            runCatching {
                successCnt++
                logger.debug { "s3 delete success: originalSizePath" }
                withContext(Dispatchers.IO) {
                    s3Client.deleteObject(bucketName, image.key)
                }
            }.getOrElse {
                logger.error(it) { "s3 delete failed: ${image.key}" }
                return@forEach
            }
        }
        cloudreveService.sync2Cloudreve()
    }

    suspend fun listUploadBucket(): List<S3ObjectSummary> =
        withContext(Dispatchers.IO) { s3Client.listObjectsV2(uploadBucketName).objectSummaries }

    suspend fun getObjFromUploadBucket(key: String): S3Object =
        withContext(Dispatchers.IO) { s3Client.getObject(uploadBucketName, key) }

    suspend fun delObjFromUploadBucket(key: String) =
        withContext(Dispatchers.IO) { s3Client.deleteObject(uploadBucketName, key) }


}