package com.lifetool.media;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.region.Region;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.util.Date;

@Component
public class CosUploadUrlSigner {

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
            return url.toString();
        } finally {
            cosClient.shutdown();
        }
    }

    private static String buildMockUploadUrl(String objectKey) {
        return "http://localhost:8080/mock-cos/" + objectKey;
    }
}
