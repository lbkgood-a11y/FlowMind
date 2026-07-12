package com.triobase.service.ops.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.dto.BindFileReferenceRequest;
import com.triobase.service.ops.entity.OpsFile;
import com.triobase.service.ops.entity.OpsFileReference;
import com.triobase.service.ops.service.FileCenterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class FileCenterController {

    private final FileCenterService fileCenterService;

    @GetMapping("/api/v1/files")
    @RequirePermission("/api/v1/files:GET")
    public R<PageResult<OpsFile>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Short status,
                                       @RequestParam(required = false) String ownerUserId) {
        return R.ok(fileCenterService.page(page, size, keyword, status, ownerUserId));
    }

    @PostMapping(value = "/api/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("/api/v1/files:POST")
    public R<OpsFile> upload(@RequestPart("file") MultipartFile file) {
        return R.ok(fileCenterService.upload(file));
    }

    @GetMapping("/api/v1/files/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        FileCenterService.FileDownload download = fileCenterService.download(id);
        String filename = download.file().getOriginalName();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(download.resource());
    }

    @PutMapping("/api/v1/files/{id}/status")
    @RequirePermission("/api/v1/files/*:PUT")
    public R<OpsFile> updateStatus(@PathVariable String id, @RequestParam Short status) {
        return R.ok(fileCenterService.updateStatus(id, status));
    }

    @DeleteMapping("/api/v1/files/{id}")
    @RequirePermission("/api/v1/files/*:DELETE")
    public R<Void> delete(@PathVariable String id) {
        fileCenterService.delete(id);
        return R.ok();
    }

    @GetMapping("/api/v1/file-references")
    @RequirePermission("/api/v1/file-references:GET")
    public R<List<OpsFileReference>> references(@RequestParam(required = false) String businessType,
                                                @RequestParam(required = false) String businessId,
                                                @RequestParam(required = false) String fileId) {
        return R.ok(fileCenterService.references(businessType, businessId, fileId));
    }

    @PostMapping("/api/v1/file-references")
    @RequirePermission("/api/v1/file-references:POST")
    public R<OpsFileReference> bindReference(@Valid @RequestBody BindFileReferenceRequest request) {
        return R.ok(fileCenterService.bindReference(request));
    }
}
