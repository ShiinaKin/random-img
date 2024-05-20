package io.sakurasou.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * @author mashirot
 * 2024/5/15 22:23
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val config: RandomImgConfig
) {
    @Bean
    fun userDetailsService(): ReactiveUserDetailsService {
        val userDetails = User.builder()
            .username(config.basicAuth.username)
            .password(config.basicAuth.password)
            .roles("USER")
            .build()
        return MapReactiveUserDetailsService(userDetails)
    }

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .authorizeExchange {
            it.pathMatchers("/random").permitAll()
                .pathMatchers("/").authenticated()
                .pathMatchers("/remote-upload").authenticated()
                .anyExchange().denyAll()
        }
        .httpBasic { }
        .build()

}