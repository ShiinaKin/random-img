package io.sakurasou.service

import com.amazonaws.services.s3.model.S3ObjectSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.RandomImgConfig
import io.sakurasou.entity.ImageDTO
import io.sakurasou.entity.ImageSize
import io.sakurasou.exception.WrongParameterException
import io.sakurasou.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

/**
 * @author mashirot
 * 2024/5/22 22:36
 */
@Service
class UploadService(
    private val s3Service: S3Service,
    private val imageService: ImageService,
    private val cloudreveService: CloudreveService,
    config: RandomImgConfig
) {

    private val logger = KotlinLogging.logger { this::class.java }

    val threadId = AtomicInteger(0)
    private val uploadThreadPool = ThreadPoolExecutor(
        3, 3, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
        { Thread(it, "image-upload-thread-${threadId.getAndIncrement()}") },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val s3ObjSummaryNeed2UploadSet = mutableSetOf<String>()

    private val cdnUrl = config.s3.cdnUrl

    suspend fun handleRemoteUpload(uploadNum: Int) {
        uploadThreadPool.execute {
            runBlocking {
                var objectSummaries =
                    s3Service.listUploadBucket().filter { !s3ObjSummaryNeed2UploadSet.contains(it.key) }
                objectSummaries = if (objectSummaries.size <= uploadNum) objectSummaries
                else objectSummaries.subList(0, uploadNum)
                objectSummaries.forEach { s3ObjSummaryNeed2UploadSet.add(it.key) }
                if (objectSummaries.isEmpty()) {
                    logger.info { "no object need to upload" }
                    return@runBlocking
                }
                logger.info { "submit task to thread pool, ${objectSummaries.map { it.key }} need to upload" }
                val pairs = objectSummaries.map { objSummary ->
                    handleS3ObjectSummary(objSummary).let {
                        if (it.first) {
                            s3ObjSummaryNeed2UploadSet.remove(objSummary.key)
                            s3Service.delObjFromUploadBucket(objSummary.key)
                        }
                        it.second
                    }
                }
                val totalImageCnt = pairs.sumOf { it }
                logger.info { "total success upload img: $totalImageCnt" }
                cloudreveService.sync2Cloudreve()
            }
        }
    }

    suspend fun handleUpload(inputFileBytes: ByteArray) {
        uploadThreadPool.submit {
            runBlocking {
                val uploadFiles = handleNormalZipFile(inputFileBytes)
                logger.info { "submit task to thread pool, ${uploadFiles.size} files need to upload" }
                val imageEntityAndFileMapping = handleUploadFiles(uploadFiles)
                val imageDTOs = s3Service.uploadFile2S3(imageEntityAndFileMapping)
                imageService.batchInsertImage(imageDTOs)
                logger.info { "upload success, imgCnt: ${uploadFiles.size}" }
                cloudreveService.sync2Cloudreve()
            }
        }
    }

    private suspend fun handleUploadFiles(uploadFiles: List<UploadFile>): Map<ImageDTO, List<UploadFile>> {
        suspend fun handleResizeImage(
            image: BufferedImage,
            rawFile: UploadFile,
            fileNamePrefix: String,
            imageSize: ImageSize
        ): UploadFile {
            val nImage = ImageUtils.resize(image, imageSize.size)
            val path = "${fileNamePrefix}_width_${imageSize.size}.webp"
            return UploadFile(
                rawFile.uid, rawFile.pid, path,
                nImage, nImage.size.toLong(), Instant.now()
            )
        }

        val map: MutableMap<ImageDTO, List<UploadFile>> = mutableMapOf()
        uploadFiles.forEach { rawFile ->
            ByteArrayInputStream(rawFile.content).use { bis ->
                val image = ImageIO.read(bis)
                val width = image.width
                val fileNamePrefix = rawFile.path.split(".")[0]
                val w320 = handleResizeImage(image, rawFile, fileNamePrefix, ImageSize.W_320)
                val w640 = handleResizeImage(image, rawFile, fileNamePrefix, ImageSize.W_640)
                val w960 = handleResizeImage(image, rawFile, fileNamePrefix, ImageSize.W_960)
                val w1280 = handleResizeImage(image, rawFile, fileNamePrefix, ImageSize.W_1280)
                val w1600 = handleResizeImage(image, rawFile, fileNamePrefix, ImageSize.W_1600)
                val w1920 = handleResizeImage(image, rawFile, fileNamePrefix, ImageSize.W_1920)
                val imageDTO = ImageDTO(
                    rawFile.uid,
                    rawFile.pid,
                    cdnUrl,
                    originalWidth = width,
                    originalPath = rawFile.path,
                    w1920Path = w1920.path,
                    w1600Path = w1600.path,
                    w1280Path = w1280.path,
                    w960Path = w960.path,
                    w640Path = w640.path,
                    w320Path = w320.path,
                    null
                )
                map[imageDTO] = listOf(rawFile, w1920, w1600, w1280, w960, w640, w320)
            }
        }
        return map
    }

    private suspend fun handleS3ObjectSummary(s3ObjectSummary: S3ObjectSummary): Pair<Boolean, Int> {
        val key = s3ObjectSummary.key
        try {
            val s3Object = s3Service.getObjFromUploadBucket(key)
            val uploadFiles = handleDownloadedZipFile(key.split(".")[0].toLong(), s3Object.objectContent.readBytes())
            val imageEntityAndFileMapping = handleUploadFiles(uploadFiles)
            val imageDTOs = s3Service.uploadFile2S3(imageEntityAndFileMapping)
            imageService.batchInsertImage(imageDTOs)
            logger.info { "$key upload success, imgCnt: ${uploadFiles.size}" }
            return true to uploadFiles.size
        } catch (e: Exception) {
            logger.error(e) { "$key upload failed" }
            return false to 0
        }
    }

    private suspend fun handleDownloadedZipFile(key: Long, byteArray: ByteArray): List<UploadFile> {
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

    private suspend fun handleNormalZipFile(fileBytes: ByteArray): List<UploadFile> {
        return withContext(Dispatchers.IO) {
            val uploadFiles = mutableListOf<UploadFile>()
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
            uploadFiles
        }
    }

    data class UploadFile(
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