package com.backend.ytdownload.controller;

import com.backend.ytdownload.service.YoutubeAudioDownloaderService;
import com.backend.ytdownload.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/audio/download")
public class AudioDownloadController {

    private static final Logger log = LoggerFactory.getLogger(AudioDownloadController.class);
    private final YoutubeAudioDownloaderService youtubeAudioDownloaderService;

    public AudioDownloadController(YoutubeAudioDownloaderService youtubeAudioDownloaderService) {
        this.youtubeAudioDownloaderService = youtubeAudioDownloaderService;
    }

    @PostMapping
    public ResponseEntity<StreamingResponseBody> downloadVideo(@RequestParam String url, @RequestParam String format) {
        File audioFile = null;
        try {
            // Validate input parameters
            ValidationUtil.validateUrl(url);
            if (format == null || !format.equalsIgnoreCase("mp3")) {
                throw new IllegalArgumentException("Format must be 'mp3' for audio downloads");
            }
            
            log.info("Audio download request received - URL: {}, Format: {}", url, format);
            
            // Get the audio file (not loaded in memory)
            audioFile = youtubeAudioDownloaderService.downloadAudio(url);
            
            // Create final reference for lambda
            final File finalAudioFile = audioFile;
            final long fileLength = audioFile.length();
            
            // Create streaming response body to avoid loading entire file in memory
            StreamingResponseBody responseBody = outputStream -> {
                try (FileInputStream fis = new FileInputStream(finalAudioFile)) {
                    byte[] buffer = new byte[8192]; // 8KB buffer
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                } finally {
                    // Delete temp file after streaming completes
                    if (finalAudioFile != null && finalAudioFile.exists()) {
                        try {
                            if (finalAudioFile.delete()) {
                                log.info("Temporary file deleted after streaming: {}", finalAudioFile.getAbsolutePath());
                            } else {
                                log.warn("Failed to delete temp file after streaming: {} (will be deleted on JVM shutdown)", finalAudioFile.getAbsolutePath());
                                finalAudioFile.deleteOnExit();
                            }
                        } catch (Exception e) {
                            log.error("Error deleting temporary file: {}", finalAudioFile.getAbsolutePath(), e);
                            finalAudioFile.deleteOnExit();
                        }
                    }
                }
            };
            
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audio." + format);
            headers.setContentLength(fileLength);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("audio/" + format))
                    .body(responseBody);
                    
        } catch (IllegalArgumentException e) {
            log.error("Invalid request parameters: {}", e.getMessage());
            cleanupFile(audioFile);
            return ResponseEntity.badRequest()
                    .body((StreamingResponseBody) outputStream -> outputStream.write(("Invalid request parameters: " + e.getMessage()).getBytes()));
        } catch (IOException | InterruptedException e) {
            log.error("Error processing audio download request", e);
            cleanupFile(audioFile);
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
