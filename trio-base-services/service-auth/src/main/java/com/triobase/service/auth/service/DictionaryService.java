package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.auth.dto.SaveDictItemRequest;
import com.triobase.service.auth.dto.SaveDictTypeRequest;
import com.triobase.service.auth.entity.SysDictItem;
import com.triobase.service.auth.entity.SysDictType;
import com.triobase.service.auth.mapper.DictItemMapper;
import com.triobase.service.auth.mapper.DictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private static final String DEFAULT_TENANT = "default";

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    public List<SysDictType> listTypes(String keyword, Integer status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, DEFAULT_TENANT)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(SysDictType::getDictCode, keyword)
                        .or()
                        .like(SysDictType::getDictName, keyword))
                .eq(status != null, SysDictType::getStatus, toStatus(status))
                .orderByAsc(SysDictType::getSortOrder)
                .orderByAsc(SysDictType::getDictCode);
        return dictTypeMapper.selectList(wrapper);
    }

    public List<SysDictItem> listItems(String dictCode, Integer status) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, DEFAULT_TENANT)
                .eq(StringUtils.hasText(dictCode), SysDictItem::getDictCode, normalizeCode(dictCode))
                .eq(status != null, SysDictItem::getStatus, toStatus(status))
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getItemValue);
        return dictItemMapper.selectList(wrapper);
    }

    public List<SysDictItem> enabledItems(String dictCode) {
        SysDictType type = findTypeByCode(dictCode);
        if (type == null || type.getStatus() == null || type.getStatus() == 0) {
            return List.of();
        }
        return listItems(type.getDictCode(), 1);
    }

    @Transactional
    public SysDictType createType(SaveDictTypeRequest request) {
        validateTypeRequest(request);
        String dictCode = normalizeCode(request.getDictCode());
        if (findTypeByCode(dictCode) != null) {
            throw new BizException(40081, "DICT_CODE_DUPLICATE");
        }
        SysDictType type = new SysDictType();
        type.setId(UlidGenerator.nextUlid());
        type.setTenantId(DEFAULT_TENANT);
        applyTypeRequest(type, request);
        type.setDictCode(dictCode);
        dictTypeMapper.insert(type);
        return type;
    }

    @Transactional
    public SysDictType updateType(String id, SaveDictTypeRequest request) {
        SysDictType type = requireType(id);
        validateTypeRequest(request);
        String dictCode = normalizeCode(request.getDictCode());
        SysDictType existed = findTypeByCode(dictCode);
        if (existed != null && !existed.getId().equals(id)) {
            throw new BizException(40081, "DICT_CODE_DUPLICATE");
        }
        String oldCode = type.getDictCode();
        applyTypeRequest(type, request);
        type.setDictCode(dictCode);
        dictTypeMapper.updateById(type);
        if (!oldCode.equals(dictCode)) {
            dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                    .eq(SysDictItem::getTenantId, DEFAULT_TENANT)
                    .eq(SysDictItem::getDictTypeId, id))
                    .forEach(item -> {
                        item.setDictCode(dictCode);
                        dictItemMapper.updateById(item);
                    });
        }
        return type;
    }

    @Transactional
    public void deleteType(String id) {
        SysDictType type = requireType(id);
        if (type.getSystemFlag() != null && type.getSystemFlag() == 1) {
            throw new BizException(40082, "SYSTEM_DICT_DELETE_FORBIDDEN");
        }
        dictTypeMapper.deleteById(id);
    }

    @Transactional
    public SysDictItem createItem(SaveDictItemRequest request) {
        SysDictType type = resolveType(request.getDictTypeId(), request.getDictCode());
        validateItemRequest(request);
        String value = request.getItemValue().trim();
        if (itemExists(type.getDictCode(), value, null)) {
            throw new BizException(40083, "DICT_ITEM_VALUE_DUPLICATE");
        }
        SysDictItem item = new SysDictItem();
        item.setId(UlidGenerator.nextUlid());
        item.setTenantId(DEFAULT_TENANT);
        item.setDictTypeId(type.getId());
        item.setDictCode(type.getDictCode());
        applyItemRequest(item, request);
        dictItemMapper.insert(item);
        return item;
    }

    @Transactional
    public SysDictItem updateItem(String id, SaveDictItemRequest request) {
        SysDictItem item = requireItem(id);
        SysDictType type = resolveType(request.getDictTypeId(), request.getDictCode());
        validateItemRequest(request);
        String value = request.getItemValue().trim();
        if (itemExists(type.getDictCode(), value, id)) {
            throw new BizException(40083, "DICT_ITEM_VALUE_DUPLICATE");
        }
        item.setDictTypeId(type.getId());
        item.setDictCode(type.getDictCode());
        applyItemRequest(item, request);
        dictItemMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteItem(String id) {
        SysDictItem item = requireItem(id);
        if (item.getSystemFlag() != null && item.getSystemFlag() == 1) {
            throw new BizException(40084, "SYSTEM_DICT_ITEM_DELETE_FORBIDDEN");
        }
        dictItemMapper.deleteById(id);
    }

    private void applyTypeRequest(SysDictType type, SaveDictTypeRequest request) {
        type.setDictName(request.getDictName().trim());
        type.setStatus(toStatus(request.getStatus()));
        type.setSystemFlag(toShort(request.getSystemFlag()));
        type.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        type.setDescription(normalizeBlank(request.getDescription()));
    }

    private void applyItemRequest(SysDictItem item, SaveDictItemRequest request) {
        item.setItemLabel(request.getItemLabel().trim());
        item.setItemValue(request.getItemValue().trim());
        item.setTagType(normalizeBlank(request.getTagType()));
        item.setCssClass(normalizeBlank(request.getCssClass()));
        item.setStatus(toStatus(request.getStatus()));
        item.setSystemFlag(toShort(request.getSystemFlag()));
        item.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        item.setDescription(normalizeBlank(request.getDescription()));
        item.setMetadata(normalizeBlank(request.getMetadata()));
    }

    private void validateTypeRequest(SaveDictTypeRequest request) {
        if (request == null || !StringUtils.hasText(request.getDictCode()) || !StringUtils.hasText(request.getDictName())) {
            throw new BizException(40080, "DICT_TYPE_REQUIRED");
        }
    }

    private void validateItemRequest(SaveDictItemRequest request) {
        if (request == null || !StringUtils.hasText(request.getItemLabel()) || !StringUtils.hasText(request.getItemValue())) {
            throw new BizException(40085, "DICT_ITEM_REQUIRED");
        }
    }

    private SysDictType resolveType(String dictTypeId, String dictCode) {
        SysDictType type = StringUtils.hasText(dictTypeId) ? dictTypeMapper.selectById(dictTypeId) : findTypeByCode(dictCode);
        if (type == null) {
            throw new BizException(40481, "DICT_TYPE_NOT_FOUND");
        }
        return type;
    }

    private SysDictType requireType(String id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BizException(40481, "DICT_TYPE_NOT_FOUND");
        }
        return type;
    }

    private SysDictItem requireItem(String id) {
        SysDictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(40482, "DICT_ITEM_NOT_FOUND");
        }
        return item;
    }

    private SysDictType findTypeByCode(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return null;
        }
        return dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, DEFAULT_TENANT)
                .eq(SysDictType::getDictCode, normalizeCode(dictCode))
                .last("LIMIT 1"));
    }

    private boolean itemExists(String dictCode, String itemValue, String excludeId) {
        return dictItemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTenantId, DEFAULT_TENANT)
                .eq(SysDictItem::getDictCode, dictCode)
                .eq(SysDictItem::getItemValue, itemValue)
                .ne(StringUtils.hasText(excludeId), SysDictItem::getId, excludeId)) > 0;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Short toStatus(Integer value) {
        return (short) (value != null && value == 0 ? 0 : 1);
    }

    private Short toShort(Integer value) {
        return (short) (value != null && value == 1 ? 1 : 0);
    }
}
