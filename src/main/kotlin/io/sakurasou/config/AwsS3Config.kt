package io.sakurasou.config

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author mashirot
 * 2024/5/15 11:50
 */
@Configuration
class AwsS3Config(
    config: RandomImgConfig
) {
    private val s3 = config.s3

    @Bean
    fun s3Cred(): BasicAWSCredentials {
        return BasicAWSCredentials(s3.accessId, s3.accessToken)
    }

    @Bean
    fun s3Client(awsCred: AWSCredentials): AmazonS3 = AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(s3.endpoint, s3.region))
        .withCredentials(AWSStaticCredentialsProvider(awsCred))
        .withPathStyleAccessEnabled(true)
        .build()
}