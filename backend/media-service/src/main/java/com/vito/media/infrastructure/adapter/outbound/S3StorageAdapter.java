package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.port.outbound.StoragePort;
import com.vito.media.infrastructure.config.S3Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Component
public class S3StorageAdapter implements StoragePort {

    private final String bucketName;
    private final String publicBaseUrl;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3StorageAdapter(S3Settings settings) {
        this.bucketName = settings.bucket();
        this.publicBaseUrl = settings.publicBaseUrl();

        URI endpoint = URI.create(settings.endpoint());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()));
        Region region = Region.of(settings.region());
        // El presigner necesita el mismo estilo de URL que el cliente: si no, firma
        // <bucket>.<host> y SeaweedFS no lo resuelve
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(settings.pathStyle())
                .build();

        // Desde el SDK 2.30 los PUT llevan checksum CRC en un cuerpo aws-chunked; SeaweedFS
        // guarda ese cuerpo tal cual ("4;chunk-signature=...") y el archivo queda corrupto.
        // WHEN_REQUIRED: checksum solo en las operaciones que lo exigen, como los S3 no-AWS.
        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(s3Configuration)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(s3Configuration)
                .build();

        if (settings.createBucket()) {
            createBucketIfNotExists();
        }
    }

    @Override
    public String uploadFile(String key, byte[] data, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();

            s3Client.putObject(putRequest, 
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));

            log.debug("Uploaded file to S3: bucket={}, key={}", bucketName, key);
            return getPublicUrl(key);

        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public byte[] downloadFile(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getRequest).readAllBytes();

        } catch (Exception e) {
            log.error("Failed to download file from S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.debug("Deleted file from S3: bucket={}, key={}", bucketName, key);

        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, long expirationMinutes) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getRequest)
                    .build();

            return presigner.presignGetObject(presignRequest).url().toString();

        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    @Override
    public String getPublicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    private void createBucketIfNotExists() {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headRequest);
            log.debug("Bucket already exists: {}", bucketName);
        } catch (NoSuchBucketException e) {
            CreateBucketRequest createRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.createBucket(createRequest);
            log.info("Created bucket: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to check/create bucket: {}", e.getMessage(), e);
        }
    }
}
