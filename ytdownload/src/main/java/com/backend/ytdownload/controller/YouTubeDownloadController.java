package com.backend.ytdownload.controller;

import com.backend.ytdownload.service.YouTubeDownloadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/video/download")
public class YouTubeDownloadController {

    private final YouTubeDownloadService youTubeDownloadService;

    public YouTubeDownloadController(YouTubeDownloadService youTubeDownloadService) {
        this.youTubeDownloadService = youTubeDownloadService;
    }

    @PostMapping
    public ResponseEntity<?> downloadVideo(@RequestParam String url, @RequestParam String format) {
        try {
            byte[] video = youTubeDownloadService.downloadVideo(url, format);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=video." + format);
            headers.setContentType(MediaType.parseMediaType("   `video/" + format)); // Set content type based on format
            headers.setContentLength(video.length);

            return new ResponseEntity<>(video, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace(); // Print stack trace for debugging
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}