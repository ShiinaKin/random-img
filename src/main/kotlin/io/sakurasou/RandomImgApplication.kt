package io.sakurasou

import io.sakurasou.config.RandomImgConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RandomImgConfig::class)
class RandomImgApplication

fun main(args: Array<String>) {
    runApplication<RandomImgApplication>(*args)
}
