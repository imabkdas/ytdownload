package com.backend.ytdownload.controller;

import com.backend.ytdownload.service.AudioDownloaderService;
import com.backend.ytdownload.service.YouTubeDownloadService;
import com.backend.ytdownload.service.YoutubeAudioDownloaderService;
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
@RequestMapping("/api/audio/download")
public class AudioDownloadController {

    private final AudioDownloaderService audioDownloaderService;
    private final YoutubeAudioDownloaderService youtubeAudioDownloaderService;

    public AudioDownloadController(AudioDownloaderService audioDownloaderService, YoutubeAudioDownloaderService youtubeAudioDownloaderService) {
        this.audioDownloaderService = audioDownloaderService;
        this.youtubeAudioDownloaderService = youtubeAudioDownloaderService;
    }

    @PostMapping
    public ResponseEntity<?> downloadVideo(@RequestParam String url, String format) {
        try {
//            byte[] audioBytes = audioDownloaderService.downloadAudio(url, format);
            byte[] audioBytes = youtubeAudioDownloaderService.downloadAudio(url);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "audio/" + format);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audio." + format);
            return new ResponseEntity<>(audioBytes, headers, HttpStatus.OK);
        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
