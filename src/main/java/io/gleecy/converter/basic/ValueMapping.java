package io.gleecy.converter.basic;

import io.gleecy.converter.BasicConverter;

import java.util.HashMap;
import java.util.List;

public class ValueMapping extends BasicConverter {
    public static final String PREFIX = "VALUE_MAPPING ";

    ValueMapping() {
    }

    protected HashMap valueMap = new HashMap();

    @Override
    public Object convert(Object value, List<String> errors) {
        //Object value = BasicConverter.trimToNull(value);
        if (value == null) return null;
        Object mapValue = valueMap.get(value);
        if(mapValue != null)
            return mapValue;
        return value;
    }

    @Override
    public boolean initialize(String configStr) {
        if (configStr == null)
            return false;
        final int minIdx = PREFIX.length();
        if (configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
            return false;

        HashMap<String, String> valueMap = new HashMap<>();
        String[] configs = BasicConverter.ENTRY_DELIM.split(configStr.substring(minIdx));
        for (String config : configs) {
            config = config.trim();
            if (config.isEmpty())
                continue;
            String[] key_val = BasicConverter.KEY_VAL_DELIM.split(config);
            key_val[0] = key_val[0].trim();
            if (key_val.length != 2 || key_val[0].isEmpty())
                throw new IllegalArgumentException("Illegal Value mapping: " + config);
            valueMap.put(key_val[0], key_val[1]);
        }
        this.valueMap = valueMap;
        return true;
    }
}
