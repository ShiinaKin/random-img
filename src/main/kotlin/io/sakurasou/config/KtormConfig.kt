package io.sakurasou.config

import org.ktorm.database.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * @author mashirot
 * 2024/5/21 20:19
 */
@Configuration
class KtormConfig {
    @Bean
    fun database(dataSource: DataSource) = Database.connectWithSpringSupport(dataSource)
}