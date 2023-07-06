package io.gleecy.converter;

import io.gleecy.converter.basic.*;

import java.util.Map;
import java.util.TreeMap;

public class ConverterRegistry {
    static {
        register(ColIndex.PREFIX, ColIndex.class);
        register(DefaultValue.PREFIX, DefaultValue.class);
        register(Trim.PREFIX, Trim.class);
        register(ValueMapping.PREFIX, ValueMapping.class);
        register(io.gleecy.converter.basic.Date.PREFIX, Date.class);
        register(Time.PREFIX, Time.class);
        register(DateTime.PREFIX, DateTime.class);
        register(FieldMapping.PREFIX, FieldMapping.class);
    }
    private static final TreeMap<String, Class<? extends BasicConverter>> registry = new TreeMap<>();
    public static void register(String prefix, Class<? extends BasicConverter> converterClass) {
        registry.put(prefix, converterClass);
    }
    private static BasicConverter newInstance(Class<? extends BasicConverter> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static BasicConverter newInstance(String configStr) {
        if(configStr == null)
            return null;
        BasicConverter converter = null;
        Map.Entry<String, Class<? extends BasicConverter>> entry = registry.floorEntry(configStr);
        if(entry != null &&  configStr.startsWith(entry.getKey())) {
            converter = newInstance(entry.getValue());
            converter.initialize(configStr);
        }
        return converter;
    }

}
