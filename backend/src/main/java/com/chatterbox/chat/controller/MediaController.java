package com.chatterbox.chat.controller;

import com.chatterbox.chat.service.CloudinaryMediaService;
import com.chatterbox.chat.security.JwtService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final CloudinaryMediaService mediaService;
    private final JwtService jwtService;

    public MediaController(CloudinaryMediaService mediaService, JwtService jwtService) {
        this.mediaService = mediaService;
        this.jwtService = jwtService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String multipartToken) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : multipartToken;
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            boolean subjectPresent = jwtService.parse(token).getSubject() != null;
            if (!subjectPresent) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication token");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired authentication token");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        return mediaService.upload(file);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleUploadError(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Media upload failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }
}
