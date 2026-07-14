package com.triobase.data.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.data.analytics.dto.DocumentIngestResponse;
import com.triobase.data.analytics.dto.IngestDocumentRequest;
import com.triobase.data.analytics.entity.DataDocument;
import com.triobase.data.analytics.entity.DataDocumentChunk;
import com.triobase.data.analytics.mapper.DataDocumentChunkMapper;
import com.triobase.data.analytics.mapper.DataDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DataDocumentMapper documentMapper;
    private final DataDocumentChunkMapper chunkMapper;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    @Transactional
    public DocumentIngestResponse ingest(IngestDocumentRequest request, String operator) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(40002, "EMPTY_DOCUMENT");
        }
        String collectionKey = request.getCollectionKey().trim();
        String sourceKey = normalizeBlank(request.getSourceKey());
        LocalDateTime now = LocalDateTime.now();

        if (sourceKey != null) {
            DataDocument existing = documentMapper.selectOne(new LambdaQueryWrapper<DataDocument>()
                    .eq(DataDocument::getCollectionKey, collectionKey)
                    .eq(DataDocument::getSourceKey, sourceKey)
                    .last("LIMIT 1"));
            if (existing != null) {
                chunkMapper.delete(new LambdaQueryWrapper<DataDocumentChunk>()
                        .eq(DataDocumentChunk::getDocumentId, existing.getId()));
                documentMapper.deleteById(existing.getId());
            }
        }

        List<String> chunks = chunkingService.split(request.getContent());
        DataDocument document = new DataDocument();
        document.setId(UlidGenerator.nextUlid());
        document.setDatasetId(normalizeBlank(request.getDatasetId()));
        document.setCollectionKey(collectionKey);
        document.setSourceKey(sourceKey);
        document.setTitle(request.getTitle().trim());
        document.setStatus("ACTIVE");
        document.setChunkCount(chunks.size());
        document.setCreatedBy(defaultOperator(operator));
        document.setUpdatedBy(defaultOperator(operator));
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            DataDocumentChunk chunk = new DataDocumentChunk();
            chunk.setId(UlidGenerator.nextUlid());
            chunk.setDocumentId(document.getId());
            chunk.setCollectionKey(collectionKey);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setEmbeddingJson(embeddingService.toJson(embeddingService.embed(chunkText)));
            chunk.setTokenCount(chunkText.length());
            chunk.setCreatedBy(defaultOperator(operator));
            chunk.setUpdatedBy(defaultOperator(operator));
            chunk.setCreatedAt(now);
            chunk.setUpdatedAt(now);
            chunkMapper.insert(chunk);
        }

        DocumentIngestResponse response = new DocumentIngestResponse();
        response.setDocumentId(document.getId());
        response.setCollectionKey(document.getCollectionKey());
        response.setSourceKey(document.getSourceKey());
        response.setTitle(document.getTitle());
        response.setChunkCount(chunks.size());
        return response;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "SYSTEM";
    }
}
