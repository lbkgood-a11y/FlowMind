package com.triobase.data.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.data.analytics.config.DataAnalyticsProperties;
import com.triobase.data.analytics.dto.HybridQueryRequest;
import com.triobase.data.analytics.dto.HybridQueryResponse;
import com.triobase.data.analytics.dto.SemanticChunkResponse;
import com.triobase.data.analytics.dto.SemanticQueryRequest;
import com.triobase.data.analytics.dto.SemanticQueryResponse;
import com.triobase.data.analytics.dto.StructuredQueryRequest;
import com.triobase.data.analytics.dto.StructuredQueryResponse;
import com.triobase.data.analytics.entity.DataDataset;
import com.triobase.data.analytics.entity.DataDocument;
import com.triobase.data.analytics.entity.DataDocumentChunk;
import com.triobase.data.analytics.mapper.DataDocumentChunkMapper;
import com.triobase.data.analytics.mapper.DataDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataQueryService {

    private static final Set<String> ALLOWED_SAMPLE_FILTERS = Set.of("applicant", "department", "status");

    private final DatasetCatalogService catalogService;
    private final DataDocumentChunkMapper chunkMapper;
    private final DataDocumentMapper documentMapper;
    private final EmbeddingService embeddingService;
    private final DataAnalyticsProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final QueryAuditService auditService;

    public StructuredQueryResponse structured(StructuredQueryRequest request) {
        long start = now();
        DataDataset dataset = catalogService.requireActiveByKey(request.getDatasetKey());
        int page = Math.max(request.getPage(), 1);
        int size = capPageSize(request.getSize());

        if (!"data_sample_expense".equals(dataset.getBackingTable())) {
            throw new BizException(40003, "UNSUPPORTED_DATASET_BACKING");
        }

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : request.getFilters().entrySet()) {
            if (entry.getValue() == null || !ALLOWED_SAMPLE_FILTERS.contains(entry.getKey())) {
                continue;
            }
            where.append(" and ").append(toColumn(entry.getKey())).append(" = ?");
            args.add(String.valueOf(entry.getValue()));
        }

        Long total = jdbcTemplate.queryForObject("select count(*) from data_sample_expense" + where,
                Long.class, args.toArray());
        List<Object> rowArgs = new java.util.ArrayList<>(args);
        rowArgs.add(size);
        rowArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select id, applicant, department, amount, reason, status, submitted_at as "submittedAt"
                from data_sample_expense
                """ + where + " order by submitted_at desc limit ? offset ?", rowArgs.toArray());

        StructuredQueryResponse response = new StructuredQueryResponse();
        response.setStatus("OK");
        response.setDatasetKey(dataset.getDatasetKey());
        response.setFields(catalogService.listFields(dataset.getId()));
        response.setRows(rows);
        response.setTotal(total == null ? 0 : total);
        response.setPage(page);
        response.setSize(size);
        response.setElapsedMs(now() - start);
        return response;
    }

    public SemanticQueryResponse semantic(SemanticQueryRequest request) {
        long start = now();
        int topK = capTopK(request.getTopK());
        double[] queryVector = embeddingService.embed(request.getQuery());
        LambdaQueryWrapper<DataDocumentChunk> wrapper = new LambdaQueryWrapper<DataDocumentChunk>()
                .orderByDesc(DataDocumentChunk::getCreatedAt);
        if (StringUtils.hasText(request.getCollectionKey())) {
            wrapper.eq(DataDocumentChunk::getCollectionKey, request.getCollectionKey().trim());
        }
        List<SemanticChunkResponse> chunks = chunkMapper.selectList(wrapper)
                .stream()
                .map(chunk -> toSemanticChunk(chunk, queryVector))
                .filter(chunk -> chunk.getScore() >= properties.getQuery().getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(SemanticChunkResponse::getScore).reversed())
                .limit(topK)
                .toList();

        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setStatus("OK");
        response.setCollectionKey(request.getCollectionKey());
        response.setTopK(topK);
        response.setChunks(chunks);
        response.setElapsedMs(now() - start);
        return response;
    }

    public HybridQueryResponse hybrid(HybridQueryRequest request, String operatorId, String operatorName) {
        long start = now();
        String mode = request.getMode().trim().toUpperCase();
        HybridQueryResponse response = new HybridQueryResponse();
        response.setMode(mode);

        if (mode.equals("STRUCTURED") || mode.equals("HYBRID")) {
            if (request.getStructured() == null) {
                throw new BizException(40004, "STRUCTURED_QUERY_REQUIRED");
            }
            response.setStructured(structured(request.getStructured()));
        } else {
            response.setStructured(skippedStructured());
        }

        if (mode.equals("SEMANTIC") || mode.equals("HYBRID")) {
            if (request.getSemantic() == null) {
                throw new BizException(40005, "SEMANTIC_QUERY_REQUIRED");
            }
            response.setSemantic(semantic(request.getSemantic()));
        } else {
            response.setSemantic(skippedSemantic());
        }

        if (!mode.equals("STRUCTURED") && !mode.equals("SEMANTIC") && !mode.equals("HYBRID")) {
            throw new BizException(40006, "INVALID_QUERY_MODE");
        }

        response.setElapsedMs(now() - start);
        auditService.record(mode, response.getStructured().getDatasetKey(), operatorId, operatorName,
                response.getElapsedMs(), response.getStructured().getRows().size(),
                response.getSemantic().getChunks().size());
        return response;
    }

    private SemanticChunkResponse toSemanticChunk(DataDocumentChunk chunk, double[] queryVector) {
        DataDocument document = documentMapper.selectById(chunk.getDocumentId());
        SemanticChunkResponse response = new SemanticChunkResponse();
        response.setDocumentId(chunk.getDocumentId());
        response.setTitle(document == null ? "" : document.getTitle());
        response.setCollectionKey(chunk.getCollectionKey());
        response.setChunkIndex(chunk.getChunkIndex());
        response.setContent(chunk.getContent());
        response.setScore(embeddingService.cosine(queryVector, embeddingService.fromJson(chunk.getEmbeddingJson())));
        return response;
    }

    private StructuredQueryResponse skippedStructured() {
        StructuredQueryResponse response = new StructuredQueryResponse();
        response.setStatus("SKIPPED");
        response.setDatasetKey(null);
        response.setFields(List.of());
        response.setRows(List.of());
        response.setTotal(0);
        response.setPage(1);
        response.setSize(0);
        response.setElapsedMs(0);
        return response;
    }

    private SemanticQueryResponse skippedSemantic() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setStatus("SKIPPED");
        response.setTopK(0);
        response.setChunks(List.of());
        response.setElapsedMs(0);
        return response;
    }

    private int capPageSize(int requested) {
        int positive = requested <= 0 ? 20 : requested;
        return Math.min(positive, properties.getQuery().getMaxPageSize());
    }

    private int capTopK(int requested) {
        int positive = requested <= 0 ? 5 : requested;
        return Math.min(positive, properties.getQuery().getMaxTopK());
    }

    private String toColumn(String field) {
        if ("submittedAt".equals(field)) {
            return "submitted_at";
        }
        return field;
    }

    private long now() {
        return Instant.now().toEpochMilli();
    }
}
