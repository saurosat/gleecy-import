package io.gleecy.converter;

import io.gleecy.converter.value.ColIndex;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.moqui.entity.EntityFind;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityDefinition;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;

public class EntityConverter extends Converter{
    public static final Logger LOGGER = LoggerFactory.getLogger(EntityConverter.class);
    public static final String COMMON_TOKEN_DELIM = "__";
    //public static
    protected EntityValue template;
    protected EntityValue commonValues;
    protected EntityDefinition ed;
    protected String entityName;
    protected long fromRow = 0, toRow = Long.MAX_VALUE;
    protected long fromCol = 0, toCol = Integer.MAX_VALUE;
    protected final Timestamp now = new Timestamp(System.currentTimeMillis());

    public long getFromRow() {
        return fromRow;
    }

    public long getToRow() {
        return toRow;
    }

    public long getFromCol() {
        return fromCol;
    }

    public long getToCol() {
        return toCol;
    }
    public String getEntityName() {
        return entityName;
    }

    protected SortedSet<FieldConverter> commonConverters;
    protected SortedSet<FieldConverter> specificConverters;
    protected SortedSet<FieldConverter> map2StringConverters;

    public EntityConverter(EntityConverter tobeCloned) {
        super(tobeCloned);
        //template = tobeCloned.template.cloneValue(); //not needed to clone:
        ed = tobeCloned.ed;
        template = tobeCloned.template;
        entityName = tobeCloned.entityName;
        fromRow = tobeCloned.fromRow;
        toRow = tobeCloned.toRow;
        toCol = tobeCloned.toCol;
        fromCol = tobeCloned.fromCol;
        toCol = tobeCloned.toCol;
        //commonConverters.putAll(tobeCloned.commonConverters);//not needed to clone:
        commonConverters = tobeCloned.commonConverters;
        //specificConverters.putAll(tobeCloned.specificConverters);//not needed to clone:
        specificConverters = tobeCloned.specificConverters;
        map2StringConverters = tobeCloned.map2StringConverters;

        commonValues = tobeCloned.commonValues.cloneValue();
    }

    public EntityConverter(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        initError = initialize();
    }

    public boolean hasCommonConfigs() {
        return !commonConverters.isEmpty();
    }
    public boolean isField(String fieldName) { return commonValues.isField(fieldName); }
    public boolean hasSpecificConfigs() { return !specificConverters.isEmpty(); }

    @Override
    protected Object doConvert(Object value, List<String> errors) {
        if (value instanceof String) {
            String[] commonValues = ((String) value).trim().split(COMMON_TOKEN_DELIM);
            for(FieldConverter fieldConverter : commonConverters) {
                Object val = fieldConverter.convert(commonValues, errors);
                if(val != null)
                    this.commonValues.set(fieldConverter.fieldName, val);
            }
            return null;
        }
        if (value.getClass().isArray()) {
            String[] vals = (String[]) value;
            Map<String, Object> paramMap = new HashMap<>();
            String colPrefix = ColIndex.PREFIX.trim();
            for(int i = 0; i < vals.length; i++) {
                paramMap.put(colPrefix + "_" + i, vals[i]);
            }

            Map<String, Object> fValMap = new HashMap<>();
            for(FieldConverter fieldConverter : specificConverters) {
                Object val = fieldConverter.convert(vals, errors);
                if(val != null) {
                    fValMap.put(fieldConverter.fieldName, val);
                    paramMap.put(fieldConverter.fieldName, val);
                }
            }
            for(FieldConverter fieldConverter : map2StringConverters) {
                Object val = fieldConverter.convert(paramMap, errors);
                if(val != null) {
                    fValMap.put(fieldConverter.fieldName, val);
                    paramMap.put(fieldConverter.fieldName, val);
                }
            }

            String tenantId = this.efi.ecfi.getEci().getUser().getTenantId();
            String ownerPartyId = null;
            if(ed.isField("ownerPartyId")) {
                ownerPartyId = (String) fValMap.get("ownerPartyId");
                if(ownerPartyId != null) {//check if ownerPartyId exists
                    if (this.efi.fastFindOne("mantle.party.Party",
                            true, true, ownerPartyId) == null) {
                        errors.add("Refer to an unknown partyId in field ownerPartyId = " + ownerPartyId);
                        return null;
                    }
                }
                if(ownerPartyId == null) {
                    ownerPartyId = tenantId;
                }
                if(ownerPartyId == null) {
                    ownerPartyId = "_NA_";
                }
                fValMap.put("ownerPartyId", ownerPartyId);
            }

            EntityValue entity = commonValues.cloneValue();
            entity.set("lastUpdatedStamp", now);
            entity.setAll(fValMap);
            if(!entity.containsPrimaryKey() && ed.isField("pseudoId")) {
                String pseudoId = (String) fValMap.get("pseudoId");
                if(!StringUtils.isBlank(pseudoId)) {
                    EntityValue existing = this.efi.find(this.entityName)
                            .useCache(true).condition("pseudoId", pseudoId.trim()).one();
                    if(existing != null) {
                        //check if any field changed:
                        Map<String, Object> newValMap = entity.getEtlValues();
                        Map<String, Object> oldValMap = existing.getEtlValues();
                        int numFieldChanges = setFieldValues(this.commonConverters, newValMap, oldValMap, null);
                        numFieldChanges += setFieldValues(this.specificConverters, newValMap, oldValMap, null);
                        numFieldChanges += setFieldValues(this.map2StringConverters, newValMap, oldValMap, null);
                        if(numFieldChanges == 0) {
                            errors.add("Ignored. Entity ID '" + pseudoId + "' already exists in DB with exact values");
                            return null;
                        }

                        entity.setAll(existing.getPrimaryKeys());
                    }
                }
            }
            return entity;
        }
        //else:
        errors.add( "EntityConverter: Parameter must be a String or array, but it is "
                + value.getClass().getName());
        return null;
    }
    protected int setFieldValues(Collection<FieldConverter> fConverters, Map<String, Object> newValMap,
                                 Map<String, Object> oldValMap, Map<String, Object> diff) {
        int numFieldChanges = 0;
        for(FieldConverter fieldConverter : fConverters) {
            String fName = fieldConverter.fieldName;
            Object newVal = newValMap.get(fName);
            Object dbVal = oldValMap.get(fName);
            if(newVal != null && (dbVal == null || !dbVal.equals(newVal))) {
                if(diff != null)
                    diff.put(fName, newVal);
                numFieldChanges++;
            }
        }
        return numFieldChanges;
    }

    protected String initialize() {
        String templateId = configStr.trim();
        template = efi.fastFindOne("gleecy.import.ImportTemplate",
                true, false, templateId);
        if(template == null) {
            String error = "No template found for templateID=" + templateId;
            LOGGER.error(error);
            return error;
        }
        Long rowFr = (Long) template.get("fromRow");
        if(rowFr != null) fromRow = rowFr.intValue();
        Long rowTo = (Long) template.get("toRow");
        if(rowTo != null) toRow = rowTo.intValue();
        Long colFr = (Long) template.get("fromCol");
        if(colFr != null) fromCol =  colFr.intValue();
        Long colTo = (Long) template.get("toCol");
        if(colTo != null) toCol = colTo.intValue();

        entityName = (String) template.get("entityName");
        if(entityName == null) { //should not happen
            String error ="Template with templateID=" + templateId + " has empty entityName";
            LOGGER.error(error);
            return error;
        }
        ed = efi.getEntityDefinition(entityName);
        commonValues = ed.makeEntityValue();

        EntityList entityList = efi.find("gleecy.import.FieldConfig")
                .condition("templateId", templateId)
                .useCache(true).forUpdate(false).list();
        if(entityList == null || entityList.isEmpty()) {
            String error ="Template with templateID=" + templateId + " has no Field Configs";
            LOGGER.error(error);
            return error;
        }
        Comparator<FieldConverter> fCComparator = new Comparator<FieldConverter>() {
            @Override
            public int compare(FieldConverter o1, FieldConverter o2) {
                return o1.sequence - o2.sequence;
            }
        };
        commonConverters = new TreeSet<>(fCComparator);
        specificConverters = new TreeSet<>(fCComparator);
        map2StringConverters = new TreeSet<>(fCComparator);
        int sequenceNo = 0;
        for (EntityValue entityValue : entityList) {
            String fieldName = (String) entityValue.get("fieldName");
            String configStr = (String) entityValue.get("config");
            FieldConverter fVConverter = new FieldConverter(fieldName, configStr, efi);
            if(fVConverter.initError != null) {
                return fVConverter.initError;
            }
            if(fVConverter.sequence == 0 || fVConverter.sequence < sequenceNo) { //not set, or  set but duplicated
                fVConverter.sequence = sequenceNo;
                sequenceNo++;
            }
            switch (fVConverter.configType) {
                case COMMON:
                    commonConverters.add(fVConverter);
                    break;
                case SPECIFIC:
                    specificConverters.add(fVConverter);
                    break;
                case MAP2STRING:
                    map2StringConverters.add(fVConverter);
                    break;
                default:
                    break;
            }
            LOGGER.debug("Converter initialized for " + fieldName + ". Converter class: " + fVConverter.getClass().getName());
        }
        return null;
    }
}
