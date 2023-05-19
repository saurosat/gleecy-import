package io.gleecy.converter.basic;

import java.time.LocalDateTime;

public class DateTime extends DateTimeBase {
    public static final String PREFIX = "DATE_TIME_FORMAT ";

    DateTime() {
        super();
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    protected Object parse(String valueStr) {
        return java.sql.Timestamp.valueOf(
                LocalDateTime.parse(valueStr, this.dateTimeFormatter));
    }

    @Override
    protected Object simpleConvert(Object value) {
        if (value instanceof LocalDateTime) {
            java.sql.Timestamp.valueOf((LocalDateTime) value);
        }
        throw new IllegalArgumentException("Argument cannot be converted to java.sql.Date: " + value.getClass() + ":" + value.toString());
    }
}
