package com.chatterbox.chat.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CloudinaryMediaService {
    private static final Logger logger = LoggerFactory.getLogger(CloudinaryMediaService.class);
    private final Cloudinary cloudinary;
    private final String apiKey;
    private final String apiSecret;

    public CloudinaryMediaService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            this.cloudinary = null;
        } else {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true));
        }
    }

    public Map<String, String> upload(MultipartFile file) {
        if (cloudinary == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "auto",
                            "folder", "chatterbox",
                            "use_filename", true,
                            "unique_filename", true,
                            "overwrite", false));
            Object secureUrl = result.get("secure_url");
            if (!(secureUrl instanceof String url) || url.isBlank()) {
                throw new IOException("Cloudinary did not return a secure media URL");
            }
            String originalName = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
            return Map.of("url", url, "name", originalName);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            String diagnostic = String.valueOf(exception.getMessage())
                    .replace(apiKey, "[redacted]")
                    .replace(apiSecret, "[redacted]");
            logger.error("Cloudinary upload failed ({}): {}", exception.getClass().getSimpleName(), diagnostic);
            if (diagnostic.toLowerCase().contains("missing permissions")
                || diagnostic.toLowerCase().contains("actions=[create]")) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Cloudinary accepted these credentials, but this API key lacks asset create/upload permission. Enable upload/create for this key in Cloudinary Console, or use an API key with upload permission, then update .env and restart the backend.",
                exception);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary rejected the upload. Check the Cloud name, API key, API secret, and account upload permissions, then restart the backend.",
                    exception);
        }
    }
}
