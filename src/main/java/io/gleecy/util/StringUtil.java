package io.gleecy.util;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
    public static final Pattern namedPattern = Pattern.compile("[$][{](\\w+)}");
    public static String format(String template, Map<String, Object> parameters) {
        StringBuilder newTemplate = new StringBuilder(template);
        List<Object> valueList = new ArrayList<>();

        Matcher matcher = namedPattern.matcher(template);

        while (matcher.find()) {
            String key = matcher.group(1);

            String paramName = "${" + key + "}";
            int index = newTemplate.indexOf(paramName);
            if (index != -1) {
                newTemplate.replace(index, index + paramName.length(), "%s");
                valueList.add(parameters.get(key));
            }
        }

        return String.format(newTemplate.toString(), valueList.toArray());
    }

    public static final int getCsvColumnIndex(String columnName) {
        columnName = columnName.trim().toUpperCase();
        int result = 0;
        for (int i = 0; i < columnName.length(); i++){
            char ch = columnName.charAt(i);
            int val = ch - 'A' + 1;
            if(val < 1 || val > 26) {
                return -1 * (i + 1);
            }
            result *= 26;
            result += val;
        }
        return result - 1; //zero-based index
    }
    public static final int toInt(String s) {
        int val = 0;
        for (int idx = 0; idx < s.length(); idx++) {
            char ch = s.charAt(idx);
            if(!Character.isDigit(ch)) {
                return -1 * (idx + 1); //Can calculate invalid index from here
            }
            //else: if is a digit
            val = val * 10 +  Character.digit(ch, 10);
        }
        return val;
    }
}
