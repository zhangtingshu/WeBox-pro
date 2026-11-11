package com.webox.controller;

import com.webox.service.UploadService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Public dish images (static seed images are served by nginx; uploads come from the API). */
@RestController
@RequestMapping("/api/uploads")
public class FileController {

    private final UploadService uploadService;

    public FileController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) {
        byte[] bytes = uploadService.read(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(uploadService.mediaType(name)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(bytes);
    }
}
