package io.sakurasou.repository

import io.sakurasou.entity.PostImage
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @author mashirot
 * 2024/5/16 17:14
 */
@Repository
interface PostImageRepository : R2dbcRepository<PostImage, Long> {

    @Query("""
        insert into post_images
        value (:#{#entity.id}, :#{#entity.source}, :#{#entity.postId}, :#{#entity.imageId}, 
            :#{#entity.url}, :#{#entity.createTime}, :#{#entity.updateTime}, :#{#entity.deleted})
    """)
    fun insert(@Param("entity") entity: PostImage): Mono<PostImage>

    @Query("""
        update post_images
        set is_deleted = 1
        where source = :source
            and is_deleted = 0
    """)
    fun deleteBySource(source: String): Flux<PostImage>

    @Query("""
        update post_images
        set is_deleted = 1
        where source = :source
            and post_id = :postId
            and is_deleted = 0
    """)
    fun deleteByPostId(source: String, postId: String): Mono<PostImage>

    @Query("""
        update post_images
        set is_deleted = 1
        where image_id = :imageId
            and is_deleted = 0
    """)
    fun deleteByImageId(imageId: Long): Mono<PostImage>

    @Query("""
        select source, post_id, image_id, url
        from post_images
        where source = :source
            and post_id = :postId
            and is_deleted = 0
    """)
    fun selImgByPostId(source: String, postId: String): Mono<PostImage>
}