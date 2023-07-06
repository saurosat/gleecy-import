package io.gleecy.converter.basic;

import io.gleecy.converter.BasicConverter;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search Field value of pre-defined entity (entityName.toFieldName) by comparing cell value with the
 * value in entityName.fromFieldName <br>
 * Related entities is preloaded using configured conditions <br>
 * Example: following configuration will search column Description in Enumeration table by CSV cell value at column AA, then return the enumId: <br>
 * ###COL AA ###FIELD_MAPPING ##_entity:moqui.basic.Enumeration ##FR:description ##TO:enumId ##_comp:equals ##enumTypeId:ProductType
 */
public class FieldMapping extends BasicConverter {
    public static final String PREFIX = "FIELD_MAPPING ";
    public static final String ENTITY_PREFIX = "_entity";
    public static final String MAP_FR_PREFIX = "_from";
    public static final String MAP_TO_PREFIX = "_to";

    /**
     * Comparison operators:
     * "=", "equals",
     * "not-equals", "not-equal", "!=", "<>",
     * "less-than", "less", "<",
     * "greater-than", "greater", ">",
     * "less-than-equal-to", "less-equals", "<=",
     * "greater-than-equal-to", "greater-equals", ">=",
     * "in", "IN", "not-in, "NOT IN",
     * "between", "BETWEEN", "not-between", "NOT BETWEEN",
     * "like", "LIKE", "not-like", "NOT LIKE",
     * "is-null", "IS NULL", "is-not-null","IS NOT NULL"
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
    private static String getKey(String s) {
        String[] tokens = s.split(" ");
        StringBuilder sbKey = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].isEmpty()) {
                continue;
            }
            sbKey.append('_').append(tokens[i].toUpperCase());
        }
        return sbKey.toString();
    }

    String entityName = null;
    String fromFieldName = null;
    String toFieldName = null;
    Map<String, String> fieldValueMap = new HashMap<>();
    Map<String, Object> conditionMap = new HashMap<>();

    FieldMapping() {
    }

    public FieldMapping addEntity(EntityValue entity) {
        String from = getKey(entity.getString(fromFieldName));
        fieldValueMap.put(from, entity.getString(toFieldName));
        return this;
    }

    public FieldMapping addEntities(EntityList entities) {
        for (EntityValue entity : entities) {
            this.addEntity(entity);
        }
        return this;
    }

    @Override
    public Object convert(Object fromValue, List<String> errors) {
        fromValue = BasicConverter.trimToNull(fromValue);
        if (fromValue == null)
            return null;
        return fieldValueMap.get(getKey(fromValue.toString()));
    }

    @Override
    public boolean initialize(String configStr) {
        if (configStr == null)
            return false;
        final int minIdx = PREFIX.length();
        if (configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
            return false;

        String[] configs = BasicConverter.ENTRY_DELIM.split(configStr.substring(minIdx));
        for (String config : configs) {
            config = config.trim();
            if (config.isEmpty())
                continue;

            String[] key_val = BasicConverter.KEY_VAL_DELIM.split(config);
            //System.out.println(key_val[0] + ": " + key_val[1]);
            key_val[0] = key_val[0].trim();
            if (key_val.length != 2 || key_val[0].isEmpty())
                throw new IllegalArgumentException("Illegal Value mapping: " + config);
            switch (key_val[0]) {
                case ENTITY_PREFIX:
                    entityName = key_val[1];
                    break;
                case MAP_FR_PREFIX:
                    fromFieldName = key_val[1];
                    break;
                case MAP_TO_PREFIX:
                    toFieldName = key_val[1];
                    break;
                default:
                    //TODO: validate key_val[0]: must be exactly a field name or an operator
                    conditionMap.put(key_val[0], key_val[1]);
                    break;
            }
        }
        return true;
    }

    @Override
    public boolean load(EntityFacadeImpl efi) {
        if (efi == null) {
            return false;
        }
        EntityList entityList = efi.find(entityName).condition(conditionMap).useCache(true).list();
        this.addEntities(entityList);
        return true;
    }
}
