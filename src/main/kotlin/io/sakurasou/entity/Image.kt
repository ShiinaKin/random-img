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
    var originalPath: String? = null
    var w1920Path: String? = null
    var w1440Path: String? = null
    var w1280Path: String? = null
    var w960Path: String? = null
    var w640Path: String? = null
    var w320Path: String? = null
}

object Images : Table<Nothing>("images") {
    val id = long("id").primaryKey()
    val uid = long("uid")
    val pid = varchar("pid")
    val authority = varchar("authority")
    val originalWidth = int("original_width")
    val originalPath = varchar("original_path")
    val w1920Path = varchar("width_1920_path")
    val w1440Path = varchar("width_1440_path")
    val w1280Path = varchar("width_1280_path")
    val w960Path = varchar("width_960_path")
    val w640Path = varchar("width_640_path")
    val w320Path = varchar("width_320_path")
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
    val origin: String?,
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
    val originalPath: String,
    val w1920Path: String,
    val w1440Path: String,
    val w1280Path: String,
    val w960Path: String,
    val w640Path: String,
    val w320Path: String,
    val id: Long? = null
)

enum class ImageSize(val width: Int, val type: String) {
    W_1920(1920, "webp"),
    W_1440(1440, "webp"),
    W_1280(1280, "webp"),
    W_960(960, "webp"),
    W_640(640, "webp"),
    W_320(320, "webp")
}