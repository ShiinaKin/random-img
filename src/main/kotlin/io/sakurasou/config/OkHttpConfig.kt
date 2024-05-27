package io.sakurasou.config

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author mashirot
 * 2024/5/14 22:33
 */
@Configuration
class OkHttpConfig {
    companion object {
        const val CLOUDREVE_SESSION = "cloudreve-session"
        var cloudreveHttpUrl: HttpUrl? = null
        val cookieMap = mutableMapOf<HttpUrl, MutableList<Cookie>>()
    }

    @Bean
    fun httpClient() = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                cookieMap[url]?.removeIf { it.expiresAt < System.currentTimeMillis() }
                return cookieMap[url] ?: emptyList()
            }

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                var isCloudreve = false
                cookies.forEach { cookie ->
                    if (CLOUDREVE_SESSION == cookie.name) {
                        isCloudreve = true
                        cloudreveHttpUrl = url
                        cookieMap[url]?.let { list ->
                            list.removeIf { CLOUDREVE_SESSION == it.name }
                            list.add(cookie)
                        } ?: cookieMap.put(url, mutableListOf(cookie))
                    }
                }
                if (!isCloudreve) cookieMap[url] = cookies.toMutableList()
            }
        }).build()

}