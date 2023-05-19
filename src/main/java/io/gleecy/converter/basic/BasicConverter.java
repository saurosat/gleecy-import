package io.gleecy.converter.basic;

import io.gleecy.converter.ValueConverter;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;

public abstract class BasicConverter implements ValueConverter {
    protected BasicConverter() {}

    public static final Pattern ENTRY_DELIM = Pattern.compile("\\#{2}(?![^\\[\\]]*])");  //"##";
    public static final Pattern KEY_VAL_DELIM = Pattern.compile("\\:{1}(?![^\\[\\]]*])"); //":";
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
    private static LinkedList<BasicClass> registry = new LinkedList<>();
    static {
        register(ColIndex.PREFIX, ColIndex.class);
        register(DefaultValue.PREFIX, DefaultValue.class);
        register(Trim.PREFIX, Trim.class);
        register(ValueMapping.PREFIX, ValueMapping.class);
        register(Date.PREFIX, Date.class);
        register(Time.PREFIX, Time.class);
        register(DateTime.PREFIX, DateTime.class);
        register(FieldMapping.PREFIX, FieldMapping.class);
    }
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

    public static ValueConverter newInstance(String configStr, EntityFacadeImpl efi) {
        ValueConverter converter =newInstance(configStr);
        if(converter == null) {
            return null;
        }
        if(!converter.initialize(configStr)) {
            return null;
        }
        converter.load(efi);
        return converter;
    }
}
