package com.backend.ytdownload.controller;

import com.backend.ytdownload.service.FormatCheckerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FormatCheckerController {

    private final FormatCheckerService formatCheckerService;

    public FormatCheckerController(FormatCheckerService formatCheckerService) {
        this.formatCheckerService = formatCheckerService;
    }

    @GetMapping("/check-quality")
    public List<String> checkQuality(@RequestParam String url) {
        return formatCheckerService.checkAvailableFormats(url);
    }
}
