package io.sakurasou.repository

import io.sakurasou.entity.Image
import io.sakurasou.entity.ImageRandomQueryDTO
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @author mashirot
 * 2024/5/16 16:58
 */
@Repository
interface ImageRepository: R2dbcRepository<Image, Long> {

    @Query("""
        update images
        set is_deleted = 1
        where id = :id
            and is_deleted = 0
    """)
    fun deleteImageById(id: Long): Mono<Int>

    @Query("""
        update images
        set is_deleted = 1
        where uid = :uid
            and is_deleted = 0
    """)
    fun deleteImageByUid(uid: Long): Mono<Int>

    @Query("""
        select uid, pid, url, path, id
        from images
        where id = :id
            and is_deleted = 0
    """)
    fun selImageById(id: Long): Mono<Image>

    @Query("""
        select uid, pid, url, path, id
        from images
        where uid = :uid
            and is_deleted = 0
    """)
    fun selImageByUid(uid: Long): Flux<Image>

    @Query("""
        select uid, pid, url, path, id
        from images
        where (:#{#randomQueryDTO.uid} is null or uid = :#{#randomQueryDTO.uid})
            and rand() < 0.11 and is_deleted = 0
        limit 1
    """)
    fun randomSelImage(randomQueryDTO: ImageRandomQueryDTO): Mono<Image>
}