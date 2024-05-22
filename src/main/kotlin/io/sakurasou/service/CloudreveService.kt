package io.sakurasou.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.config.OkHttpConfig.Companion.CLOUDREVE_SESSION
import io.sakurasou.config.OkHttpConfig.Companion.cloudreveHttpUrl
import io.sakurasou.config.OkHttpConfig.Companion.cookieMap
import io.sakurasou.config.RandomImgConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Service

/**
 * @author mashirot
 * 2024/5/22 22:34
 */
@Service
class CloudreveService(
    objectMapper: ObjectMapper,
    config: RandomImgConfig,
    private val httpClient: OkHttpClient,
) {
    private val logger = KotlinLogging.logger { this::class.java }

    private val cloudreve = config.cloudreve
    private val cloudreveUri = cloudreve.url + "/api/v3"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val cloudreveLoginRequestBody =
        objectMapper.writeValueAsString(
            CloudreveLoginRequest(cloudreve.username, cloudreve.password)
        ).toRequestBody(jsonMediaType)
    private val cloudreveSyncRequestBody =
        objectMapper.writeValueAsString(
            CloudreveSyncRequest(
                cloudreve.imageAccountUid, cloudreve.imagePolicyId,
                cloudreve.s3Src, cloudreve.cloudreveDist, cloudreve.recursive
            )
        ).toRequestBody(jsonMediaType)

    private val loginRequest = Request.Builder()
        .url("$cloudreveUri/user/session")
        .post(cloudreveLoginRequestBody)
        .build()
    private val syncRequest = Request.Builder()
        .url("$cloudreveUri/admin/task/import")
        .post(cloudreveSyncRequestBody)
        .build()

    suspend fun sync2Cloudreve() {
        withContext(Dispatchers.IO) {
            if (!httpClient.hasCloudreveSession()) {
                httpClient.newCall(loginRequest).execute().use {
                    if (!it.isSuccessful) {
                        logger.warn { "cloudreve login failed, ${it.code} ${it.message}" }
                        return@withContext
                    }
                    logger.debug { "cloudreve login success" }
                }
            }
            httpClient.newCall(syncRequest).execute().use {
                if (!it.isSuccessful) {
                    logger.warn { "cloudreve sync failed, ${it.code} ${it.message}" }
                    return@withContext
                }
                logger.debug { "cloudreve sync success" }
            }
        }
    }

    fun OkHttpClient.hasCloudreveSession(): Boolean =
        cookieMap[cloudreveHttpUrl]?.any {
            CLOUDREVE_SESSION == it.name
                    && it.expiresAt + 10000 > System.currentTimeMillis()
        } ?: false

    private data class CloudreveLoginRequest(
        val userName: String,
        val password: String,
        val captchaCode: String = ""
    )

    private data class CloudreveSyncRequest(
        val uid: Int,
        val policyId: Int,
        val src: String,
        val dst: String,
        val recursive: Boolean
    )
}