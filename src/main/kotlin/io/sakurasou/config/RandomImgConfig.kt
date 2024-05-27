package io.sakurasou.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author mashirot
 * 2024/5/15 21:01
 */
@ConfigurationProperties(prefix = "random-img")
class RandomImgConfig {
    lateinit var basicAuth: BasicAuth
    lateinit var s3: S3
    lateinit var persistenceReferer: List<String>
    lateinit var probability: String

    data class BasicAuth(
        val username: String,
        val password: String
    )

    data class S3(
        val accessId: String,
        val accessToken: String,
        val endpoint: String,
        val region: String,
        val bucketName: String,
        val manualUploadBucketName: String,
        val cdnUrl: String
    )
}