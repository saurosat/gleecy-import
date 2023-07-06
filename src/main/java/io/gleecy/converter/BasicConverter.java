package io.gleecy.converter;

import org.moqui.impl.entity.EntityFacadeImpl;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;

public abstract class BasicConverter {
    public static final Pattern ENTRY_DELIM = Pattern.compile("#{2}(?![^\\[\\]]*])");  //"##";
    public static final Pattern KEY_VAL_DELIM = Pattern.compile(":{1}(?![^\\[\\]]*])"); //":";
    protected BasicConverter() {}
    public abstract Object convert(Object value, List<String> errors);
    public abstract boolean initialize(String configStr);
    public boolean load(EntityFacadeImpl efi) { //To be override
        return true;
    }
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
