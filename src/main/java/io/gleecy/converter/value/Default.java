package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import io.gleecy.util.ObjectUtil;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.List;

public class Default extends Converter {
    public static final String PREFIX = "DEFAULT ";

    protected String defaultValue = null;

    public Default(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        final int minIdx = PREFIX.length();
        if (configStr.length() < minIdx || !configStr.startsWith(PREFIX)) //<, not <=
            initError = this.getClass().getName() + " expect a config starting with " + PREFIX;
        else
            this.defaultValue = configStr.substring(minIdx).trim();
    }

    @Override
    protected Object doConvert(Object value, List<String> errors) {
        if(value == null) {
            return defaultValue;
        }
        if(value instanceof String) {
            String sValue = ((String) value).trim();
            return sValue.isEmpty() ? defaultValue : sValue;
        }
        return defaultValue;
    }
}
