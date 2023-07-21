package io.gleecy.converter.value;

import org.moqui.impl.entity.EntityFacadeImpl;

import java.time.LocalDateTime;

public class DateTime extends DateTimeBase {
    public static final String PREFIX = "DATE_TIME_FORMAT ";

    public DateTime(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
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
