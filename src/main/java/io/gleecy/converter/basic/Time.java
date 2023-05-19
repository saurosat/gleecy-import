package io.gleecy.converter.basic;

import java.time.LocalTime;

public class Time extends DateTimeBase {
    public static final String PREFIX = "TIME_FORMAT ";

    Time() {
        super();
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    protected Object parse(String valueStr) {
        return java.sql.Time.valueOf(
                LocalTime.parse(valueStr, this.dateTimeFormatter));
    }

    @Override
    protected Object simpleConvert(Object value) {
        if (value instanceof LocalTime) {
            java.sql.Time.valueOf((LocalTime) value);
        }
        throw new IllegalArgumentException("Argument cannot be converted to java.sql.Date: " + value.getClass() + ":" + value.toString());
    }
}
