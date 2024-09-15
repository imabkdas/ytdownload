package com.backend.ytdownload.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.logging.Logger;

@Service
public class YouTubeDownloadService {

    public byte[] downloadVideo(String url, String format, String quality) throws IOException, InterruptedException {
        Logger logger = Logger.getLogger(YouTubeDownloadService.class.getName());

        // Create a temporary file for the download
        File tempFile = File.createTempFile("video", "." + format);
        logger.info("Temporary file created: " + tempFile.getAbsolutePath());

        // Build the yt-dlp command
        ProcessBuilder processBuilder;
        if (quality.equalsIgnoreCase("HQ")) {
            processBuilder = new ProcessBuilder(
                    "yt-dlp", url, "-o", tempFile.getAbsolutePath()
            );
        } else if (quality.equalsIgnoreCase("720")) {
            processBuilder = new ProcessBuilder(
                    "yt-dlp", "-f", "bestvideo[height<=720]+bestaudio/best[height<=720]", "-o", tempFile.getAbsolutePath(), url
            );
        } else if (quality.equalsIgnoreCase("480")) {
            processBuilder = new ProcessBuilder(
                    "yt-dlp", "-f", "bestvideo[height<=480]+bestaudio/best[height<=480]", "-o", tempFile.getAbsolutePath(), url
            );
        } else {
            throw new IllegalArgumentException("Unsupported quality: " + quality);
        }


        logger.info("Executing yt-dlp command: " + String.join(" ", processBuilder.command()));

        // Start the yt-dlp process
        Process process = processBuilder.start();

        // Capture and log the output and error streams
        captureProcessOutput(process, logger);

        // Wait for the yt-dlp process to complete
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

//        File mp3File = File.createTempFile("audio",".mp3");

        // Build the ffmpeg command to convert .webm to .mp4 with H.264 and AAC
        ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder(
//                "ffmpeg", "-i", mergedFile.getAbsolutePath(), "-vn","-c:a", "aac", "-y", mp4File.getAbsolutePath()
                "ffmpeg", "-i", mergedFile.getAbsolutePath(), "-c:v", "libx264", "-c:a", "aac", "-y", mp4File.getAbsolutePath()
        );

        logger.info("Executing ffmpeg command: " + String.join(" ", ffmpegProcessBuilder.command()));

        // Start the ffmpeg process
        Process ffmpegProcess = ffmpegProcessBuilder.start();

        // Capture and log the output and error streams of the ffmpeg process
        captureProcessOutput(ffmpegProcess, logger);

        // Wait for the ffmpeg process to complete
        int ffmpegExitCode = ffmpegProcess.waitFor();
        if (ffmpegExitCode != 0) {
            throw new RuntimeException("ffmpeg failed with exit code " + ffmpegExitCode);
        }

        // Read the converted mp4 file into a byte array
        byte[] videoBytes = new byte[(int) mp4File.length()];
        try (FileInputStream fis = new FileInputStream(mp4File)) {
            fis.read(videoBytes);
        }

        // Log the file size and delete the temporary files after use
        logger.info("MP4 File size: " + videoBytes.length + " bytes");
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


