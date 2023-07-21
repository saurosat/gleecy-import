package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.HashMap;
import java.util.List;

public class Mapping extends Converter {
    public static final String PREFIX = "VALUE_MAPPING";

    protected HashMap valueMap = new HashMap();

    public Mapping(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        initError = initialize();
    }

    @Override
    protected Object doConvert(Object value, List<String> errors) {
        //Object value = Converter.trimToNull(value);
        if (value == null) return null;
        Object mapValue = valueMap.get(value);
        if(mapValue != null)
            return mapValue;
        return value;
    }

    protected String initialize() {
        final int minIdx = PREFIX.length();
        if (configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
            return this.getClass().getName() + " expect a config starting with " + PREFIX;

        HashMap<String, String> valueMap = new HashMap<>();
        String[] configs = Converter.ENTRY_DELIM.split(configStr.substring(minIdx));
        for (String config : configs) {
            config = config.trim();
            if (config.isEmpty())
                continue;
            String[] key_val = Converter.KEY_VAL_DELIM.split(config);
            key_val[0] = key_val[0].trim();
            if (key_val.length != 2 || key_val[0].isEmpty())
                return "Illegal Value mapping: " + config;
            valueMap.put(key_val[0], key_val[1]);
        }
        this.valueMap = valueMap;
        return null;
    }
}
