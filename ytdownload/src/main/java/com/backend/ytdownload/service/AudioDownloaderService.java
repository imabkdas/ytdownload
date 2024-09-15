package com.backend.ytdownload.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Service
public class AudioDownloaderService {

    public byte[] downloadAudio(String url, String format) throws IOException, InterruptedException {
        Logger logger = Logger.getLogger(YouTubeDownloadService.class.getName());
        long startTime = System.currentTimeMillis();

        // Create a temporary file for the download
        File tempFile = File.createTempFile("audio", "." + format);
        logger.info("Temporary file created: " + tempFile.getAbsolutePath());

        // Build the yt-dlp command with verbose logging
        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp",
                "-v",                     // Verbose logging
                "-x",                     // Extract audio only
                "--audio-format ",  format,// Desired audio format
//                "/opt/homebrew/bin/ffmpeg","/opt/homebrew/bin/ffprobe",
                url,                      // YouTube video URL
                "-o", tempFile.getAbsolutePath() // Output file path
        );

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

        // Read the converted audio file into a byte array
        byte[] audioBytes;
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            audioBytes = fis.readAllBytes();
        }

        // Log the file size and delete the temporary file after use
        logger.info("MP3 File size: " + audioBytes.length + " bytes");
        if (!tempFile.delete()) {
            logger.warning("Temporary file deletion failed: " + tempFile.getAbsolutePath());
        }

        long endTime = System.currentTimeMillis();
        logger.info("Total time taken: " + (endTime - startTime) + " milliseconds");

        return audioBytes;
    }




    private static  void printTotalTimeTaken(long startTime, long endTime, String processName){
        long minutes = ((endTime - startTime)/1000)/60;
        long seconds = ((endTime - startTime)/1000)%60;
        System.out.println("Total " + processName +" Time Taken : " + minutes + "  minutes " + seconds +" seconds");
    }
//    public byte[] downloadAudio(String url, String format) throws IOException, InterruptedException {
//        Logger logger = Logger.getLogger(YouTubeDownloadService.class.getName());
//        long startTime = System.currentTimeMillis();
//        // Create a temporary file for the download
//        File tempFile = File.createTempFile("audio", "." + format);
//        logger.info("Temporary file created: " + tempFile.getAbsolutePath());
//
//        // Build the yt-dlp command
//        ProcessBuilder processBuilder = new ProcessBuilder(
////                "yt-dlp", url, "-o", tempFile.getAbsolutePath()
//                "yt-dlp", "-x", "--audio-format", "mp3", tempFile.getAbsolutePath()
//        );
//
//        logger.info("Executing yt-dlp command: " + String.join(" ", processBuilder.command()));
//
//        // Start the yt-dlp process
//        Process process = processBuilder.start();
//
//        // Capture and log the output and error streams
////        captureProcessOutput(process, logger);
//
//        // Wait for the yt-dlp process to complete
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//            throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
//        }
//
//        // Verify if the merged file has been created
////        File mergedFile = new File(tempFile.getAbsolutePath() + ".webm");
//        File mergedFile = new File(tempFile.getAbsolutePath() + ".mp3");
//        if (!mergedFile.exists() || mergedFile.length() == 0) {
//            throw new IOException("Merged file is empty or not found. Possible issues with yt-dlp or the provided URL.");
//        }
//
//        long mergeFileCreatedTime = System.currentTimeMillis();
//        long startFfmpegTime = System.currentTimeMillis();
//
//        // Create a new temporary file for the converted mp4 file
////        File mp4File = File.createTempFile("video", ".mp4");
//
//        File mp3File = File.createTempFile("audio",".mp3");
//
//        // Build the ffmpeg command to convert .webm to .mp4 with H.264 and AAC
//        ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder(
//                "ffmpeg", "-i", mergedFile.getAbsolutePath(), "-vn", "-c:a", "libmp3lame", "-y", mp3File.getAbsolutePath()
//        );
//
//        logger.info("Executing ffmpeg command: " + String.join(" ", ffmpegProcessBuilder.command()));
//
//        // Start the ffmpeg process
//        Process ffmpegProcess = ffmpegProcessBuilder.start();
//
//        // Capture and log the output and error streams of the ffmpeg process
////        captureProcessOutput(ffmpegProcess, logger);
//
//        // Wait for the ffmpeg process to complete
//        int ffmpegExitCode = ffmpegProcess.waitFor();
//        if (ffmpegExitCode != 0) {
//            throw new RuntimeException("ffmpeg failed with exit code " + ffmpegExitCode);
//        }
//
//        // Read the converted mp4 file into a byte array
//        byte[] videoBytes = new byte[(int) mp3File.length()];
//        try (FileInputStream fis = new FileInputStream(mp3File)) {
//            fis.read(videoBytes);
//        }
//
//        // Log the file size and delete the temporary files after use
//        logger.info("MP4 File size: " + videoBytes.length + " bytes");
//        if (!tempFile.delete()) {
//            logger.warning("Temporary file deletion failed: " + tempFile.getAbsolutePath());
//        }
//        if (!mergedFile.delete()) {
//            logger.warning("Merged file deletion failed: " + mergedFile.getAbsolutePath());
//        }
//        if (!mp3File.delete()) {
//            logger.warning("Temporary mp4 file deletion failed: " + mp3File.getAbsolutePath());
//        }
//
//        long endTime = System.currentTimeMillis();
//        printTotalTimeTaken(startTime, mergeFileCreatedTime, "youtube");
//        printTotalTimeTaken(startFfmpegTime, endTime," ffmpeg");
//        printTotalTimeTaken(startTime, endTime, "overall");
//
//        return videoBytes;
//    }

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
