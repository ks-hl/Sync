package dev.heliosares.sync.params.mapper;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class JSONMappers {
    public static final DirectMapper<String> STRING = new DirectMapper<>() {
        @Override
        public String mapFromJSON(Object o) {
            return Objects.toString(o);
        }
    };
    public static final DirectMapper<Boolean> BOOLEAN = new DirectMapper<>() {
        @Override
        public Boolean mapFromJSON(Object o) {
            return (Boolean) o;
        }
    };
    public static final DirectMapper<Long> LONG = new DirectMapper<>() {
        @Override
        public Long mapFromJSON(Object o) {
            return (Long) o;
        }
    };
    public static final DirectMapper<Integer> INTEGER = new DirectMapper<>() {
        @Override
        public Integer mapFromJSON(Object o) {
            return (Integer) o;
        }
    };
    public static final DirectMapper<Double> DOUBLE = new DirectMapper<>() {
        @Override
        public Double mapFromJSON(Object o) {
            if (o instanceof BigDecimal bd) return bd.doubleValue();
            if (o instanceof Integer i) return (double) i;
            return (Double) o;
        }
    };
    public static final DirectMapper<Short> SHORT = new DirectMapper<>() {
        @Override
        public Short mapFromJSON(Object o) {
            if (o instanceof Integer i) {
                return i.shortValue();
            } else if (o instanceof Short s) {
                return s;
            }
            return null;
        }
    };

    public static final JSONMapper<UUID> UUID_MAPPER = new JSONMapper<>() {
        @Override
        public String mapToJSON(UUID uuid) {
            return uuid == null ? null : uuid.toString();
        }

        @Override
        public UUID mapFromJSON(Object o) {
            if (o == null) return null;
            return UUID.fromString(Objects.toString(o));
        }
    };

    public static JSONMapper.HashMapMapper<String> STRING_MAP = new JSONMapper.HashMapMapper<>(STRING);
    public static CollectionMapper.HashSetMapper<UUID> UUID_SET = new CollectionMapper.HashSetMapper<>(UUID_MAPPER);
    public static CollectionMapper.ArrayListMapper<UUID> UUID_LIST = new CollectionMapper.ArrayListMapper<>(UUID_MAPPER);

}
