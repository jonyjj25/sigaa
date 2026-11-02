package com.sigaa.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ConfigController {

    @Value("${app.qr-public-url:http://localhost:4200}")
    private String qrPublicUrl;

    @GetMapping("/api/config")
    public Map<String, String> getConfig() {
        return Map.of("qrBaseUrl", qrPublicUrl);
    }
}
