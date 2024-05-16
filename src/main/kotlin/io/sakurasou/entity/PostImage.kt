package io.sakurasou.entity

import org.springframework.data.relational.core.mapping.Table

/**
 * @author mashirot
 * 2024/5/13 22:50
 */
@Table(name = "post_images")
class PostImage: BaseEntity() {
    var source: String ?= null
    var postId: String ?= null
    var imageId: Long ?= null
    var url: String ?= null
}

data class PostImageSourceDTO(
    val source: String
)

data class PostImagePostIdDTO(
    val source: String,
    val postId: String
)

data class PostImageDTO(
    val source: String,
    val postId: String,
    val imageId: Long,
    val url: String
)