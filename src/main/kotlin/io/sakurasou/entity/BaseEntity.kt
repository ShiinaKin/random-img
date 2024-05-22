package io.sakurasou.entity

import java.time.LocalDateTime

/**
 * @author mashirot
 * 2024/5/15 10:47
 */
open class BaseEntity {
    var id: Long ?= null
    var createTime: LocalDateTime ?= null
    var updateTime: LocalDateTime ?= null
    var deleted: Boolean = false
}