package io.sakurasou.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sakurasou.exception.ImageFetchException
import io.sakurasou.exception.WrongParameterException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.result.view.ViewResolver
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono

/**
 * @author mashirot
 * 2024/5/16 13:23
 */
@ControllerAdvice
@Order(-1)
class GlobalExceptionHandler : WebExceptionHandler {

    private val logger = KotlinLogging.logger { this::class.java }

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {

        val serverResponseMono = when (ex) {
            is WrongParameterException -> {
                ServerResponse.status(HttpStatus.BAD_REQUEST).bodyValue(ex.message!!)
            }

            is ImageFetchException -> {
                ServerResponse.status(HttpStatus.NOT_FOUND).bodyValue(ex.message!!)
            }

            else -> {
                logger.error(ex) { "handle server error: $ex" }
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(ex.message!!)
            }
        }

        return serverResponseMono.flatMap { it.writeTo(exchange, ResponseContextInstance) }.then()

    }

    private object ResponseContextInstance : ServerResponse.Context {
        val strategies: HandlerStrategies = HandlerStrategies.withDefaults()
        override fun messageWriters(): List<HttpMessageWriter<*>> {
            return strategies.messageWriters()
        }

        override fun viewResolvers(): List<ViewResolver> {
            return strategies.viewResolvers()
        }
    }
}