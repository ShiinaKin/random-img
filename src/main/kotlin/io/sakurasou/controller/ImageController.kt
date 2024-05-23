package io.sakurasou.controller

import io.sakurasou.annotation.BasicAuth
import io.sakurasou.entity.ImageDeleteDTO
import io.sakurasou.entity.ImageQuery
import io.sakurasou.entity.ImageRandomQuery
import io.sakurasou.exception.WrongParameterException
import io.sakurasou.service.ImageService
import io.sakurasou.service.UploadService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * @author mashirot
 * 2024/5/21 19:50
 */
@RestController
@RequestMapping
class ImageController(
    private val imageService: ImageService,
    private val uploadService: UploadService
) {
    @BasicAuth
    @PostMapping("/")
    suspend fun directUpload(@RequestBody file: MultipartFile) {
        uploadService.handleUpload(file.bytes)
    }

    @BasicAuth
    @PutMapping("/remote-upload")
    suspend fun remoteUpload(num: Int = 1) {
        uploadService.handleRemoteUpload(num)
    }

    @BasicAuth
    @DeleteMapping("/")
    suspend fun deleteImage(id: Long?, uid: Long?) {
        imageService.deleteImage(ImageDeleteDTO(id, uid))
    }

    @GetMapping("/")
    suspend fun selectImage(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
        id: Long, th: Int?, quality: Int?
    ) {
        val query = ImageQuery(id, th, quality ?: 1)
        val url = imageService.selectImage(query)
        withContext(Dispatchers.IO) {
            httpResponse.sendRedirect(url)
        }
    }

    @GetMapping("/random")
    suspend fun randomSelImage(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
        postId: String?, uid: Long?, th: Int?, quality: Int?
    ) {
        val referer = httpRequest.getHeader("Referer")?.replace(":", "")
            ?: throw WrongParameterException("must take referer")
        val randomQuery = ImageRandomQuery(referer, postId, uid, th, quality ?: 1)
        val url = imageService.randomSelectImage(randomQuery)
        withContext(Dispatchers.IO) {
            httpResponse.sendRedirect(url)
        }
    }

}