package com.triobase.service.ops.service.job;

import org.springframework.stereotype.Component;

@Component
public class NoopJobHandler implements OpsJobHandler {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public String run(String params) {
        return "noop job completed";
    }
}
