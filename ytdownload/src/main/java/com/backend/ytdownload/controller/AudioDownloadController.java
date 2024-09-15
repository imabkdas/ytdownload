package com.backend.ytdownload.controller;

import com.backend.ytdownload.service.AudioDownloaderService;
import com.backend.ytdownload.service.YouTubeDownloadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/mp3/download")
public class AudioDownloadController {

    private final AudioDownloaderService audioDownloaderService;

    public AudioDownloadController(AudioDownloaderService audioDownloaderService) {
        this.audioDownloaderService = audioDownloaderService;
    }

    @PostMapping
    public ResponseEntity<?> downloadVideo(@RequestParam String url, @RequestParam String format) {
        try {
            byte[] audioBytes = audioDownloaderService.downloadAudio(url, format);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "audio/" + format);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audio." + format);
            return new ResponseEntity<>(audioBytes, headers, HttpStatus.OK);
        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
