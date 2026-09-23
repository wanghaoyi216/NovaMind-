package com.novamind.media.config;

import com.novamind.media.storage.IFileStorage;
import com.novamind.media.storage.minio.MinioFileStorage;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "MINIO")
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "MINIO")
    public IFileStorage minioFileStorage(MinioClient minioClient, MinioProperties properties) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
        }
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(properties.getBucket())
                .config(publicReadPolicy(properties.getBucket()))
                .build());
        return new MinioFileStorage(minioClient, properties);
    }

    private String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }
}
