package com.webox.service;

import com.webox.common.ApiException;
import com.webox.common.ErrorCode;
import com.webox.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadService {
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final AppProperties props;
    private Path directory;

    public UploadService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        try {
            directory = Path.of(props.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialise upload directory", e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Please choose an image to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Image is too large. Maximum size is 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Only JPG, PNG or WebP images are supported.");
        }
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String name = UUID.randomUUID().toString().replace("-", "") + extension;
        try {
            Files.copy(file.getInputStream(), directory.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
            return "/api/uploads/" + name;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not save the image. Please try again.");
        }
    }

    public byte[] read(String name) {
        if (name == null || !name.matches("[A-Za-z0-9._-]+")) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Image not found.");
        }
        Path path = directory.resolve(name).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Image not found.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Image not found.");
        }
    }

    public String mediaType(String name) {
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
