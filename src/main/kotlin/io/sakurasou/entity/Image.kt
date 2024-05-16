package io.sakurasou.entity

import org.springframework.data.relational.core.mapping.Table

/**
 * @author mashirot
 * 2024/5/13 18:06
 */
@Table(name = "images")
class Image : BaseEntity() {
    var uid: Long? = null
    var pid: String? = null
    var url: String? = null
    var path: String? = null
}

data class ImageDeleteDTO(
    val id: Long?,
    val uid: Long?,
)

data class ImageQuery(
    val id: Long,
    val th: Int = 640
)

data class ImageQueryDTO(
    val id: Long
)

data class ImageRandomDTO(
    val source: String,
    val postID: String?,
    val uid: Long?,
    val th: Int = 640
)

data class ImageRandomQueryDTO(
    val uid: Long?
)

data class ImageDTO(
    val uid: Long,
    val pid: String,
    val url: String,
    val path: String,
    val id: Long? = null
)
