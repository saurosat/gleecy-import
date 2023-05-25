package io.gleecy.converter.basic;

import io.gleecy.converter.ValueConverter;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;

public abstract class BasicConverter implements ValueConverter {
    protected BasicConverter() {}

    public static final Pattern ENTRY_DELIM = Pattern.compile("#{2}(?![^\\[\\]]*])");  //"##";
    public static final Pattern KEY_VAL_DELIM = Pattern.compile(":{1}(?![^\\[\\]]*])"); //":";
    private static final class BasicClass {
        public final String prefix;
        public final Class<? extends BasicConverter> clazz;
        public BasicClass(String prefix, Class<? extends BasicConverter> clazz) {
            this.prefix = prefix;
            this.clazz = clazz;
        }
        public ValueConverter newInstance() {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    private static final LinkedList<BasicClass> registry = new LinkedList<>();
    public static void register(String prefix, Class<? extends BasicConverter> converterClass) {
        registry.add(new BasicClass(prefix, converterClass));
    }
    public static ValueConverter newInstance(String configStr) {
        if(configStr == null)
            return null;
        ValueConverter converter = null;
        for(Iterator<BasicClass> itor = registry.iterator();
            itor.hasNext();) {
            BasicClass item = itor.next();
            if(configStr.startsWith(item.prefix)) {
                converter = item.newInstance();
                converter.initialize(configStr);
                break;
            }
        }
        return converter;
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
