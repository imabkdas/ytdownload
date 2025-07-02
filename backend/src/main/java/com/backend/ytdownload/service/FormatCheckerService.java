package com.backend.ytdownload.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FormatCheckerService {

    public List<String> checkAvailableFormats(String videoUrl) {
        Set<String> requiredResolutions = Set.of("480p", "720p", "1080p");
        Set<String> availableResolutions = new HashSet<>();
        boolean hasMp3 = false;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("yt-dlp", "-F", videoUrl);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains("mp4") || line.contains("webm") || line.contains("mkv")) {
                    if (line.contains("480p")) availableResolutions.add("480p");
                    if (line.contains("720p")) availableResolutions.add("720p");
                    if (line.contains("1080p")) availableResolutions.add("1080p");
                }
                if (line.contains("mp3")) {
                    hasMp3 = true;
                }
            }

            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions or propagate as needed
            //
        }

        // Return the list of available resolutions and formats
        List<String> results = new ArrayList<>(availableResolutions);
        if (hasMp3) {
            results.add("mp3");
        }
        return results;
    }
}
