package io.sakurasou.config

import io.sakurasou.interceptor.BasicAuthorizationInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * @author mashirot
 * 2024/5/22 16:22
 */
@Configuration
class WebMvcConfig(
    private val basicAuthInterceptor: BasicAuthorizationInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(basicAuthInterceptor).addPathPatterns("/**")
    }
}