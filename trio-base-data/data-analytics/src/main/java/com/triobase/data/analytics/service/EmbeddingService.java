package com.triobase.data.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.data.analytics.config.DataAnalyticsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final DataAnalyticsProperties properties;
    private final ObjectMapper objectMapper;

    public double[] embed(String text) {
        int dimension = Math.max(properties.getEmbedding().getDimension(), 8);
        double[] vector = new double[dimension];
        String normalized = text == null ? "" : text.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return vector;
        }
        byte[] digest = sha256(normalized);
        for (int i = 0; i < normalized.length(); i++) {
            int bucket = Math.floorMod(normalized.charAt(i) + digest[i % digest.length], dimension);
            vector[bucket] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    public String toJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize embedding", e);
        }
    }

    public double[] fromJson(String json) {
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (JsonProcessingException e) {
            return new double[Math.max(properties.getEmbedding().getDimension(), 8)];
        }
    }

    public double cosine(double[] left, double[] right) {
        int size = Math.min(left.length, right.length);
        double dot = 0.0;
        for (int i = 0; i < size; i++) {
            dot += left[i] * right[i];
        }
        return dot;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void normalize(double[] vector) {
        double sum = Arrays.stream(vector).map(value -> value * value).sum();
        if (sum == 0.0) {
            return;
        }
        double length = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / length;
        }
    }
}
