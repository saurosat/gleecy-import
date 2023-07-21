package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.time.format.DateTimeFormatter;
import java.util.List;

public abstract class DateTimeBase extends Converter {
    protected DateTimeFormatter dateTimeFormatter = null;

    public DateTimeBase(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        final int minIdx = prefix().length();
        if (configStr.length() <= minIdx || !configStr.startsWith(prefix()))
            initError = this.getClass().getName() + " expect a config starting with " + prefix();
        else
            this.dateTimeFormatter = DateTimeFormatter.ofPattern(configStr.substring(minIdx).trim());
    }

    protected abstract Object parse(String valueStr);

    protected abstract Object simpleConvert(Object value);

    public abstract String prefix();

    @Override
    protected Object doConvert(Object value, List<String> errors) {
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
}
