package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import io.gleecy.util.ObjectUtil;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.List;

public class Trim extends Converter {
    public static final String PREFIX = "TRIM ";

    public Trim(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        final int minIdx = PREFIX.length();
        if (configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
            initError = this.getClass().getName() + " expect a config starting with " + PREFIX;
    }

    @Override
    protected Object doConvert(Object value, List<String> errors) {
        return ObjectUtil.trimToNull(value);
    }
}
