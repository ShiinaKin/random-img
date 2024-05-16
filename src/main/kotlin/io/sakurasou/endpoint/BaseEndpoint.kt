package io.sakurasou.endpoint

import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * @author mashirot
 * 2024/2/2 20:38
 */
internal interface BaseEndpoint {
    fun route(): RouterFunction<ServerResponse>
}
