package dev.heliosares.sync.params.mapper;

public abstract class DirectMapper<T> extends JSONMapper<T> {
    @Override
    public Object mapToJSON(T s) {
        return s;
    }
}
