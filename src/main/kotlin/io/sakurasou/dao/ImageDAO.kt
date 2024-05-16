package io.sakurasou.dao

import io.sakurasou.config.SnowFlakeIdGenerator
import io.sakurasou.entity.*
import io.sakurasou.exception.WrongParameterException
import io.sakurasou.repository.ImageRepository
import io.sakurasou.repository.PostImageRepository
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.time.LocalDateTime

/**
 * @author mashirot
 * 2024/5/14 22:39
 */
@Component
class ImageDAO(
    private val idGenerator: SnowFlakeIdGenerator,
    private val imageRepository: ImageRepository,
    private val databaseClient: DatabaseClient
) {

    fun batchInsert(dtoList: List<ImageDTO>): Mono<Long> {
        return dtoList.map {
            Image().apply {
                val now = LocalDateTime.now()
                id = idGenerator.generate()
                uid = it.uid
                pid = it.pid
                url = it.url
                path = it.path
                createTime = now
                updateTime = now
                deleted = 0
            }
        }.let { list ->
            val sql = StringBuilder("insert into images values ")
            val iterator = list.iterator()
            while (iterator.hasNext()) {
                val image = iterator.next()
                sql.append(
                    String.format(
                        "('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')",
                        image.id, image.uid, image.pid, image.url, image.path,
                        image.createTime, image.updateTime, image.deleted
                    )
                )
                if (iterator.hasNext()) sql.append(", ")
            }
            databaseClient.sql(sql.toString()).fetch().rowsUpdated()
        }
    }

    @Transactional
    fun deleteImageByIdOrUid(deleteDTO: ImageDeleteDTO): Flux<Image> {
        return when {
            deleteDTO.id != null -> {
                imageRepository.selImageById(deleteDTO.id)
                    .flatMap { image ->
                        // 这里如果是Repository返回的Mono/Flux只能被订阅一层，里面的不会被消费
                        // 也可能是我写错了
                        databaseClient.sql("""
                                update post_images
                                set is_deleted = 1
                                where image_id = ${image.id}
                                    and is_deleted = 0
                            """.trimIndent()).fetch().rowsUpdated().flatMap {
                            imageRepository.deleteImageById(image.id!!)
                        }.thenReturn(image)
                    }.toFlux()
            }

            deleteDTO.uid != null -> {
                imageRepository.selImageByUid(deleteDTO.uid)
                    .flatMap { image ->
                        databaseClient.sql("""
                                update post_images
                                set is_deleted = 1
                                where image_id = ${image.id}
                                    and is_deleted = 0
                            """.trimIndent()).fetch().rowsUpdated().flatMap {
                            imageRepository.deleteImageById(image.id!!)
                        }.thenReturn(image)
                    }
            }

            else -> throw WrongParameterException("must have id or uid")
        }
    }

    fun selImage(imageQueryDTO: ImageQueryDTO): Mono<ImageDTO> {
        return imageRepository.selImageById(imageQueryDTO.id)
            .map {
                ImageDTO(
                    it.uid!!,
                    it.pid!!,
                    it.url!!,
                    it.path!!,
                    it.id
                )
            }
    }

    fun randomSelImage(randomQueryDTO: ImageRandomQueryDTO): Mono<ImageDTO> {
        return imageRepository.randomSelImage(randomQueryDTO)
            .map {
                ImageDTO(
                    it.uid!!,
                    it.pid!!,
                    it.url!!,
                    it.path!!,
                    it.id
                )
            }
    }

}