package com.triobase.data.analytics.service;

import com.triobase.data.analytics.config.DataAnalyticsProperties;
import com.triobase.data.analytics.dto.HybridQueryRequest;
import com.triobase.data.analytics.dto.SemanticQueryRequest;
import com.triobase.data.analytics.dto.StructuredQueryRequest;
import com.triobase.data.analytics.dto.StructuredQueryResponse;
import com.triobase.data.analytics.entity.DataDataset;
import com.triobase.data.analytics.mapper.DataDocumentChunkMapper;
import com.triobase.data.analytics.mapper.DataDocumentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataQueryServiceTest {

    private final DatasetCatalogService catalogService = mock(DatasetCatalogService.class);
    private final DataDocumentChunkMapper chunkMapper = mock(DataDocumentChunkMapper.class);
    private final DataDocumentMapper documentMapper = mock(DataDocumentMapper.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final DataAnalyticsProperties properties = new DataAnalyticsProperties();
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final QueryAuditService auditService = mock(QueryAuditService.class);
    private final DataQueryService service = new DataQueryService(
            catalogService, chunkMapper, documentMapper, embeddingService, properties, jdbcTemplate, auditService);

    @Test
    void structuredQueryCapsPageSizeAndReturnsRows() {
        properties.getQuery().setMaxPageSize(2);
        DataDataset dataset = new DataDataset();
        dataset.setId("D1");
        dataset.setDatasetKey("expense_report_sample");
        dataset.setBackingTable("data_sample_expense");
        when(catalogService.requireActiveByKey("expense_report_sample")).thenReturn(dataset);
        when(catalogService.listFields("D1")).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(3L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "EXP1", "amount", 100)));

        StructuredQueryRequest request = new StructuredQueryRequest();
        request.setDatasetKey("expense_report_sample");
        request.setSize(50);

        StructuredQueryResponse response = service.structured(request);

        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(3);
    }

    @Test
    void hybridStructuredOnlySkipsSemanticAndAudits() {
        DataDataset dataset = new DataDataset();
        dataset.setId("D1");
        dataset.setDatasetKey("expense_report_sample");
        dataset.setBackingTable("data_sample_expense");
        when(catalogService.requireActiveByKey("expense_report_sample")).thenReturn(dataset);
        when(catalogService.listFields("D1")).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        StructuredQueryRequest structured = new StructuredQueryRequest();
        structured.setDatasetKey("expense_report_sample");
        SemanticQueryRequest semantic = new SemanticQueryRequest();
        semantic.setQuery("finance review");
        HybridQueryRequest request = new HybridQueryRequest();
        request.setMode("STRUCTURED");
        request.setStructured(structured);
        request.setSemantic(semantic);

        var response = service.hybrid(request, "U1", "alice");

        assertThat(response.getStructured().getStatus()).isEqualTo("OK");
        assertThat(response.getSemantic().getStatus()).isEqualTo("SKIPPED");
        verify(auditService).record(anyString(), any(), any(), any(), any(Long.class), any(Integer.class), any(Integer.class));
    }
}
