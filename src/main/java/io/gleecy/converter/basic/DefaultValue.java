package io.gleecy.converter.basic;

import java.util.List;

public class DefaultValue extends BasicConverter {
    public static final String PREFIX = "DEFAULT ";

    DefaultValue() {
    }

    protected String defaultValue = null;

    @Override
    public Object convert(Object value, List<String> errors) {
        Object obj = BasicConverter.trimToNull(value);
        return obj == null ? defaultValue : obj;
    }

    @Override
    public boolean initialize(String configStr) {
        if (configStr == null)
            return false;
        final int minIdx = PREFIX.length();
        if (configStr.length() < minIdx || !configStr.startsWith(PREFIX)) //<, not <=
            return false;
        this.defaultValue = configStr.substring(minIdx);
        return true;
    }
}
