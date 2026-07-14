package com.triobase.data.analytics.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.data.analytics.dto.CreateDatasetRequest;
import com.triobase.data.analytics.dto.DatasetFieldRequest;
import com.triobase.data.analytics.entity.DataDataset;
import com.triobase.data.analytics.mapper.DataDatasetFieldMapper;
import com.triobase.data.analytics.mapper.DataDatasetMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasetCatalogServiceTest {

    private final DataDatasetMapper datasetMapper = mock(DataDatasetMapper.class);
    private final DataDatasetFieldMapper fieldMapper = mock(DataDatasetFieldMapper.class);
    private final DatasetCatalogService service = new DatasetCatalogService(datasetMapper, fieldMapper);

    @Test
    void duplicateDatasetKeyIsRejected() {
        when(datasetMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        CreateDatasetRequest request = new CreateDatasetRequest();
        request.setDatasetKey("expense");
        request.setName("Expense");

        assertThatThrownBy(() -> service.create(request, "tester"))
                .isInstanceOf(BizException.class)
                .hasMessage("DATASET_KEY_ALREADY_EXISTS");
        verify(datasetMapper, never()).insert(any(DataDataset.class));
    }

    @Test
    void invalidDatasetTypeIsRejected() {
        when(datasetMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        CreateDatasetRequest request = new CreateDatasetRequest();
        request.setDatasetKey("expense");
        request.setName("Expense");
        request.setDatasetType("RAW_SQL");
        request.setFields(List.of(new DatasetFieldRequest()));

        assertThatThrownBy(() -> service.create(request, "tester"))
                .isInstanceOf(BizException.class)
                .hasMessage("INVALID_DATASET_TYPE");
    }
}
