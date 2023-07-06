package io.gleecy.converter.basic;

import io.gleecy.converter.BasicConverter;

import java.util.List;

public class Trim extends BasicConverter {
    public static final String PREFIX = "TRIM ";

    @Override
    public Object convert(Object value, List<String> errors) {
        return BasicConverter.trimToNull(value);
    }

    @Override
    public boolean initialize(String configStr) {
        if (configStr == null) {
            return false;
        }
        final int minIdx = PREFIX.length();
        return configStr.length() >= minIdx && configStr.startsWith(PREFIX);
    }
}
