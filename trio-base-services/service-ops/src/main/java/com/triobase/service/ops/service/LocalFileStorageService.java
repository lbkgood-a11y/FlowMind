package com.triobase.service.ops.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.ops.config.OpsStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService {

    private final OpsStorageProperties properties;

    public StorageResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(46001, "FILE_EMPTY");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BizException(46002, "FILE_TOO_LARGE");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "unnamed";
        String extension = resolveExtension(originalName);
        if (!isAllowed(extension)) {
            throw new BizException(46003, "FILE_TYPE_NOT_ALLOWED");
        }

        String storageName = UlidGenerator.nextUlid() + (StringUtils.hasText(extension) ? "." + extension : "");
        String datePath = LocalDate.now().toString().replace("-", "/");
        Path relativePath = Path.of(datePath, storageName);
        Path absolutePath = basePath().resolve(relativePath).normalize();
        if (!absolutePath.startsWith(basePath())) {
            throw new BizException(46004, "INVALID_STORAGE_PATH");
        }

        try {
            Files.createDirectories(absolutePath.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, absolutePath);
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            return new StorageResult(storageName, relativePath.toString(), checksum, extension);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException(46005, "FILE_STORE_FAILED");
        }
    }

    public Resource load(String storagePath) {
        Path absolutePath = basePath().resolve(storagePath).normalize();
        if (!absolutePath.startsWith(basePath()) || !Files.exists(absolutePath)) {
            throw new BizException(46006, "FILE_NOT_FOUND");
        }
        return new FileSystemResource(absolutePath);
    }

    private Path basePath() {
        Path path = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new BizException(46007, "FILE_STORAGE_UNAVAILABLE");
        }
        return path;
    }

    private boolean isAllowed(String extension) {
        if (properties.getAllowedExtensions() == null || properties.getAllowedExtensions().isEmpty()) {
            return true;
        }
        return properties.getAllowedExtensions().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(extension));
    }

    private String resolveExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record StorageResult(String storageName, String storagePath, String checksum, String extension) {
    }
}
