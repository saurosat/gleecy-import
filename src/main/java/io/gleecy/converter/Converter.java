package io.gleecy.converter;

import io.gleecy.converter.value.UninitializedInstanceException;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.List;
import java.util.regex.Pattern;

public abstract class Converter {
    public String initError = null;
    public final String configStr;
    protected final EntityFacadeImpl efi;
    private Converter() {
        configStr = null;
        efi = null;
        initError = this.getClass().getName() + " instance is not initialized";
    }
    protected Converter(Converter tobeCloned) {
        initError = tobeCloned.initError;
        configStr = tobeCloned.configStr;
        efi = tobeCloned.efi;
    }
    public Converter(String configStr, EntityFacadeImpl efi) {
        this.configStr = configStr.trim();
        this.efi = efi;
        if (this.configStr.isEmpty()) {
            this.initError = this.getClass().getName() +
                    " is failed to initialize. Config string is null or empty";
        }
    }
    public final Object convert(Object value, List<String> errors) {
        if(initError != null)
            throw new UninitializedInstanceException(configStr,
                    "There was error while initialize this instance: " + initError);
        return doConvert(value, errors);
    }

    protected abstract Object doConvert(Object value, List<String> errors) ;
    public static final Pattern ENTRY_DELIM = Pattern.compile("#{2}(?![^\\[\\]]*])");  //"##";
    public static final Pattern KEY_VAL_DELIM = Pattern.compile(":{1}(?![^\\[\\]]*])"); //":";
}
