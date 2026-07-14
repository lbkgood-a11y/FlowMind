package com.triobase.data.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.data.analytics.dto.CreateDatasetRequest;
import com.triobase.data.analytics.dto.DatasetFieldRequest;
import com.triobase.data.analytics.dto.DatasetFieldResponse;
import com.triobase.data.analytics.dto.DatasetResponse;
import com.triobase.data.analytics.entity.DataDataset;
import com.triobase.data.analytics.entity.DataDatasetField;
import com.triobase.data.analytics.mapper.DataDatasetFieldMapper;
import com.triobase.data.analytics.mapper.DataDatasetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DatasetCatalogService {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private final DataDatasetMapper datasetMapper;
    private final DataDatasetFieldMapper fieldMapper;

    @Transactional
    public DatasetResponse create(CreateDatasetRequest request, String operator) {
        String datasetKey = request.getDatasetKey().trim();
        Long exists = datasetMapper.selectCount(new LambdaQueryWrapper<DataDataset>()
                .eq(DataDataset::getDatasetKey, datasetKey));
        if (exists > 0) {
            throw new BizException(40901, "DATASET_KEY_ALREADY_EXISTS");
        }

        LocalDateTime now = LocalDateTime.now();
        DataDataset dataset = new DataDataset();
        dataset.setId(UlidGenerator.nextUlid());
        dataset.setDatasetKey(datasetKey);
        dataset.setName(request.getName().trim());
        dataset.setDatasetType(normalizeType(request.getDatasetType()));
        dataset.setOwnerId(request.getOwnerId());
        dataset.setOwnerName(request.getOwnerName());
        dataset.setStatus(STATUS_ACTIVE);
        dataset.setBackingTable(normalizeBlank(request.getBackingTable()));
        dataset.setDescription(request.getDescription());
        dataset.setCreatedBy(defaultOperator(operator));
        dataset.setUpdatedBy(defaultOperator(operator));
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);
        datasetMapper.insert(dataset);

        int index = 0;
        for (DatasetFieldRequest field : request.getFields()) {
            DataDatasetField entity = new DataDatasetField();
            entity.setId(UlidGenerator.nextUlid());
            entity.setDatasetId(dataset.getId());
            entity.setFieldKey(field.getFieldKey().trim());
            entity.setLabel(field.getLabel().trim());
            entity.setFieldType(field.getFieldType().trim().toUpperCase());
            entity.setSearchable(Boolean.TRUE.equals(field.getSearchable()) ? 1 : 0);
            entity.setSortable(Boolean.TRUE.equals(field.getSortable()) ? 1 : 0);
            entity.setSortOrder(field.getSortOrder() != null ? field.getSortOrder() : index * 10);
            entity.setCreatedBy(defaultOperator(operator));
            entity.setUpdatedBy(defaultOperator(operator));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            fieldMapper.insert(entity);
            index++;
        }

        return getById(dataset.getId());
    }

    public PageResult<DatasetResponse> list(String status, int pageNo, int pageSize) {
        LambdaQueryWrapper<DataDataset> wrapper = new LambdaQueryWrapper<DataDataset>()
                .orderByDesc(DataDataset::getUpdatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(DataDataset::getStatus, status.trim().toUpperCase());
        }
        IPage<DataDataset> page = datasetMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toResponseWithoutFields).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    public DatasetResponse getById(String id) {
        DataDataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BizException(40401, "DATASET_NOT_FOUND");
        }
        DatasetResponse response = toResponseWithoutFields(dataset);
        response.setFields(listFields(dataset.getId()));
        return response;
    }

    public DataDataset requireActiveByKey(String datasetKey) {
        DataDataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<DataDataset>()
                .eq(DataDataset::getDatasetKey, datasetKey)
                .eq(DataDataset::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (dataset == null) {
            throw new BizException(40401, "DATASET_NOT_FOUND");
        }
        return dataset;
    }

    public List<DatasetFieldResponse> listFields(String datasetId) {
        return fieldMapper.selectList(new LambdaQueryWrapper<DataDatasetField>()
                        .eq(DataDatasetField::getDatasetId, datasetId)
                        .orderByAsc(DataDatasetField::getSortOrder))
                .stream()
                .map(this::toFieldResponse)
                .toList();
    }

    private DatasetResponse toResponseWithoutFields(DataDataset dataset) {
        DatasetResponse response = new DatasetResponse();
        response.setId(dataset.getId());
        response.setDatasetKey(dataset.getDatasetKey());
        response.setName(dataset.getName());
        response.setDatasetType(dataset.getDatasetType());
        response.setOwnerId(dataset.getOwnerId());
        response.setOwnerName(dataset.getOwnerName());
        response.setStatus(dataset.getStatus());
        response.setBackingTable(dataset.getBackingTable());
        response.setDescription(dataset.getDescription());
        response.setCreatedAt(dataset.getCreatedAt());
        response.setUpdatedAt(dataset.getUpdatedAt());
        return response;
    }

    private DatasetFieldResponse toFieldResponse(DataDatasetField field) {
        DatasetFieldResponse response = new DatasetFieldResponse();
        response.setId(field.getId());
        response.setFieldKey(field.getFieldKey());
        response.setLabel(field.getLabel());
        response.setFieldType(field.getFieldType());
        response.setSearchable(field.getSearchable() != null && field.getSearchable() == 1);
        response.setSortable(field.getSortable() != null && field.getSortable() == 1);
        response.setSortOrder(field.getSortOrder());
        return response;
    }

    private String normalizeType(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toUpperCase() : "STRUCTURED";
        if (!value.equals("STRUCTURED") && !value.equals("DOCUMENT")) {
            throw new BizException(40001, "INVALID_DATASET_TYPE");
        }
        return value;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "SYSTEM";
    }
}
