package com.ispf.server.object;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;

import java.time.Instant;

public record ObjectChangeEvent(
        ObjectChangeType type,
        String path,
        String variableName,
        Instant timestamp,
        Long revision,
        String changedBy
) {
    public static ObjectChangeEvent of(ObjectChangeType type, String path) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), null, null);
    }

    public static ObjectChangeEvent of(ObjectChangeType type, String path, long revision, String changedBy) {
        return new ObjectChangeEvent(type, path, null, Instant.now(), revision, changedBy);
    }

    public static ObjectChangeEvent variableUpdated(String path, String variableName) {
        return new ObjectChangeEvent(ObjectChangeType.VARIABLE_UPDATED, path, variableName, Instant.now(), null, null);
    }

    public static ObjectChangeEvent variableUpdated(String path, String variableName, long revision, String changedBy) {
        return new ObjectChangeEvent(
                ObjectChangeType.VARIABLE_UPDATED,
                path,
                variableName,
                Instant.now(),
                revision,
                changedBy
        );
    }

    public static ObjectChangeEvent eventFired(String path, String eventName) {
        return new ObjectChangeEvent(ObjectChangeType.EVENT_FIRED, path, eventName, Instant.now(), null, null);
    }
}
