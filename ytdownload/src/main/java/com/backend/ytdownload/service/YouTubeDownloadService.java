package com.backend.ytdownload.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.logging.Logger;

@Service
public class YouTubeDownloadService {

    public byte[] downloadVideo(String url, String format) throws IOException, InterruptedException {
        Logger logger = Logger.getLogger(YouTubeDownloadService.class.getName());

        // Create a temporary file for the download
        File tempFile = File.createTempFile("video", "." + format);
        logger.info("Temporary file created: " + tempFile.getAbsolutePath());

        // Build the yt-dlp command
        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp", url, "-o", tempFile.getAbsolutePath()
//                "yt-dlp", url, "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/mp4", "-o", tempFile.getAbsolutePath()
        );

        logger.info("Executing yt-dlp command: " + String.join(" ", processBuilder.command()));

        // Start the process
        Process process = processBuilder.start();

        // Capture and log the output and error streams
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("yt-dlp output: " + line);
            }
            while ((line = errorReader.readLine()) != null) {
                logger.severe("yt-dlp error: " + line);
            }
        }

        // Wait for the process to complete and check the exit code
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
        }

        // Verify if the merged file has been created
        File mergedFile = new File(tempFile.getAbsolutePath() + ".webm");
        if (!mergedFile.exists() || mergedFile.length() == 0) {
            throw new IOException("Merged file is empty or not found. Possible issues with yt-dlp or the provided URL.");
        }

        // Create a new temporary file for the converted mp4 file
        File mp4File = File.createTempFile("video", ".mp4");

        // Build the ffmpeg command to convert .webm to .mp4 with H.264 and AAC
        ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder(
                "ffmpeg", "-i", mergedFile.getAbsolutePath(), "-c:v", "libx264", "-c:a", "aac", mp4File.getAbsolutePath()
        );

        logger.info("Executing ffmpeg command: " + String.join(" ", ffmpegProcessBuilder.command()));

        // Start the ffmpeg process
        Process ffmpegProcess = ffmpegProcessBuilder.start();

        // Capture and log the output and error streams of the ffmpeg process
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(ffmpegProcess.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("ffmpeg output: " + line);
            }
            while ((line = errorReader.readLine()) != null) {
                logger.severe("ffmpeg error: " + line);
            }
        }

        // Wait for the ffmpeg process to complete and check the exit code
        int ffmpegExitCode = ffmpegProcess.waitFor();
        if (ffmpegExitCode != 0) {
            throw new RuntimeException("ffmpeg failed with exit code " + ffmpegExitCode);
        }

        // Read the merged file into a byte array
        byte[] videoBytes = new byte[(int) mergedFile.length()];
        try (FileInputStream fis = new FileInputStream(mergedFile)) {
            fis.read(videoBytes);
        }

        // Log the file size and delete the temporary files after use
        logger.info("MP4 File size: " + videoBytes.length + " bytes");
//        logger.info("File size: " + videoBytes.length + " bytes");
        if (!tempFile.delete()) {
            logger.warning("Temporary file deletion failed: " + tempFile.getAbsolutePath());
        }
        if (!mergedFile.delete()) {
            logger.warning("Merged file deletion failed: " + mergedFile.getAbsolutePath());
        }
        if (!mp4File.delete()) {
            logger.warning("Temporary mp4 file deletion failed: " + mp4File.getAbsolutePath());
        }

        return videoBytes;
    }


}
