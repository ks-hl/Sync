package dev.heliosares.sync.params.param;

import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

public class IntegerParam extends JSONParam<Integer> {
    public IntegerParam(JSONObject handle, String key) {
        super(handle, key, JSONMappers.INTEGER);
    }
}
