package com.backend.ytdownload.controller;

import com.backend.ytdownload.service.YouTubeDownloadService;
import com.backend.ytdownload.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;

@RestController
@RequestMapping("/api/video/download")
public class YouTubeDownloadController {

    private static final Logger log = LoggerFactory.getLogger(YouTubeDownloadController.class);
    private final YouTubeDownloadService youTubeDownloadService;

    public YouTubeDownloadController(YouTubeDownloadService youTubeDownloadService) {
        this.youTubeDownloadService = youTubeDownloadService;
    }

    @PostMapping
    public ResponseEntity<StreamingResponseBody> downloadVideo(@RequestParam String url, @RequestParam String format, @RequestParam String quality) {
        File videoFile = null;
        try {
            // Validate input parameters
            ValidationUtil.validateUrl(url);
            ValidationUtil.validateFormat(format);
            ValidationUtil.validateQuality(quality);
            
            log.info("Download request received - URL: {}, Format: {}, Quality: {}", url, format, quality);
            
            // Get the video file (not loaded in memory)
            videoFile = youTubeDownloadService.downloadVideo(url, format, quality);
            
            // Create final reference for lambda
            final File finalVideoFile = videoFile;
            final long fileLength = videoFile.length();

            // Create streaming response body to avoid loading entire file in memory
            StreamingResponseBody responseBody = outputStream -> {
                try (FileInputStream fis = new FileInputStream(finalVideoFile)) {
                    byte[] buffer = new byte[8192]; // 8KB buffer
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                } finally {
                    // Delete temp file after streaming completes
                    if (finalVideoFile != null && finalVideoFile.exists()) {
                        try {
                            if (finalVideoFile.delete()) {
                                log.info("Temporary file deleted after streaming: {}", finalVideoFile.getAbsolutePath());
                            } else {
                                log.warn("Failed to delete temp file after streaming: {} (will be deleted on JVM shutdown)", finalVideoFile.getAbsolutePath());
                                finalVideoFile.deleteOnExit();
                            }
                        } catch (Exception e) {
                            log.error("Error deleting temporary file: {}", finalVideoFile.getAbsolutePath(), e);
                            finalVideoFile.deleteOnExit();
                        }
                    }
                }
            };

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=video." + format);
            headers.setContentLength(fileLength);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("video/" + format))
                    .body(responseBody);
                    
        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            cleanupFile(videoFile);
            return ResponseEntity.badRequest()
                    .body((StreamingResponseBody) outputStream -> outputStream.write(("Invalid request parameters: " + e.getMessage()).getBytes()));
        } catch (Exception e) {
            log.error("Error processing download request", e);
            cleanupFile(videoFile);
            // Log error for debugging (don't expose internal details to client)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body((StreamingResponseBody) outputStream -> outputStream.write("An error occurred while processing your request".getBytes()));
        }
    }
    
    private void cleanupFile(File file) {
        if (file != null && file.exists()) {
            try {
                if (file.delete()) {
                    log.info("Temporary file deleted on error: {}", file.getAbsolutePath());
                } else {
                    log.warn("Failed to delete temp file on error: {} (will be deleted on JVM shutdown)", file.getAbsolutePath());
                    file.deleteOnExit();
                }
            } catch (Exception e) {
                log.error("Error deleting temporary file on error: {}", file.getAbsolutePath(), e);
                file.deleteOnExit();
            }
        }
    }
}