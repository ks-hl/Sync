package dev.heliosares.sync.params.param;

import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

import java.util.UUID;

public class UUIDParam extends JSONParam<UUID> {
    public UUIDParam(JSONObject handle, String key) {
        super(handle, key, JSONMappers.UUID_MAPPER);
    }
}
