package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.infrastructure.config.S3Settings;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-I10: el adaptador funciona contra la misma imagen de SeaweedFS del compose local,
 * con credenciales reales (sin acceso anónimo), path-style y URLs firmadas.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3StorageAdapterIntegrationTest {

    private static final String ACCESS_KEY = "it_access";
    private static final String SECRET_KEY = "it_secret_key_for_tests";
    private static final int S3_PORT = 8333;

    @Container
    private static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>("chrislusf/seaweedfs:3.97")
            .withCopyToContainer(Transferable.of("""
                    {"identities":[{"name":"vito","credentials":[{"accessKey":"%s","secretKey":"%s"}],
                    "actions":["Admin","Read","Write","List","Tagging"]}]}
                    """.formatted(ACCESS_KEY, SECRET_KEY)), "/etc/seaweedfs/s3.json")
            .withCommand("server", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json")
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/")
                    .forPort(S3_PORT)
                    .forStatusCodeMatching(code -> code < 500)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static String endpoint() {
        return "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(S3_PORT);
    }

    private static S3Settings settings(String bucket, boolean createBucket, String publicUrl) {
        return new S3Settings(endpoint(), "us-east-1", ACCESS_KEY, SECRET_KEY, bucket,
                true, createBucket, publicUrl);
    }

    @Test
    void uploadsDownloadsAndDeletesInBucketCreatedOnStartup() {
        S3StorageAdapter adapter = new S3StorageAdapter(settings("it-roundtrip", true, ""));
        byte[] data = "hola mosaico".getBytes(StandardCharsets.UTF_8);

        String url = adapter.uploadFile("events/e1/u1/a.txt", data, "text/plain");

        assertThat(url).isEqualTo(endpoint() + "/it-roundtrip/events/e1/u1/a.txt");
        assertThat(adapter.downloadFile("events/e1/u1/a.txt")).isEqualTo(data);

        adapter.deleteFile("events/e1/u1/a.txt");
        assertThatThrownBy(() -> adapter.downloadFile("events/e1/u1/a.txt"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void presignedUrlIsPathStyleAndServesTheObject() throws Exception {
        S3StorageAdapter adapter = new S3StorageAdapter(settings("it-presign", true, ""));
        byte[] data = "foto".getBytes(StandardCharsets.UTF_8);
        adapter.uploadFile("k/photo.jpg", data, "image/jpeg");

        String presigned = adapter.generatePresignedUrl("k/photo.jpg", 5);

        assertThat(presigned).startsWith(endpoint() + "/it-presign/k/photo.jpg?");
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(presigned)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(data);
    }

    @Test
    void doesNotCreateBucketWhenDisabled() {
        // Así arranca en R2: el token no puede crear buckets y no debe intentarlo
        new S3StorageAdapter(settings("it-not-created", false, ""));
        new S3StorageAdapter(settings("it-created", true, ""));

        try (S3Client raw = rawClient()) {
            assertThatThrownBy(() -> raw.headBucket(b -> b.bucket("it-not-created")))
                    .isInstanceOf(NoSuchBucketException.class);
            raw.headBucket(b -> b.bucket("it-created"));
        }
    }

    private static S3Client rawClient() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
    }

    @Test
    void publicUrlUsesConfiguredBase() {
        S3StorageAdapter adapter = new S3StorageAdapter(
                settings("it-public", true, "https://media.example.com/"));

        assertThat(adapter.getPublicUrl("events/e1/a.jpg"))
                .isEqualTo("https://media.example.com/events/e1/a.jpg");
    }
}
