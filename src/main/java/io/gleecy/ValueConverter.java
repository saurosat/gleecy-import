package io.gleecy;

import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public interface ValueConverter {
    Object convert(Object value, List<String> errors);
    boolean initialize(String configStr);
    default boolean load(EntityFacadeImpl efi) { //To be override
        return true;
    }
    public static final Pattern ENTRY_DELIM = Pattern.compile("\\#{2}(?![^\\[\\]]*])");  //"##";
    public static final Pattern KEY_VAL_DELIM = Pattern.compile("\\:{1}(?![^\\[\\]]*])"); //":";

    public static class Trim implements ValueConverter {
        public static final String PREFIX = "TRIM ";
        @Override
        public Object convert(Object value, List<String> errors) {
            return trimToNull(value);
        }
        @Override
        public boolean initialize(String configStr) {
            if(configStr == null) {
                return false;
            }
            final int minIdx = PREFIX.length();
            if(configStr.length() < minIdx || !configStr.startsWith(PREFIX))
                return false;
            return true;
        }
    }
    public static class ColIndex implements ValueConverter {
        public static final String PREFIX = "COL ";
        public static final int MIN_CHAR = 'A';
        public static final int MAX_CHAR = 'Z';
        public static final int BASE = MAX_CHAR - MIN_CHAR + 1;
        int colIndex = -1;
        ColIndex() {}
        public int getColIndex() {return colIndex; }
        public boolean initialize(String configStr) {
            if(configStr == null) {
                return false;
            }

            final int minIdx = PREFIX.length();
            if(configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
                return false;

            char ch = configStr.charAt(minIdx);
            int colIdx = Character.toUpperCase(ch) - MIN_CHAR;
            if(colIdx >= BASE || colIdx < 0) {
                throw new IllegalArgumentException("Character " + ch + " is not a valid column name in CSV files");
            }

            for(int i = minIdx + 1; i < configStr.length(); i++) {
                char c = configStr.charAt(i);
                int unitsNo = Character.toUpperCase(c) - MIN_CHAR;
                if(unitsNo >= BASE || unitsNo < 0) {
                    throw new IllegalArgumentException("Character " + c + " is not a valid column name in CSV files");
                }
                colIdx = (colIdx + 1) * BASE + unitsNo;
            }

            this.colIndex = colIdx;
            return true;
        }
        @Override
        public Object convert(Object value, List<String> errors) {
            if(colIndex < 0)
                return null;
            Object obj = trimToNull(value);
            if(obj == null)
                return null;
            if(obj.getClass().isArray()) {
                Object[] vals = (Object[]) value;
                if(colIndex >= vals.length)
                    return null;
                return vals[colIndex];
            }
            errors.add("ColIndex converter: Parameter must be an array, but it is "
                    +  value.getClass().getName());
            return null;
        }
    }
    public static class DefaultValue implements ValueConverter {
        public static final String PREFIX = "DEFAULT ";
        DefaultValue(){}
        protected String defaultValue = null;
        @Override
        public Object convert(Object value, List<String> errors) {
            Object obj = trimToNull(value);
            return obj == null ? defaultValue : obj;
        }

        @Override
        public boolean initialize(String configStr) {
            if(configStr == null)
                return false;
            final int minIdx = PREFIX.length();
            if(configStr.length() < minIdx || !configStr.startsWith(PREFIX)) //<, not <=
                return false;
            this.defaultValue = configStr.substring(minIdx);
            return true;
        }
    }
    public static class ValueMapping implements ValueConverter {
        public static final String PREFIX = "VALUE_MAPPING ";

        ValueMapping(){}
        protected HashMap valueMap = new HashMap();
        @Override
        public Object convert(Object value, List<String> errors) {
            Object obj = trimToNull(value);
            if(obj == null) return null;
            return valueMap.get(obj);
        }

        @Override
        public boolean initialize(String configStr) {
            if(configStr == null)
                return false;
            final int minIdx = PREFIX.length();
            if(configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
                return false;

            HashMap<String, String> valueMap = new HashMap<>();
            String[] configs = ENTRY_DELIM.split(configStr.substring(minIdx));
            for(String config : configs) {
                config = config.trim();
                if(config.isEmpty())
                    continue;
                String[] key_val = KEY_VAL_DELIM.split(config);
                key_val[0] = key_val[0].trim();
                if(key_val.length != 2 || key_val[0].isEmpty())
                    throw new IllegalArgumentException("Illegal Value mapping: " + config);
                valueMap.put(key_val[0], key_val[1]);
            }
            this.valueMap = valueMap;
            return true;
        }
    }

    /**
     * Search Field value of pre-defined entity (entityName.toFieldName) by comparing cell value with the
     *  value in entityName.fromFieldName <br>
     * Related entities is preloaded using configured conditions <br>
     * Example: following configuration will search column Description in Enumeration table by CSV cell value at column AA, then return the enumId: <br>
     *  ###COL AA ###FIELD_MAPPING ##_entity:moqui.basic.Enumeration ##FR:description ##TO:enumId ##_comp:equals ##enumTypeId:ProductType
     */
    public static class FieldMapping implements ValueConverter {
        public static final String PREFIX = "FIELD_MAPPING ";
        public static final String ENTITY_PREFIX = "_entity";
        public static final String MAP_FR_PREFIX = "_from";
        public static final String MAP_TO_PREFIX = "_to";

        /**
         * Comparison operators:
         *      "=", "equals",
         *      "not-equals", "not-equal", "!=", "<>",
         *      "less-than", "less", "<",
         *      "greater-than", "greater", ">",
         *      "less-than-equal-to", "less-equals", "<=",
         *      "greater-than-equal-to", "greater-equals", ">=",
         *      "in", "IN", "not-in, "NOT IN",
         *      "between", "BETWEEN", "not-between", "NOT BETWEEN",
         *      "like", "LIKE", "not-like", "NOT LIKE",
         *      "is-null", "IS NULL", "is-not-null","IS NOT NULL"
         */
        public static final String COMPARISON_PREFIX = "_comp";
        /**
         * Join operators: AND, and, OR, or
         */
        public static final String JOIN_PREFIX = "_join";
        /**
         * TODO: support nested logic expression, like: (fieldA='value1' AND (fieldB='value2.1' OR fieldB='value2.2'))
         * Group multiple single conditions in to group
         */
        //public static final String GROUP_PREFIX = "_group";

        String entityName = null;
        String fromFieldName = null;
        String toFieldName = null;
        Map<String, String> fieldValueMap = new HashMap<>();
        Map<String, Object> conditionMap = new HashMap<>();
        FieldMapping() {}
        public FieldMapping addEntity(EntityValue entity) {
            String from = getKey(entity.getString(fromFieldName));
            fieldValueMap.put(from, entity.getString(toFieldName));
            return this;
        }
        public FieldMapping addEntities(EntityList entities) {
            for (EntityValue entity: entities) {
                this.addEntity(entity);
            }
            return this;
        }

        @Override
        public Object convert(Object fromValue, List<String> errors) {
            fromValue = ValueConverter.trimToNull(fromValue);
            if(fromValue == null)
                return null;
            return fieldValueMap.get(getKey(fromValue.toString()));
        }
        @Override
        public boolean initialize(String configStr) {
            if(configStr == null)
                return false;
            final int minIdx = PREFIX.length();
            if(configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
                return false;

            String[] configs = ENTRY_DELIM.split(configStr.substring(minIdx));
            for(String config : configs) {
                config = config.trim();
                if(config.isEmpty())
                    continue;

                String[] key_val = KEY_VAL_DELIM.split(config);
                //System.out.println(key_val[0] + ": " + key_val[1]);
                key_val[0] = key_val[0].trim();
                if(key_val.length != 2 || key_val[0].isEmpty())
                    throw new IllegalArgumentException("Illegal Value mapping: " + config);
                if(ENTITY_PREFIX.equals(key_val[0])) {
                    entityName = key_val[1];
                } else if(MAP_FR_PREFIX.equals(key_val[0])) {
                    fromFieldName = key_val[1];
                } else if (MAP_TO_PREFIX.equals(key_val[0])) {
                    toFieldName = key_val[1];
                } else {
                    //TODO: validate key_val[0]: must be exactly a field name or an operator
                    conditionMap.put(key_val[0], key_val[1]);
                }
            }
            return true;
        }
        public boolean load(EntityFacadeImpl efi) {
            if(efi == null) {
                return false;
            }
            EntityList entityList = efi.find(entityName).condition(conditionMap).useCache(true).list();
            this.addEntities(entityList);
            return true;
        }
    }
    public static abstract class DateTimeBase implements ValueConverter {
        DateTimeBase() {}
        protected DateTimeFormatter dateTimeFormatter = null;
        protected abstract Object parse(String valueStr);
        protected abstract Object simpleConvert(Object value);
        protected abstract String getPrefix();
        @Override
        public Object convert(Object value, List<String> errors) {
            if(value == null) return null;
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

        @Override
        public boolean initialize(String configStr) {
            if(configStr == null) {
                return false;
            }

            final int minIdx = getPrefix().length();
            if(configStr.length() <= minIdx || !configStr.startsWith(getPrefix()))
                return false;
            this.dateTimeFormatter = DateTimeFormatter.ofPattern(configStr.substring(minIdx).trim());
            return true;
        }
    }
    public static class Date extends DateTimeBase {
        public static final String PREFIX = "DATE_FORMAT ";
        Date() {
            super();
        }
        @Override
        public String getPrefix() { return PREFIX; }
        @Override
        protected Object parse(String valueStr) {
            return java.sql.Date.valueOf(
                    LocalDate.parse(valueStr, this.dateTimeFormatter));
        }

        @Override
        protected Object simpleConvert(Object value) {
            if(value instanceof LocalDate) {
                java.sql.Date.valueOf((LocalDate) value);
            }
            throw new IllegalArgumentException("Argument cannot be converted to java.sql.Date: " + value.getClass() + ":" + value.toString());
        }
    }
    public static class Time extends DateTimeBase {
        public static final String PREFIX = "TIME_FORMAT ";
        Time() {
            super();
        }
        @Override
        public String getPrefix() { return PREFIX; }
        @Override
        protected Object parse(String valueStr) {
            return java.sql.Time.valueOf(
                    LocalTime.parse(valueStr, this.dateTimeFormatter));
        }

        @Override
        protected Object simpleConvert(Object value) {
            if(value instanceof LocalTime) {
                java.sql.Time.valueOf((LocalTime) value);
            }
            throw new IllegalArgumentException("Argument cannot be converted to java.sql.Date: " + value.getClass() + ":" + value.toString());
        }
    }
    public static class DateTime extends DateTimeBase {
        public static final String PREFIX = "DATE_TIME_FORMAT ";
        DateTime() {
            super();
        }
        @Override
        public String getPrefix() { return PREFIX; }
        @Override
        protected Object parse(String valueStr) {
            return java.sql.Timestamp.valueOf(
                    LocalDateTime.parse(valueStr, this.dateTimeFormatter));
        }

        @Override
        protected Object simpleConvert(Object value) {
            if(value instanceof LocalDateTime) {
                java.sql.Timestamp.valueOf((LocalDateTime) value);
            }
            throw new IllegalArgumentException("Argument cannot be converted to java.sql.Date: " + value.getClass() + ":" + value.toString());
        }
    }
    private static String getKey(String s) {
        String[] tokens = s.split(" ");
        StringBuilder sbKey = new StringBuilder();
        for(int i = 0; i < tokens.length; i++) {
            if(tokens[i].isEmpty()) {
                continue;
            }
            sbKey.append('_').append(tokens[i].toUpperCase());
        }
        return sbKey.toString();
    }
    public static Object trimToNull(Object value) {
        if(value == null) return null;
        if(value instanceof CharSequence) {
            String strValue = value.toString().trim();
            return strValue.isEmpty() ? null : strValue;
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0 ? null : value;
        }
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).isEmpty() ? null : value;
        }
        if (value instanceof Map<?, ?>) {
            return ((Map<?, ?>) value).isEmpty() ? null : value;
        }
        if (value instanceof Optional<?>) {
            return ((Optional<?>) value).isEmpty() ? null : value;
        }
        return value;
    }
}
