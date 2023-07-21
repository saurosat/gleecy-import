package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.TreeMap;

public class ValueConverterFactory {
    private static final TreeMap<String, Constructor<? extends Converter>> registry;
    static {
        registry = new TreeMap<>();
        try {
            register(ColIndex.PREFIX, ColIndex.class);
            register(Default.PREFIX, Default.class);
            register(Trim.PREFIX, Trim.class);
            register(Mapping.PREFIX, Mapping.class);
            register(Date.PREFIX, Date.class);
            register(Time.PREFIX, Time.class);
            register(DateTime.PREFIX, DateTime.class);
            register(FieldMapping.PREFIX, FieldMapping.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    public static void register(String prefix, Class<? extends Converter> converterClass) throws NoSuchMethodException {
        registry.put(prefix, converterClass.getDeclaredConstructor(String.class, EntityFacadeImpl.class));
    }
    private static Converter newInstance(Constructor<? extends Converter> constructor,
                                         String configStr, EntityFacadeImpl efi) {
        try {
            Converter converter = constructor.newInstance(configStr, efi);
            return converter.initError == null ? converter : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static Converter newInstance(String configStr, EntityFacadeImpl efi) {
        if(configStr == null)
            return null;
        Converter converter = null;
        Map.Entry<String, Constructor<? extends Converter>> entry = registry.floorEntry(configStr);
        if(entry != null &&  configStr.startsWith(entry.getKey())) {
            converter = newInstance(entry.getValue(), configStr, efi);
        }
        return converter;
    }

}
