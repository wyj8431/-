package com.example.lowcode.template.api;

import com.example.lowcode.template.application.TemplateCoverService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/template-cover-assets")
public class TemplateCoverController {
    private final TemplateCoverService templateCoverService;

    public TemplateCoverController(TemplateCoverService templateCoverService) {
        this.templateCoverService = templateCoverService;
    }

    @GetMapping("/{coverAssetId}/content")
    public ResponseEntity<byte[]> content(@PathVariable long coverAssetId) {
        TemplateCoverService.CoverContent content = templateCoverService.readPublicContent(coverAssetId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.mimeType()))
            .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
            .header("X-Content-Type-Options", "nosniff")
            .body(content.bytes());
    }
}
