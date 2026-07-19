package com.triobase.common.action.definition;

import com.triobase.common.action.enums.ActionErrorCategory;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class ActionRetryPolicy {
    private int maxAttempts;
    private long initialDelayMillis;
    private long maxDelayMillis;
    private double backoffMultiplier = 1.0D;
    private Set<ActionErrorCategory> retryableCategories = new LinkedHashSet<>();
}
