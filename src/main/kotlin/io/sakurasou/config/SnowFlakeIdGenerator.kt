package io.sakurasou.config

import io.sakurasou.common.SnowFlake
import org.springframework.stereotype.Component

/**
 * @author mashirot
 * 2024/5/15 13:24
 */
@Component
class SnowFlakeIdGenerator {
    companion object {
        val snowFlake = SnowFlake(0, 2)
    }

    fun generate() = snowFlake.nextId()
}