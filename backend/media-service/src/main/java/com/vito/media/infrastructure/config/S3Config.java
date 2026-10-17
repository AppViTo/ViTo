package com.vito.media.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    // @Value (y no @ConfigurationProperties) para que un secreto ausente falle al arrancar
    @Bean
    public S3Settings s3Settings(
            @Value("${s3.endpoint}") String endpoint,
            @Value("${s3.region}") String region,
            @Value("${s3.access-key}") String accessKey,
            @Value("${s3.secret-key}") String secretKey,
            @Value("${s3.bucket}") String bucket,
            @Value("${s3.path-style}") boolean pathStyle,
            @Value("${s3.create-bucket}") boolean createBucket,
            @Value("${s3.public-url}") String publicUrl) {
        return new S3Settings(endpoint, region, accessKey, secretKey, bucket,
                pathStyle, createBucket, publicUrl);
    }
}
