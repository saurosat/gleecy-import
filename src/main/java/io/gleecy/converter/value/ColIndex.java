package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import io.gleecy.util.ObjectUtil;
import io.gleecy.util.StringUtil;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.Collection;
import java.util.List;

public class ColIndex extends Converter {
    public static final String PREFIX = "COL ";
    protected int colIndex = -1;

    public ColIndex(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        if (!configStr.startsWith(PREFIX)) {
            initError = this.getClass().getName() + " expect a config starting with " + PREFIX;
            return;
        }

        int idx = PREFIX.length();
        configStr = configStr.substring(idx);
        colIndex = StringUtil.toInt(configStr);
        if(colIndex < -1) { //First character is digit but next chars are not
            int invalidIdx = -1 * colIndex - 1;
            initError = "Invalid character at index: " + invalidIdx + ". Expecting a decimal number";
            return;
        }

        if(colIndex == -1) {//First char is not a digit, should be an excel column name, A-ZZ
            colIndex = StringUtil.getCsvColumnIndex(configStr);
        }
        if(colIndex < 0) {
            int invalidIdx = -1 * colIndex - 1;
            initError = "Invalid column index character: " + invalidIdx + ". Expecting 'A' to 'Z'";
            return;
        }

    }

    public int getColIndex() {
        return colIndex;
    }

    @Override
    protected Object doConvert(Object value, List<String> errors) {
        if (colIndex < 0)
            return null;
        if(value == null)
            return null;
        Object[] vals = null;
        if (value.getClass().isArray()) {
            vals = (Object[]) value;
        } else if(value instanceof Collection) {
            vals = ((Collection) value).toArray();
        } else {
            errors.add("ColIndex converter: Parameter must be an array or collection, but it is "
                    + value.getClass().getName());
            return null;
        }
        if (vals == null || colIndex >= vals.length)
            return null;
        return ObjectUtil.trimToNull(vals[colIndex]);
    }
    public static void main(String[] args) {
        String[] vals = "".split("_");
        for (String val : vals) System.out.println("item = " + val);
        System.out.println(vals.length);
    }
}
