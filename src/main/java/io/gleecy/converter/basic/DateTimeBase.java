package io.gleecy.converter.basic;

import java.time.format.DateTimeFormatter;
import java.util.List;

public abstract class DateTimeBase extends BasicConverter{
    protected DateTimeFormatter dateTimeFormatter = null;

    protected abstract Object parse(String valueStr);

    protected abstract Object simpleConvert(Object value);

    public abstract String prefix();

    @Override
    public Object convert(Object value, List<String> errors) {
        if (value == null) return null;
        try {
            if (value instanceof CharSequence) {
                String strValue = value.toString().trim();
                if (strValue.isEmpty()) return null;
                return parse(strValue);
            }
            return simpleConvert(value);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        return null;
    }

    @Override
    public boolean initialize(String configStr) {
        if (configStr == null) {
            return false;
        }

        final int minIdx = prefix().length();
        if (configStr.length() <= minIdx || !configStr.startsWith(prefix()))
            return false;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(configStr.substring(minIdx).trim());
        return true;
    }
}
