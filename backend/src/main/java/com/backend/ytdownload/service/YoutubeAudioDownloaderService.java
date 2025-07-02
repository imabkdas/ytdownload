package com.backend.ytdownload.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.logging.Logger;

@Service
public class YoutubeAudioDownloaderService {

    public byte[] downloadAudio(String url) throws IOException, InterruptedException {
        Logger logger = Logger.getLogger(YouTubeDownloadService.class.getName());

        // Create a temporary file for the audio download
        File tempFile = File.createTempFile("audio", ".mp3");
        logger.info("Temporary file created: " + tempFile.getAbsolutePath());

        // Build the yt-dlp command to download audio only in MP3 format
//        ProcessBuilder processBuilder = new ProcessBuilder(
//                "yt-dlp",
//                "-f", "bestaudio/best",
//                "--extract-audio",
//                "--audio-format", "mp3",
//                "-o", tempFile.getAbsolutePath(),
//                url
//        );

        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp",
                "-x",
                "--audio-format",
                "mp3",
                "--audio-format", "mp3",
                "-o", tempFile.getAbsolutePath(),
                url

        );

        // Log the command being executed
        String command = String.join(" ", processBuilder.command());
        logger.info("Executing yt-dlp command: " + command);

        // Start the yt-dlp process
        Process process = processBuilder.start();

        // Capture and log the output and error streams
        captureProcessOutput(process, logger);

        // Wait for the yt-dlp process to complete
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
        }

        // Check if the MP3 file has been created successfully
        if (!tempFile.exists() || tempFile.length() == 0) {
            throw new IOException("MP3 file is empty or not found. Possible issues with yt-dlp or the provided URL.");
        }

        // Read the MP3 file into a byte array
        byte[] audioBytes = new byte[(int) tempFile.length()];
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            fis.read(audioBytes);
        }

        // Log the file size and delete the temporary file after use
        logger.info("MP3 File size: " + audioBytes.length + " bytes");
        if (!tempFile.delete()) {
            logger.warning("Temporary file deletion failed: " + tempFile.getAbsolutePath());
        }

        return audioBytes;
    }

    private void captureProcessOutput(Process process, Logger logger) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info(line);
                }
            } catch (IOException e) {
                logger.severe("Error capturing process output: " + e.getMessage());
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.severe(line);
                }
            } catch (IOException e) {
                logger.severe("Error capturing process error output: " + e.getMessage());
            }
        }).start();
    }
}
