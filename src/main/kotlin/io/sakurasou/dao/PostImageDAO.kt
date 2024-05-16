package io.sakurasou.dao

import io.sakurasou.config.SnowFlakeIdGenerator
import io.sakurasou.entity.PostImage
import io.sakurasou.entity.PostImageDTO
import io.sakurasou.entity.PostImagePostIdDTO
import io.sakurasou.entity.PostImageSourceDTO
import io.sakurasou.repository.PostImageRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * @author mashirot
 * 2024/5/15 18:02
 */
@Component
class PostImageDAO(
    private val idGenerator: SnowFlakeIdGenerator,
    private val postImageRepository: PostImageRepository
) {

     fun insert(postImageDTO: PostImageDTO): Mono<PostImage> {
        return postImageRepository.insert(
            PostImage().apply {
                val now = LocalDateTime.now()
                id = idGenerator.generate()
                source = postImageDTO.source
                postId = postImageDTO.postId
                imageId = postImageDTO.imageId
                url = postImageDTO.url
                createTime = now
                updateTime = now
                deleted = 0
            }
        )
    }

    fun deleteBySource(sourceDTO: PostImageSourceDTO): Flux<PostImage> {
        return postImageRepository.deleteBySource(sourceDTO.source)
    }

    fun deleteByPostId(postIdDTO: PostImagePostIdDTO): Mono<PostImage> {
        return postImageRepository.deleteByPostId(postIdDTO.source, postIdDTO.postId)
    }

    fun selImgByPostId(postIdDTO: PostImagePostIdDTO): Mono<PostImageDTO> {
        return postImageRepository.selImgByPostId(postIdDTO.source, postIdDTO.postId)
            .map {
                PostImageDTO(
                    it.source!!,
                    it.postId!!,
                    it.imageId!!,
                    it.url!!
                )
            }
    }
}