package io.sakurasou.interceptor

import io.sakurasou.annotation.BasicAuth
import io.sakurasou.config.RandomImgConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * @author mashirot
 * 2024/5/21 20:09
 */
@Component
class BasicAuthorizationInterceptor(
    config: RandomImgConfig
) : HandlerInterceptor {
    private val basicAuth = config.basicAuth

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val effectivePath = setOf(
            "/", "/remote-upload", "/delete-all",
            "/cache/select", "/cache/random",
            "/random", "/health"
        )
        if (!effectivePath.contains(request.servletPath)) {
            response.status = HttpStatus.FORBIDDEN.value()
            return false
        }
        if (handler is HandlerMethod) {
            if (!handler.hasMethodAnnotation(BasicAuth::class.java)) return true
            val authorization = request.getHeader("Authorization") ?: run {
                response.status = HttpStatus.UNAUTHORIZED.value()
                return false
            }
            val prefix = "Basic "
            if (!authorization.startsWith(prefix)) {
                response.status = HttpStatus.UNAUTHORIZED.value()
                return false
            }
            val base64Cred = authorization.substring(prefix.length)
            val decodeBase64 = Base64.decodeBase64(base64Cred).decodeToString().split(":")
            val carryUsername = decodeBase64[0]
            val carryPassword = decodeBase64[1]
            val sha256EncodePasswd = DigestUtils.sha256Hex(carryPassword)

            if (basicAuth.username != carryUsername || basicAuth.password != sha256EncodePasswd) {
                response.status = HttpStatus.UNAUTHORIZED.value()
                return false
            }
        }
        return true
    }
}