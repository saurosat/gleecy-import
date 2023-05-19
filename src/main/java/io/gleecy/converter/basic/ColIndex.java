package io.gleecy.converter.basic;

import java.util.List;

public class ColIndex extends BasicConverter {
    public static final String PREFIX = "COL ";
    public static final int MIN_CHAR = 'A';
    public static final int MAX_CHAR = 'Z';
    public static final int BASE = MAX_CHAR - MIN_CHAR + 1;
    protected int colIndex = -1;

    public int getColIndex() {
        return colIndex;
    }

    public boolean initialize(String configStr) {
        if (configStr == null) {
            return false;
        }

        final int minIdx = PREFIX.length();
        if (configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
            return false;

        char ch = configStr.charAt(minIdx);
        int colIdx = Character.toUpperCase(ch) - MIN_CHAR;
        if (colIdx >= BASE || colIdx < 0) {
            throw new IllegalArgumentException("Character " + ch + " is not a valid column name in CSV files");
        }

        for (int i = minIdx + 1; i < configStr.length(); i++) {
            char c = configStr.charAt(i);
            int unitsNo = Character.toUpperCase(c) - MIN_CHAR;
            if (unitsNo >= BASE || unitsNo < 0) {
                throw new IllegalArgumentException("Character " + c + " is not a valid column name in CSV files");
            }
            colIdx = (colIdx + 1) * BASE + unitsNo;
        }

        this.colIndex = colIdx;
        return true;
    }

    @Override
    public Object convert(Object value, List<String> errors) {
        if (colIndex < 0)
            return null;
        Object obj = BasicConverter.trimToNull(value);
        if (obj == null)
            return null;
        if (obj.getClass().isArray()) {
            Object[] vals = (Object[]) value;
            if (colIndex >= vals.length)
                return null;
            return vals[colIndex];
        }
        errors.add("ColIndex converter: Parameter must be an array, but it is "
                + value.getClass().getName());
        return null;
    }
}
