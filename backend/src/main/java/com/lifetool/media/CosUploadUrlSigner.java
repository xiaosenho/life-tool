package com.lifetool.media;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.util.Date;

@Component
public class CosUploadUrlSigner {
    private static final Logger log = LoggerFactory.getLogger(CosUploadUrlSigner.class);

    private final MediaConfig config;

    public CosUploadUrlSigner(MediaConfig config) {
        this.config = config;
    }

    public String generatePutUrl(String objectKey, Instant expiresAt) {
        if (!config.isCosSigningEnabled()) {
            return buildMockUploadUrl(objectKey);
        }

        COSCredentials credentials = new BasicCOSCredentials(
                config.getCosSecretId(),
                config.getCosSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getCosRegion()));

        COSClient cosClient = new COSClient(credentials, clientConfig);
        try {
            URL url = cosClient.generatePresignedUrl(
                    config.getCosBucket(),
                    objectKey,
                    Date.from(expiresAt),
                    HttpMethodName.PUT);
            log.info(
                    "Generated COS PUT signed URL. bucket={}, region={}, objectKey={}, now={}, expiresAt={}, url={}",
                    config.getCosBucket(),
                    config.getCosRegion(),
                    objectKey,
                    Instant.now(),
                    expiresAt,
                    url);
            return url.toString();
        } finally {
            cosClient.shutdown();
        }
    }

    public String generateGetUrl(String objectKey, Instant expiresAt) {
        if (!config.isCosSigningEnabled()) {
            return buildMockUploadUrl(objectKey);
        }

        COSCredentials credentials = new BasicCOSCredentials(
                config.getCosSecretId(),
                config.getCosSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getCosRegion()));

        COSClient cosClient = new COSClient(credentials, clientConfig);
        try {
            URL url = cosClient.generatePresignedUrl(
                    config.getCosBucket(),
                    objectKey,
                    Date.from(expiresAt),
                    HttpMethodName.GET);
            log.info(
                    "Generated COS GET signed URL. bucket={}, region={}, objectKey={}, now={}, expiresAt={}, url={}",
                    config.getCosBucket(),
                    config.getCosRegion(),
                    objectKey,
                    Instant.now(),
                    expiresAt,
                    url);
            return url.toString();
        } finally {
            cosClient.shutdown();
        }
    }

    private static String buildMockUploadUrl(String objectKey) {
        return "http://localhost:8080/mock-cos/" + objectKey;
    }
}
