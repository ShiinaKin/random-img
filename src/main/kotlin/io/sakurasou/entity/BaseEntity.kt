package io.sakurasou.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import java.time.LocalDateTime

/**
 * @author mashirot
 * 2024/5/15 10:47
 */
open class BaseEntity {
    @Id
    var id: Long ?= null
    var createTime: LocalDateTime ?= null
    var updateTime: LocalDateTime ?= null
    @Column("is_deleted")
    var deleted: Int = 0
}