package io.sakurasou.config

import io.sakurasou.endpoint.BaseEndpoint
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * @author mashirot
 * 2024/5/15 14:13
 */
@Configuration
class WebFluxConfig(
    private val context: ApplicationContext
) {

    @Bean
    fun router(): RouterFunction<ServerResponse> =
        RouterFunctions.route().apply {
            context.getBeansOfType(BaseEndpoint::class.java).values.forEach {
                add(it.route())
            }
        }.build()
}