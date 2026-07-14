package com.triobase.data.analytics.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.data.analytics.config.DataAnalyticsProperties;
import com.triobase.data.analytics.dto.DocumentIngestResponse;
import com.triobase.data.analytics.dto.IngestDocumentRequest;
import com.triobase.data.analytics.entity.DataDocument;
import com.triobase.data.analytics.entity.DataDocumentChunk;
import com.triobase.data.analytics.mapper.DataDocumentChunkMapper;
import com.triobase.data.analytics.mapper.DataDocumentMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    private final DataDocumentMapper documentMapper = mock(DataDocumentMapper.class);
    private final DataDocumentChunkMapper chunkMapper = mock(DataDocumentChunkMapper.class);
    private final DataAnalyticsProperties properties = new DataAnalyticsProperties();
    private final DocumentIngestionService service = new DocumentIngestionService(
            documentMapper,
            chunkMapper,
            new ChunkingService(properties),
            new EmbeddingService(properties, new ObjectMapper()));

    @Test
    void rejectsBlankDocument() {
        IngestDocumentRequest request = new IngestDocumentRequest();
        request.setCollectionKey("policies");
        request.setTitle("Policy");
        request.setContent(" ");

        assertThatThrownBy(() -> service.ingest(request, "tester"))
                .isInstanceOf(BizException.class)
                .hasMessage("EMPTY_DOCUMENT");
        verify(documentMapper, never()).insert(any(DataDocument.class));
    }

    @Test
    void replacesExistingSourceInOneIngestion() {
        DataDocument existing = new DataDocument();
        existing.setId("DOC_OLD");
        when(documentMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        IngestDocumentRequest request = new IngestDocumentRequest();
        request.setCollectionKey("policies");
        request.setSourceKey("expense");
        request.setTitle("Expense Policy");
        request.setContent("Amounts above 5000 require finance review.");

        DocumentIngestResponse response = service.ingest(request, "tester");

        assertThat(response.getChunkCount()).isEqualTo(1);
        verify(chunkMapper).delete(any(Wrapper.class));
        verify(documentMapper).deleteById("DOC_OLD");
        verify(documentMapper).insert(any(DataDocument.class));
        verify(chunkMapper).insert(any(DataDocumentChunk.class));
    }
}
