package com.triobase.service.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.ops.dto.BindFileReferenceRequest;
import com.triobase.service.ops.entity.OpsFile;
import com.triobase.service.ops.entity.OpsFileReference;
import com.triobase.service.ops.mapper.FileReferenceMapper;
import com.triobase.service.ops.mapper.OpsFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileCenterService {

    private static final Short ENABLED = (short) 1;
    private static final Short DISABLED = (short) 0;
    private static final Short DELETED = (short) 1;
    private static final String DOWNLOAD_PERMISSION = "/api/v1/files/*:GET";

    private final OpsFileMapper fileMapper;
    private final FileReferenceMapper referenceMapper;
    private final LocalFileStorageService storageService;
    private final RequestContextService contextService;

    public PageResult<OpsFile> page(int page,
                                    int size,
                                    String keyword,
                                    Short status,
                                    String ownerUserId) {
        LambdaQueryWrapper<OpsFile> wrapper = new LambdaQueryWrapper<OpsFile>()
                .eq(OpsFile::getTenantId, contextService.tenantId())
                .eq(OpsFile::getDeleted, (short) 0)
                .like(StringUtils.hasText(keyword), OpsFile::getOriginalName, keyword)
                .eq(status != null, OpsFile::getStatus, status)
                .eq(StringUtils.hasText(ownerUserId), OpsFile::getOwnerUserId, ownerUserId)
                .orderByDesc(OpsFile::getCreatedAt);
        IPage<OpsFile> result = fileMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Transactional
    public OpsFile upload(MultipartFile multipartFile) {
        LocalFileStorageService.StorageResult storage = storageService.store(multipartFile);
        OpsFile file = new OpsFile();
        file.setId(UlidGenerator.nextUlid());
        file.setTenantId(contextService.tenantId());
        file.setOriginalName(StringUtils.hasText(multipartFile.getOriginalFilename())
                ? multipartFile.getOriginalFilename()
                : storage.storageName());
        file.setStorageName(storage.storageName());
        file.setContentType(multipartFile.getContentType());
        file.setExtension(storage.extension());
        file.setFileSize(multipartFile.getSize());
        file.setStoragePath(storage.storagePath());
        file.setChecksum(storage.checksum());
        file.setOwnerUserId(contextService.userId());
        file.setStatus(ENABLED);
        file.setDeleted((short) 0);
        file.setDownloadCount(0L);
        fileMapper.insert(file);
        return file;
    }

    @Transactional
    public FileDownload download(String id) {
        OpsFile file = requireFile(id);
        String userId = contextService.userId();
        boolean manager = contextService.hasPermission(DOWNLOAD_PERMISSION);
        if (!userId.equals(file.getOwnerUserId()) && !manager) {
            throw new BizException(45202, "FILE_DOWNLOAD_FORBIDDEN");
        }
        if (!ENABLED.equals(file.getStatus()) && !manager) {
            throw new BizException(45203, "FILE_DISABLED");
        }
        file.setDownloadCount(file.getDownloadCount() == null ? 1 : file.getDownloadCount() + 1);
        file.setLastDownloadAt(LocalDateTime.now());
        fileMapper.updateById(file);
        return new FileDownload(file, storageService.load(file.getStoragePath()));
    }

    @Transactional
    public OpsFile updateStatus(String id, Short status) {
        OpsFile file = requireFile(id);
        file.setStatus(ENABLED.equals(status) ? ENABLED : DISABLED);
        fileMapper.updateById(file);
        return file;
    }

    @Transactional
    public void delete(String id) {
        OpsFile file = requireFile(id);
        file.setDeleted(DELETED);
        fileMapper.updateById(file);
    }

    @Transactional
    public OpsFileReference bindReference(BindFileReferenceRequest request) {
        requireFile(request.getFileId());
        OpsFileReference reference = new OpsFileReference();
        reference.setId(UlidGenerator.nextUlid());
        reference.setTenantId(contextService.tenantId());
        reference.setFileId(request.getFileId());
        reference.setBusinessType(request.getBusinessType());
        reference.setBusinessId(request.getBusinessId());
        reference.setRefType(StringUtils.hasText(request.getRefType()) ? request.getRefType() : "ATTACHMENT");
        referenceMapper.insert(reference);
        return reference;
    }

    public List<OpsFileReference> references(String businessType, String businessId, String fileId) {
        return referenceMapper.selectList(new LambdaQueryWrapper<OpsFileReference>()
                .eq(OpsFileReference::getTenantId, contextService.tenantId())
                .eq(StringUtils.hasText(businessType), OpsFileReference::getBusinessType, businessType)
                .eq(StringUtils.hasText(businessId), OpsFileReference::getBusinessId, businessId)
                .eq(StringUtils.hasText(fileId), OpsFileReference::getFileId, fileId)
                .orderByDesc(OpsFileReference::getCreatedAt));
    }

    private OpsFile requireFile(String id) {
        OpsFile file = fileMapper.selectById(id);
        if (file == null || DELETED.equals(file.getDeleted())) {
            throw new BizException(45201, "FILE_NOT_FOUND");
        }
        return file;
    }

    public record FileDownload(OpsFile file, Resource resource) {
    }
}
