package com.triobase.common.core.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

public class UlidIdentifierGenerator implements IdentifierGenerator {

    @Override
    public String nextUUID(Object entity) {
        return UlidGenerator.nextUlid();
    }

    @Override
    public Number nextId(Object entity) {
        throw new UnsupportedOperationException("TrioBase uses ULID string identifiers; use IdType.ASSIGN_UUID");
    }
}
