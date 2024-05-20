package io.sakurasou.endpoint

import io.sakurasou.entity.ImageDeleteDTO
import io.sakurasou.entity.ImageQuery
import io.sakurasou.entity.ImageRandomDTO
import io.sakurasou.exception.WrongParameterException
import io.sakurasou.service.ImageService
import io.sakurasou.service.S3UploadService
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Mono
import java.net.URI
import java.nio.ByteBuffer
import java.util.*

/**
 * @author mashirot
 * 2024/5/14 22:17
 */
@Component
class ImageEndpoint(
    private val imageService: ImageService,
    private val s3UploadService: S3UploadService,
) : BaseEndpoint {

    override fun route(): RouterFunction<ServerResponse> =
        RouterFunctions.route()
            .POST("/", this::upload)
            .PUT("/remote-upload", this::remoteUpload)
            .DELETE("/", this::delImg)
            .GET("/", this::getImg)
            .GET("/random", this::random)
            .build()

    @PreAuthorize("authenticated")
    private fun upload(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.multipartData().flatMap { parts ->
            val filePart = parts["file"]?.first() as? FilePart
            (filePart?.content()?.reduce(ByteBuffer.allocate(0)) { buffer, dataBuffer ->
                ByteBuffer.allocate(buffer.remaining() + dataBuffer.readableByteCount()).apply {
                    put(buffer)
                    val dataByteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount())
                    dataBuffer.toByteBuffer(dataByteBuffer)
                    put(dataByteBuffer)
                    flip()
                }
            } ?: throw WrongParameterException("file needed"))
                .flatMap { byteBuffer ->
                    val byteArray = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(byteArray)
                    s3UploadService.handleUpload(byteArray)
                        .flatMap { ServerResponse.ok().bodyValue(it) }
                }
        }
    }

    @PreAuthorize("authenticated")
    private fun remoteUpload(serverRequest: ServerRequest): Mono<ServerResponse> {
        val uploadNum = serverRequest.queryParamOrNull("uploadNum")?.toInt() ?: 1
        return s3UploadService.handleRemoteUpload(uploadNum)
            .flatMap { ServerResponse.ok().bodyValue(it) }
    }

    @PreAuthorize("authenticated")
    fun delImg(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.let {
            val id = it.queryParamOrNull("id")?.toLong()
            val uid = it.queryParamOrNull("uid")?.toLong()
            if (id == null && uid == null) throw WrongParameterException("wrong query param")
            Mono.just(ImageDeleteDTO(id, uid))
        }.flatMap {
            imageService.deleteImage(it).flatMap { result ->
                ServerResponse.ok().bodyValue(result)
            }
        }
    }

    @PreAuthorize("authenticated")
    private fun getImg(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.let {
            val id = it.queryParamOrNull("id")?.toLong() ?: throw WrongParameterException("need imageId")
            val quality = it.queryParamOrNull("quality")?.toInt()
            val th = it.queryParamOrNull("th")?.toInt()
            Mono.just(ImageQuery(id, quality, th))
        }
            .flatMap { imageService.selectImage(it) }
            .flatMap { redirect2Img(it) }
    }

    private fun random(httpRequest: ServerRequest): Mono<ServerResponse> {
        val source = httpRequest.getSource()
        return httpRequest.let {
            val postId = it.queryParamOrNull("postId")?.let { postId -> UUID.fromString(postId) }
            val uid = it.queryParamOrNull("uid")?.toLong()
            val quality = it.queryParamOrNull("quality")?.toInt()
            val th = it.queryParamOrNull("th")?.toInt()
            Mono.just(ImageRandomRequest(source, postId, uid, quality, th))
        }
            .map { ImageRandomDTO(source, it.postId?.toString(), it.uid, it.quality, it.th) }
            .flatMap { imageService.randomGetImage(it) }
            .flatMap { redirect2Img(it) }
    }

    /**
     * 可以去掉，等重构，目前唯一的意义是UUID格式校验
     */
    private data class ImageRandomRequest(
        val source: String?,
        val postId: UUID?,
        val uid: Long?,
        val quality: Int?,
        val th: Int?
    )

    private fun redirect2Img(imgUrl: String): Mono<ServerResponse> =
        ServerResponse.permanentRedirect(URI.create(imgUrl)).build()

    private fun ServerRequest.getSource(): String {
        return headers().host()?.hostString ?: throw WrongParameterException("must take host")
    }

}