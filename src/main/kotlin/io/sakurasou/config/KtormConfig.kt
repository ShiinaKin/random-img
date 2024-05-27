package io.sakurasou.config

import org.ktorm.database.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.sql.DataSource

/**
 * @author mashirot
 * 2024/5/21 20:19
 */
@Configuration
class KtormConfig {
    @Bean
    fun database(dataSource: DataSource): Database {
        val database = Database.connectWithSpringSupport(dataSource)
        initTable(database)
        return database
    }

    fun initTable(database: Database) {
        val sqlFileStream = this::class.java.classLoader.getResourceAsStream("sql/random_img.sql")
            ?: throw RuntimeException("cannot find sql file")
        val sql = BufferedReader(InputStreamReader(sqlFileStream)).use { it.readText() }
        database.useConnection { connection ->
            connection.createStatement().use { statement ->
                sql.split("# ---").forEach {
                    statement.execute(it)
                }
            }
        }
    }
}