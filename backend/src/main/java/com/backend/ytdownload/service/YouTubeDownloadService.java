package com.backend.ytdownload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.TimeUnit;

@Service
public class YouTubeDownloadService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeDownloadService.class);
    private static final int PROCESS_TIMEOUT_MINUTES = 30; // 30 minutes timeout for downloads
    
    /**
     * Downloads and converts video, returns the final MP4 file.
     * The file will be deleted after streaming - do not delete it manually.
     * 
     * @return The converted MP4 file (will be cleaned up after streaming)
     */
    public File downloadVideo(String url, String format, String quality) throws IOException, InterruptedException {
        // Create a temporary file for the download
        File tempFile = File.createTempFile("video", "." + format);
        tempFile.deleteOnExit(); // Ensure cleanup on JVM shutdown
        File mergedFile = null;
        File mp4File = null;
        
        try {
            log.info("Temporary file created: {}", tempFile.getAbsolutePath());

        // Build the yt-dlp command - prefer MP4/H.264/AAC to avoid transcoding
        // Add --no-cache to avoid issues with "already downloaded" messages
        ProcessBuilder processBuilder;
        if (quality.equalsIgnoreCase("HQ")) {
            // Prefer MP4 with H.264/AAC, fallback to best
            processBuilder = new ProcessBuilder(
                    "yt-dlp", 
                    "--no-cache-dir",  // Avoid cache issues
                    "--force-overwrites",  // Always overwrite existing files
                    "-f", "bv*[vcodec^=avc1][ext=mp4]+ba*[acodec^=mp4a][ext=m4a]/bv*[vcodec^=avc1]+ba*[acodec^=mp4a]/best[ext=mp4]/best",
                    "-o", tempFile.getAbsolutePath(), 
                    url
            );
        } else if (quality.equalsIgnoreCase("720")) {
            // Prefer MP4/H.264/AAC at 720p or lower
            processBuilder = new ProcessBuilder(
                    "yt-dlp", 
                    "--no-cache-dir",  // Avoid cache issues
                    "--force-overwrites",  // Always overwrite existing files
                    "-f", "bv*[height<=720][vcodec^=avc1][ext=mp4]+ba*[acodec^=mp4a][ext=m4a]/bv*[height<=720][vcodec^=avc1]+ba*[acodec^=mp4a]/bestvideo[height<=720]+bestaudio/best[height<=720]",
                    "-o", tempFile.getAbsolutePath(), 
                    url
            );
        } else if (quality.equalsIgnoreCase("480")) {
            // Prefer MP4/H.264/AAC at 480p or lower
            processBuilder = new ProcessBuilder(
                    "yt-dlp", 
                    "--no-cache-dir",  // Avoid cache issues
                    "--force-overwrites",  // Always overwrite existing files
                    "-f", "bv*[height<=480][vcodec^=avc1][ext=mp4]+ba*[acodec^=mp4a][ext=m4a]/bv*[height<=480][vcodec^=avc1]+ba*[acodec^=mp4a]/bestvideo[height<=480]+bestaudio/best[height<=480]",
                    "-o", tempFile.getAbsolutePath(), 
                    url
            );
        } else {
            throw new IllegalArgumentException("Unsupported quality: " + quality);
        }


        log.info("Executing yt-dlp command: {}", String.join(" ", processBuilder.command()));

        // Start the yt-dlp process
        Process process = processBuilder.start();

        // Capture and log the output and error streams
        captureProcessOutput(process);

        // Wait for the yt-dlp process to complete with timeout
        boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            log.error("yt-dlp process timed out after {} minutes, killing process", PROCESS_TIMEOUT_MINUTES);
            cleanupTempFile(tempFile);
            throw new RuntimeException("yt-dlp process timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("yt-dlp failed with exit code: {}", exitCode);
            cleanupTempFile(tempFile);
            throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
        }

        // Wait a longer moment for file system to sync (especially if yt-dlp says "already downloaded")
        Thread.sleep(1000);  // Increased from 500ms to 1000ms

        // Verify if the merged file has been created (try multiple extensions)
        // First check if yt-dlp wrote directly to the temp file (no extension added)
        mergedFile = findMergedFile(tempFile);
        
        // Retry logic in case file system hasn't synced yet
        int retries = 5;  // Increased from 3 to 5 retries
        while ((mergedFile == null || !mergedFile.exists() || mergedFile.length() == 0) && retries > 0) {
            log.info("File not found or empty, retrying... (attempts left: {})", retries);
            Thread.sleep(1500);  // Increased from 1000ms to 1500ms
            mergedFile = findMergedFile(tempFile);
            retries--;
        }
        
        if (mergedFile == null || !mergedFile.exists() || mergedFile.length() == 0) {
            // List all files in temp directory for debugging
            File tempDir = tempFile.getParentFile();
            if (tempDir != null && tempDir.exists()) {
                File[] files = tempDir.listFiles((dir, name) -> name.startsWith(tempFile.getName().substring(0, Math.min(10, tempFile.getName().length()))));
                StringBuilder fileList = new StringBuilder("Files in temp dir: ");
                if (files != null) {
                    for (File f : files) {
                        fileList.append(f.getName()).append("(").append(f.length()).append(" bytes), ");
                    }
                }
                log.error(fileList.toString());
            }
            throw new IOException("Merged file is empty or not found. Possible issues with yt-dlp or the provided URL. Temp file: " + tempFile.getAbsolutePath());
        }
        
        log.info("Using file: {} (size: {} bytes)", mergedFile.getAbsolutePath(), mergedFile.length());

        // Check if file is already MP4 with H.264/AAC - if so, skip transcoding!
        boolean needsTranscoding = needsTranscoding(mergedFile);
        
        if (!needsTranscoding) {
            log.info("File is already in MP4/H.264/AAC format - skipping transcoding for faster download!");
            // Cleanup temp file, return merged file directly
            cleanupTempFile(tempFile);
            return mergedFile;
        }

        log.info("File needs transcoding - starting ffmpeg conversion...");
        
        // Create a new temporary file for the converted mp4 file
        mp4File = File.createTempFile("video", ".mp4");

        // Build the ffmpeg command with faster preset and optimized settings
        // Using "veryfast" preset is 2-3x faster than default "medium"
        ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder(
                "ffmpeg", 
                "-i", mergedFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-preset", "veryfast",  // Faster encoding (vs default "medium") - 2-3x speedup
                "-tune", "zerolatency",  // Optimize for low latency
                "-crf", "23",            // Good quality, faster than bitrate-based
                "-c:a", "aac",
                "-b:a", "128k",          // Audio bitrate
                "-movflags", "+faststart", // Enable web streaming optimization
                "-threads", "0",        // Use all available CPU cores
                "-y", 
                mp4File.getAbsolutePath()
        );

        log.info("Executing ffmpeg command: {}", String.join(" ", ffmpegProcessBuilder.command()));

        // Start the ffmpeg process
        Process ffmpegProcess = ffmpegProcessBuilder.start();

        // Capture and log the output and error streams of the ffmpeg process
        captureProcessOutput(ffmpegProcess);

        // Wait for the ffmpeg process to complete with timeout
        boolean ffmpegFinished = ffmpegProcess.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!ffmpegFinished) {
            ffmpegProcess.destroyForcibly();
            log.error("ffmpeg process timed out after {} minutes, killing process", PROCESS_TIMEOUT_MINUTES);
            cleanupTempFile(mergedFile);
            cleanupTempFile(mp4File);
            throw new RuntimeException("ffmpeg process timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes");
        }
        
        int ffmpegExitCode = ffmpegProcess.exitValue();
        if (ffmpegExitCode != 0) {
            log.error("ffmpeg failed with exit code: {}", ffmpegExitCode);
            cleanupTempFile(mergedFile);
            cleanupTempFile(mp4File);
            throw new RuntimeException("ffmpeg failed with exit code " + ffmpegExitCode);
        }

        // Log the file size
        log.info("MP4 File size: {} bytes", mp4File.length());
        
        // Cleanup intermediate files (tempFile and mergedFile)
        // Note: mp4File will be deleted after streaming in the controller
        cleanupTempFile(tempFile);
        cleanupTempFile(mergedFile);
        
        // Return the final MP4 file - it will be streamed and then deleted
        return mp4File;
        } catch (Exception e) {
            // Cleanup all files on error
            cleanupTempFile(tempFile);
            cleanupTempFile(mergedFile);
            cleanupTempFile(mp4File);
            throw e;
        }
    }
    
    private File findMergedFile(File tempFile) {
        // First check if the temp file itself exists (yt-dlp might write directly to it)
        if (tempFile.exists() && tempFile.length() > 0) {
            return tempFile;
        }
        
        // Try common video extensions that yt-dlp might use (prioritize MP4)
        String[] extensions = {".mp4", ".webm", ".mkv", ".m4a", ".flv"};
        for (String ext : extensions) {
            File file = new File(tempFile.getAbsolutePath() + ext);
            if (file.exists() && file.length() > 0) {
                return file;
            }
        }
        
        // Try without the format extension in the filename
        String basePath = tempFile.getAbsolutePath();
        // Remove the format extension from the temp file name if present
        if (basePath.endsWith(".mp4") || basePath.endsWith(".webm") || basePath.endsWith(".mkv")) {
            int lastDot = basePath.lastIndexOf('.');
            if (lastDot > 0) {
                basePath = basePath.substring(0, lastDot);
            }
        }
        
        // Try extensions on the base path
        for (String ext : extensions) {
            File file = new File(basePath + ext);
            if (file.exists() && file.length() > 0) {
                return file;
            }
        }
        
        // Check if yt-dlp created files with format IDs (e.g., .f136.mp4, .f140.m4a)
        // These are intermediate files before merging
        File tempDir = tempFile.getParentFile();
        if (tempDir != null && tempDir.exists()) {
            String baseName = tempFile.getName();
            // Remove extension to get base name
            if (baseName.contains(".")) {
                baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            }
            
            // Look for merged file (yt-dlp merges video+audio)
            for (String ext : extensions) {
                File merged = new File(tempDir, baseName + ext);
                if (merged.exists() && merged.length() > 0) {
                    return merged;
                }
            }
        }
        
        // Fallback to original assumption
        return new File(tempFile.getAbsolutePath() + ".webm");
    }
    
    /**
     * Check if file needs transcoding by examining its format.
     * Returns false if already MP4 with H.264/AAC (can skip transcoding).
     */
    private boolean needsTranscoding(File file) {
        String fileName = file.getName().toLowerCase();
        
        // If not MP4, definitely needs transcoding
        if (!fileName.endsWith(".mp4")) {
            log.info("File is not MP4 format - transcoding required");
            return true;
        }
        
        // If it's MP4, check codecs using ffprobe
        try {
            ProcessBuilder probeBuilder = new ProcessBuilder(
                "ffprobe", 
                "-v", "error",
                "-select_streams", "v:0,a:0",
                "-show_entries", "stream=codec_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.getAbsolutePath()
            );
            
            Process probeProcess = probeBuilder.start();
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(probeProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line.trim()).append(" ");
                }
            }
            
            boolean finished = probeProcess.waitFor(5, TimeUnit.SECONDS);
            if (finished && probeProcess.exitValue() == 0) {
                String codecInfo = output.toString().toLowerCase();
                // Check if video is H.264/AVC and audio is AAC
                boolean hasH264 = codecInfo.contains("h264") || codecInfo.contains("avc") || codecInfo.contains("avc1");
                boolean hasAAC = codecInfo.contains("aac") || codecInfo.contains("mp4a");
                
                if (hasH264 && hasAAC) {
                    log.info("File is already MP4 with H.264/AAC - skipping transcoding for faster download!");
                    return false; // Skip transcoding!
                } else {
                    log.info("File is MP4 but codecs don't match (video: {}, audio: {}) - transcoding required", hasH264, hasAAC);
                }
            }
        } catch (Exception e) {
            log.warn("Could not check codec with ffprobe, will transcode: {}", e.getMessage());
        }
        
        // Default to transcoding if we can't verify or codecs don't match
        return true;
    }
    
    private void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                if (file.delete()) {
                    log.info("Temporary file deleted successfully: {}", file.getAbsolutePath());
                } else {
                    log.warn("Failed to delete temporary file: {} (will be deleted on JVM shutdown)", file.getAbsolutePath());
                    file.deleteOnExit();
                }
            } catch (Exception e) {
                log.error("Error deleting temporary file: {}", file.getAbsolutePath(), e);
                file.deleteOnExit();
            }
        }
    }

    private void captureProcessOutput(Process process) {
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
            } catch (IOException e) {
                log.error("Error capturing process output: {}", e.getMessage());
            }
        });

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);  // Log as info instead of severe to avoid spam
                }
            } catch (IOException e) {
                log.error("Error capturing process error output: {}", e.getMessage());
            }
        });
        
        stdoutThread.setDaemon(false);
        stderrThread.setDaemon(false);
        stdoutThread.start();
        stderrThread.start();
        
        // Wait for threads to complete before returning
        try {
            stdoutThread.join(10000);  // Wait up to 10 seconds
            stderrThread.join(10000);
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for process output capture: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}


