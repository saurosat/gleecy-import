package io.gleecy.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class ObjectUtil {
    public static Object trimToNull(Object value) {
        if(value == null) return null;
        if(value instanceof CharSequence) {
            String strValue = value.toString().trim();
            return strValue.isEmpty() ? null : strValue;
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0 ? null : value;
        }
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).isEmpty() ? null : value;
        }
        if (value instanceof Map<?, ?>) {
            return ((Map<?, ?>) value).isEmpty() ? null : value;
        }
        if (value instanceof Optional<?>) {
            return ((Optional<?>) value).isEmpty() ? null : value;
        }
        return value;
    }
}
