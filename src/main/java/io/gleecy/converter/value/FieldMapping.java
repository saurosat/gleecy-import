package io.gleecy.converter.value;

import io.gleecy.converter.Converter;
import io.gleecy.util.ObjectUtil;
import org.moqui.entity.EntityFind;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search Field value of pre-defined entity (entityName.toFieldName) by comparing cell value with the
 * value in entityName.fromFieldName <br>
 * Related entities of Enum tables is preloaded using configured conditions <br>
 * Example: following configuration will search column Description in Enumeration table by CSV cell value at column AA, then return the enumId: <br>
 * ###COL AA ###FIELD_MAPPING ##_entity:moqui.basic.Enumeration ##_from:description ##_to:enumId ##_comp:equals ##enumTypeId:ProductType
 */
public class FieldMapping extends Converter {
    public static final String PREFIX = "FIELD_MAPPING";
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

    public FieldMapping(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        initError = initialize();
    }
    private String entityName = null;
    private String fromFieldName = null;
    private String toFieldName = null;
    private boolean preCached = false;
    private final Map<String, String> fieldValueMap = new HashMap<>();
    private final Map<String, Object> conditionMap = new HashMap<>();

    public FieldMapping addToMappingCache(EntityValue entity) {
        String from = getKey(entity.getString(fromFieldName));
        fieldValueMap.put(from, entity.getString(toFieldName));
        return this;
    }

    public FieldMapping addToMappingCache(EntityList entities) {
        for (EntityValue entity : entities) {
            this.addToMappingCache(entity);
        }
        return this;
    }

    @Override
    protected Object doConvert(Object fromValue, List<String> errors) {
        fromValue = ObjectUtil.trimToNull(fromValue);
        if (fromValue == null)
            return null;
        if(!fieldValueMap.isEmpty()) //pre-cached:
            return fieldValueMap.get(getKey(fromValue.toString()));
        EntityFind query = efi.find(entityName).
                selectField(fromFieldName).
                selectField(toFieldName).
                condition(fromFieldName, fromValue);
        if(!conditionMap.isEmpty()) {
            query.condition(conditionMap);
        }
        EntityList entityList = query.list();
        if(entityList.isEmpty()) {
            return null;
        }
        return entityList.getFirst().get(toFieldName);
    }

    protected String initialize() {
        final int minIdx = PREFIX.length();
        if (configStr.length() <= minIdx || !configStr.startsWith(PREFIX))
            return this.getClass().getName() + " expect a config starting with " + PREFIX;

        String[] configs = Converter.ENTRY_DELIM.split(configStr.substring(minIdx));
        for (String config : configs) {
            config = config.trim();
            if (config.isEmpty())
                continue;

            String[] key_val = Converter.KEY_VAL_DELIM.split(config);
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
        if (efi == null) {
            return this.getClass().getName() + " expect a not-null EntityFacadeImpl instance";
        }
        preCached = entityName.equals("moqui.basic.Enumeration");
        if(preCached) { //pre-cache
            EntityList entityList = efi.find(entityName).condition(conditionMap)
                    .selectField(fromFieldName)
                    .selectField(toFieldName)
                    .useCache(true).list();
            this.addToMappingCache(entityList);
        }
        return null;
    }

    public static void main(String[] args) {

    }
}
