package com.triobase.service.auth.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.service.auth.dto.SaveDictItemRequest;
import com.triobase.service.auth.dto.SaveDictTypeRequest;
import com.triobase.service.auth.entity.SysDictItem;
import com.triobase.service.auth.entity.SysDictType;
import com.triobase.service.auth.mapper.DictItemMapper;
import com.triobase.service.auth.mapper.DictTypeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceTest {

    @Mock
    private DictTypeMapper dictTypeMapper;

    @Mock
    private DictItemMapper dictItemMapper;

    @InjectMocks
    private DictionaryService dictionaryService;

    @Test
    void createType_shouldRejectDuplicateDictCode() {
        SaveDictTypeRequest request = new SaveDictTypeRequest();
        request.setDictCode("user_status");
        request.setDictName("用户状态");

        SysDictType existing = new SysDictType();
        existing.setId("DT001");
        existing.setDictCode("USER_STATUS");
        when(dictTypeMapper.selectOne(any())).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> dictionaryService.createType(request));

        assertEquals(40081, ex.getCode());
        verify(dictTypeMapper, never()).insert(any(SysDictType.class));
    }

    @Test
    void deleteType_shouldRejectSystemDictionary() {
        SysDictType type = new SysDictType();
        type.setId("DT001");
        type.setSystemFlag((short) 1);
        when(dictTypeMapper.selectById("DT001")).thenReturn(type);

        BizException ex = assertThrows(BizException.class, () -> dictionaryService.deleteType("DT001"));

        assertEquals(40082, ex.getCode());
        verify(dictTypeMapper, never()).deleteById("DT001");
    }

    @Test
    void enabledItems_shouldReturnEmpty_whenDictionaryDisabled() {
        SysDictType type = new SysDictType();
        type.setId("DT001");
        type.setDictCode("USER_STATUS");
        type.setStatus((short) 0);
        when(dictTypeMapper.selectOne(any())).thenReturn(type);

        assertEquals(0, dictionaryService.enabledItems("USER_STATUS").size());
        verify(dictItemMapper, never()).selectList(any());
    }

    @Test
    void createItem_shouldPersistUnderResolvedType() {
        SaveDictItemRequest request = new SaveDictItemRequest();
        request.setDictCode("USER_STATUS");
        request.setItemLabel("启用");
        request.setItemValue("ENABLED");
        request.setStatus(1);

        SysDictType type = new SysDictType();
        type.setId("DT001");
        type.setDictCode("USER_STATUS");
        when(dictTypeMapper.selectOne(any())).thenReturn(type);
        when(dictItemMapper.selectCount(any())).thenReturn(0L);

        SysDictItem item = dictionaryService.createItem(request);

        assertEquals("DT001", item.getDictTypeId());
        assertEquals("USER_STATUS", item.getDictCode());
        assertEquals("ENABLED", item.getItemValue());
        verify(dictItemMapper).insert(any(SysDictItem.class));
    }
}
