package com.budgetflow.orchestrator.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A command to a participant, without the saga/command ids the transport adds. Field values may be null. */
public record SagaCommand(String type, Map<String, Object> fields) {

    public SagaCommand {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    static SagaCommand of(String type, Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new SagaCommand(type, fields);
    }
}
