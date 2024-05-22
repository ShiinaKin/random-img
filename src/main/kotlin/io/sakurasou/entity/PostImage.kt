package io.sakurasou.entity

import org.ktorm.schema.*

/**
 * @author mashirot
 * 2024/5/13 22:50
 */
class PostImage: BaseEntity() {
    var origin: String ?= null
    var postId: String ?= null
    var imageId: Long ?= null
    var queryCondition: String ?= null
    var url: String ?= null
}

object PostImages : Table<Nothing>("post_images") {
    val id = long("id").primaryKey()
    val origin = varchar("origin")
    val postId = varchar("post_id")
    val imageId = long("image_id")
    val queryCondition = varchar("query_condition")
    val url = varchar("url")
    val createTime = datetime("create_time")
    val updateTime = datetime("update_time")
    val deleted = boolean("is_deleted")
}

data class PostImageDeleteByOriginDTO(
    val origin: String
)

data class PostImageQueryByPostIdDTO(
    val origin: String,
    val postId: String,
    var queryCondition: String
)

data class PostImageDTO(
    val origin: String,
    val postId: String,
    val imageId: Long,
    var queryCondition: String,
    val url: String
)