package io.sakurasou.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3ObjectSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.entity.ImageDTO
import io.sakurasou.exception.WrongParameterException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * @author mashirot
 * 2024/5/14 15:50
 */
@Service
class S3UploadService(
    private val imageService: ImageService,
    private val cloudreveService: CloudreveService,
    private val s3Client: AmazonS3,
    config: RandomImgConfig,
) {
    private val logger = KotlinLogging.logger { this::class.java }

    val bucketName = config.s3.bucketName
    val uploadBucketName = config.s3.manualUploadBucketName
    val cdnUrl = config.s3.cdnUrl

    fun handleRemoteUpload(): Mono<String> {
        return Flux.fromIterable(s3Client.listObjectsV2(uploadBucketName).objectSummaries)
            .flatMap { objSummary ->
                handleS3ObjectSummary(objSummary)
                    .flatMap { (success, imgCnt) ->
                        if (success) {
                            s3Client.deleteObject(uploadBucketName, objSummary.key)
                            Mono.just(true to imgCnt)
                        } else {
                            Mono.empty()
                        }
                    }
            }
            .collectList()
            .map { pairs ->
                val objKeys = pairs.map { it.first }
                val totalImageCnt = pairs.sumOf { it.second }
                "success upload: ${objKeys}, success upload img: $totalImageCnt"
            }
    }

    private fun handleS3ObjectSummary(s3ObjectSummary: S3ObjectSummary): Mono<Pair<Boolean, Int>> {
        val key = s3ObjectSummary.key
        val s3Object = s3Client.getObject(uploadBucketName, key)
        val uploadFiles = handleDownloadedZipFile(key.split(".")[0].toLong(), s3Object.objectContent.readBytes())
        val imageDTOs = mutableListOf<ImageDTO>()
        uploadFile2S3(uploadFiles, imageDTOs)
        return imageService.batchInsertImage(imageDTOs)
            .thenReturn(true to uploadFiles.size)
    }

    private fun handleDownloadedZipFile(key: Long, byteArray: ByteArray): List<UploadFile> {
        val uploadFiles = mutableListOf<UploadFile>()
        ZipInputStream(ByteArrayInputStream(byteArray)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.isDirectory) {
                    throw WrongParameterException("Invalid zip file directory format")
                }
                val content = zis.readBytes()
                val lastModifiedTime = entry.lastModifiedTime
                uploadFiles.add(
                    UploadFile(
                        uid = key,
                        pid = entry.name,
                        content = content,
                        size = content.size.toLong(),
                        lastModifiedTime = lastModifiedTime.toInstant()
                    )
                )
                entry = zis.nextEntry
            }
        }
        return uploadFiles
    }

    fun handleUpload(inputFileBytes: ByteArray): Mono<String> {
        val uploadFiles = mutableListOf<UploadFile>()
        handleNormalZipFile(uploadFiles, inputFileBytes)
        val imageDTOs = mutableListOf<ImageDTO>()
        uploadFile2S3(uploadFiles, imageDTOs)
        return imageService.batchInsertImage(imageDTOs)
            .doOnNext {
                cloudreveService.sync2Cloudreve()
            }
    }

    private fun uploadFile2S3(uploadFiles: List<UploadFile>, imageDTOs: MutableList<ImageDTO>) {
        uploadFiles.forEach { uploadFile ->
            val bis = ByteArrayInputStream(uploadFile.content)
            val fileMetadata = ObjectMetadata()
            fileMetadata.lastModified = Date.from(uploadFile.lastModifiedTime)
            fileMetadata.contentLength = uploadFile.size

            runCatching {
                val putRequest = PutObjectRequest(bucketName, uploadFile.path, bis, fileMetadata)
                s3Client.putObject(putRequest)
                imageDTOs.add(
                    ImageDTO(
                        uploadFile.uid, uploadFile.pid,
                        "$cdnUrl/${uploadFile.path}", uploadFile.path
                    )
                )
            }.getOrElse {
                logger.error(it) { "s3 upload failed ${uploadFile.path}" }
                return@forEach
            }
        }
    }

    private fun handleNormalZipFile(uploadFiles: MutableList<UploadFile>, fileBytes: ByteArray) {
        ZipInputStream(ByteArrayInputStream(fileBytes)).use { zipInputStream ->
            var entry: ZipEntry? = zipInputStream.nextEntry
            var uid = 0L
            var isFirst = true
            while (entry != null) {
                if (entry.isDirectory) {
                    uid = entry.name.substringBeforeLast('/').toLong()
                    isFirst = false
                } else {
                    if (isFirst) throw WrongParameterException("Invalid zip file directory format")
                    val fileName = entry.name
                    val pid = fileName.substring(fileName.indexOf('/') + 1, fileName.lastIndexOf('.'))
                    val fileContent = zipInputStream.readBytes()
                    val lastModifiedTime = entry.lastModifiedTime
                    uploadFiles.add(
                        UploadFile(
                            uid,
                            pid,
                            fileName,
                            fileContent,
                            fileContent.size.toLong(),
                            lastModifiedTime.toInstant()
                        )
                    )
                }
                entry = zipInputStream.nextEntry
            }
            zipInputStream.closeEntry()
        }
    }

    private data class UploadFile(
        val uid: Long,
        val pid: String,
        val path: String = "$uid/$pid",
        val content: ByteArray,
        val size: Long,
        val lastModifiedTime: Instant
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UploadFile

            if (uid != other.uid) return false
            if (pid != other.pid) return false
            if (path != other.path) return false
            if (!content.contentEquals(other.content)) return false
            if (size != other.size) return false
            if (lastModifiedTime != other.lastModifiedTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = uid.hashCode()
            result = 31 * result + pid.hashCode()
            result = 31 * result + path.hashCode()
            result = 31 * result + content.contentHashCode()
            result = 31 * result + size.hashCode()
            result = 31 * result + lastModifiedTime.hashCode()
            return result
        }
    }
}