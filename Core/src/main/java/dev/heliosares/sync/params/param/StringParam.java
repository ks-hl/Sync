package dev.heliosares.sync.params.param;

import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

public class StringParam extends JSONParam<String> {
    public StringParam(JSONObject handle, String key) {
        super(handle, key, JSONMappers.STRING);
    }
}
