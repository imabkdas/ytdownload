package com.backend.ytdownload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class YoutubeAudioDownloaderService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeAudioDownloaderService.class);
    private static final int PROCESS_TIMEOUT_MINUTES = 30; // 30 minutes timeout for downloads
    
    /**
     * Downloads audio, returns the MP3 file.
     * The file will be deleted after streaming - do not delete it manually.
     * 
     * @return The MP3 file (will be cleaned up after streaming)
     */
    public File downloadAudio(String url) throws IOException, InterruptedException {

        // Create a temporary file for the audio download
        File tempFile = File.createTempFile("audio", ".mp3");
        tempFile.deleteOnExit(); // Ensure cleanup on JVM shutdown
        
        try {
            log.info("Temporary file created: {}", tempFile.getAbsolutePath());

            // Build the yt-dlp command to download audio only
            // Strategy: Download Opus audio (format 251), then convert to MP3 using ffmpeg with full path
            File tempOpusFile = new File(tempFile.getAbsolutePath().replace(".mp3", ".webm"));
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "yt-dlp",
                    "-f", "251",  // Audio-only Opus format (highest quality)
                    "--no-check-certificate",
                    "--progress-template", "[download] %(progress)s",
                    "-o", tempOpusFile.getAbsolutePath(),
                    url
            );

            // Ensure ffmpeg/ffprobe are available in the subprocess environment
            // Works across local, Docker, and cloud environments
            Map<String, String> env = processBuilder.environment();
            String currentPath = env.get("PATH");
            
            // Add common binary locations to PATH for both macOS and Linux environments
            String enhancedPath = "/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin";
            if (currentPath != null && !currentPath.isEmpty()) {
                enhancedPath = currentPath + ":" + enhancedPath;
            }
            env.put("PATH", enhancedPath);
            
            // Also set DYLD_LIBRARY_PATH for macOS (helps find libraries ffprobe needs)
            String currentDyldPath = env.get("DYLD_LIBRARY_PATH");
            String enhancedDyldPath = "/opt/homebrew/lib";
            if (currentDyldPath != null && !currentDyldPath.isEmpty()) {
                enhancedDyldPath = currentDyldPath + ":" + enhancedDyldPath;
            }
            env.put("DYLD_LIBRARY_PATH", enhancedDyldPath);
            
            log.info("Process PATH: {}", enhancedPath);
            log.info("Process DYLD_LIBRARY_PATH: {}", enhancedDyldPath);

            // Log the command being executed
            String command = String.join(" ", processBuilder.command());
            log.info("Executing yt-dlp command: {}", command);

            // Start the yt-dlp process
            Process process = processBuilder.start();

            try {
                // Capture and log the output and error streams
                captureProcessOutput(process);

                // Wait for the yt-dlp process to complete with timeout
                boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                if (!finished) {
                    process.destroyForcibly();
                    log.error("yt-dlp process timed out after {} minutes, killing process", PROCESS_TIMEOUT_MINUTES);
                    deleteFile(tempFile);
                    throw new RuntimeException("yt-dlp process timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes");
                }
                
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    log.error("yt-dlp failed with exit code: {}", exitCode);
                    deleteFile(tempFile);
                    deleteFile(tempOpusFile);
                    throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
                }

                // Check if the Opus file has been created successfully
                if (!tempOpusFile.exists() || tempOpusFile.length() == 0) {
                    log.error("Opus file is empty or not found after yt-dlp completion");
                    deleteFile(tempFile);
                    deleteFile(tempOpusFile);
                    throw new IOException("Opus audio file is empty or not found. Possible issues with yt-dlp or the provided URL.");
                }

                log.info("Opus file downloaded successfully: {} bytes", tempOpusFile.length());
                
                // Convert Opus to MP3 using ffmpeg with full path
                log.info("Converting Opus to MP3...");
                ProcessBuilder ffmpegBuilder = new ProcessBuilder(
                        "/opt/homebrew/bin/ffmpeg",  // Use full path to avoid issues
                        "-i", tempOpusFile.getAbsolutePath(),
                        "-q:a", "0",  // Best quality audio
                        "-y",  // Overwrite output file
                        tempFile.getAbsolutePath()
                );
                
                // Copy environment to ffmpeg process
                ffmpegBuilder.environment().putAll(env);
                
                Process ffmpegProcess = ffmpegBuilder.start();
                
                try {
                    // Capture and log the output and error streams
                    captureProcessOutput(ffmpegProcess);

                    // Wait for the ffmpeg process to complete with timeout
                    boolean ffmpegFinished = ffmpegProcess.waitFor(10, TimeUnit.MINUTES);
                    if (!ffmpegFinished) {
                        ffmpegProcess.destroyForcibly();
                        log.error("ffmpeg process timed out after 10 minutes, killing process");
                        deleteFile(tempFile);
                        deleteFile(tempOpusFile);
                        throw new RuntimeException("ffmpeg conversion timed out");
                    }
                    
                    int ffmpegExitCode = ffmpegProcess.exitValue();
                    if (ffmpegExitCode != 0) {
                        log.error("ffmpeg conversion failed with exit code: {}", ffmpegExitCode);
                        deleteFile(tempFile);
                        deleteFile(tempOpusFile);
                        throw new RuntimeException("ffmpeg conversion failed with exit code " + ffmpegExitCode);
                    }
                } finally {
                    if (ffmpegProcess.isAlive()) {
                        ffmpegProcess.destroyForcibly();
                    }
                }

                // Check if the MP3 file has been created successfully
                if (!tempFile.exists() || tempFile.length() == 0) {
                    log.error("MP3 file is empty or not found after ffmpeg conversion");
                    deleteFile(tempFile);
                    deleteFile(tempOpusFile);
                    throw new IOException("MP3 file is empty or not found after conversion.");
                }

                // Log the file size
                log.info("MP3 File size: {} bytes", tempFile.length());
                
                // Delete the temporary Opus file since we no longer need it
                deleteFile(tempOpusFile);
                
                // Return the MP3 file - it will be streamed and then deleted in the controller
                return tempFile;
            } finally {
                // Ensure process is cleaned up
                if (process.isAlive()) {
                    process.destroyForcibly();
                    log.warn("Forcibly destroyed yt-dlp process");
                }
            }
        } catch (Exception e) {
            // Comprehensive cleanup on any error
            log.error("Error during audio download: {}", e.getMessage(), e);
            deleteFile(tempFile);
            File tempOpusFile = new File(tempFile.getAbsolutePath().replace(".mp3", ".webm"));
            deleteFile(tempOpusFile);
            throw e;
        }
    }

    /**
     * Safely deletes a file with proper logging
     */
    private void deleteFile(File file) {
        if (file != null && file.exists()) {
            try {
                if (file.delete()) {
                    log.info("Temporary file deleted successfully: {}", file.getAbsolutePath());
                } else {
                    log.warn("Failed to delete temporary file: {} (will be deleted on JVM shutdown)", file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Error deleting temporary file: {}", file.getAbsolutePath(), e);
            }
        }
    }

    private void captureProcessOutput(Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
            } catch (IOException e) {
                log.error("Error capturing process output: {}", e.getMessage());
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.error(line);
                }
            } catch (IOException e) {
                log.error("Error capturing process error output: {}", e.getMessage());
            }
        }).start();
    }
}
