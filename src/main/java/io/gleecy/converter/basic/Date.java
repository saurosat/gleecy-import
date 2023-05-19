package io.gleecy.converter.basic;

import java.time.LocalDate;

public class Date extends DateTimeBase {
    public static final String PREFIX = "DATE_FORMAT ";

    Date() {
        super();
    }

    @Override
    public String prefix() {
        return PREFIX;
    }

    @Override
    protected Object parse(String valueStr) {
        return java.sql.Date.valueOf(
                LocalDate.parse(valueStr, this.dateTimeFormatter));
    }

    @Override
    protected Object simpleConvert(Object value) {
        if (value instanceof LocalDate) {
            java.sql.Date.valueOf((LocalDate) value);
        }
        throw new IllegalArgumentException("Argument cannot be converted to java.sql.Date: " + value.getClass() + ":" + value.toString());
    }
}
