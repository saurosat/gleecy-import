package io.gleecy.converter.basic;

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
        if (configStr.length() < minIdx || !configStr.startsWith(PREFIX))
            return false;
        return true;
    }
}
