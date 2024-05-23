package io.sakurasou.entity

import org.ktorm.schema.*

/**
 * @author mashirot
 * 2024/5/13 18:06
 */
class Image : BaseEntity() {
    var uid: Long? = null
    var pid: String? = null
    var authority: String? = null
    var originalWidth: Int? = null
    var originalSizePath: String? = null
    var mediumSizePath: String? = null
    var minimalSizePath: String? = null
}

object Images : Table<Nothing>("images") {
    val id = long("id").primaryKey()
    val uid = long("uid")
    val pid = varchar("pid")
    val authority = varchar("authority")
    val originalWidth = int("original_width")
    val originalSizePath = varchar("original_size_path")
    val mediumSizePath = varchar("medium_size_path")
    val minimalSizePath = varchar("minimal_size_path")
    val createTime = datetime("create_time")
    val updateTime = datetime("update_time")
    val deleted = boolean("is_deleted")
}

data class ImageDeleteDTO(
    val id: Long?,
    val uid: Long?,
)

data class ImageQuery(
    val id: Long,
    val th: Int?,
    val quality: Int = 1
) {
    val queryConditionMap = mapOf("quality" to quality, "th" to th).filter { it.value != null }
    val queryCondition = queryConditionMap.toList().joinToString("&") { "${it.first}=${it.second}" }
}

data class ImageQueryDTO(
    val id: Long
)

data class ImageRandomQuery(
    val origin: String,
    val postID: String?,
    val uid: Long?,
    val th: Int?,
    val quality: Int = 1
) {
    val queryConditionMap = mapOf("uid" to uid, "quality" to quality, "th" to th).filter { it.value != null }
    val queryCondition = queryConditionMap.toList().joinToString("&") { "${it.first}=${it.second}" }
}

data class ImageRandomQueryDTO(
    val uid: Long?
)

data class ImageDTO(
    val uid: Long,
    val pid: String,
    val authority: String,
    var originalWidth: Int,
    val originalSizePath: String,
    var mediumSizePath: String,
    var minimalSizePath: String,
    val id: Long? = null
)

enum class ImageSize(val size: Int) {
    MEDIUM_SIZE(1280),
    MINIMAL_SIZE(640)
}