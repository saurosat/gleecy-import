package io.gleecy;

import org.moqui.impl.entity.EntityFacadeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class FieldValueHandler {
    public final static Logger logger = LoggerFactory.getLogger(FieldValueHandler.class);
    public static final Pattern CONFIG_DELIM = Pattern.compile("\\#{3}(?![^\\[\\]]*])"); //"###";

    private LinkedList<ValueConverter> valueConverters = new LinkedList<>();
    public final boolean initialized;
    private FieldValueHandler(){ initialized = false; }
    public FieldValueHandler(String configStr, EntityFacadeImpl efi) {
        initialized = initialize(configStr, efi);
    }
    public boolean initialize(String configStr, EntityFacadeImpl efi) {
        configStr = configStr.trim();
        if(configStr.isEmpty()) {
            return false;
        }
        ValueConverterFactory converterFactory = new ValueConverterFactory();
        String[] configs = CONFIG_DELIM.split(configStr);
        for (int i = 0; i < configs.length; i++) {
            String config = configs[i].trim();
            if(config.isEmpty()) {
                continue;
            }
            ValueConverter converter = converterFactory.newInstance(config, efi);
            if(converter == null) {
                String errMsg = "Illegal import configuration: " + config;
                throw new IllegalArgumentException(errMsg);
            }
            this.addConverter(converter);
        }
        return valueConverters.size() > 0;
    }
    public Object getFieldValue(String[] rowValues) {
        Object value = rowValues;
        ArrayList<String> errors = new ArrayList<>();
        for (Iterator<ValueConverter> itor = valueConverters.iterator();
             itor.hasNext(); ){
            ValueConverter converter = itor.next();
            value = converter.convert(value, errors);
            System.out.println(Arrays.toString(rowValues));
            System.out.println(converter.getClass().getName() + ": " + value);
        }
        return value;
    }
    private FieldValueHandler addConverter(ValueConverter converter) {
        if(converter != null) {
            valueConverters.add(converter);
        }
        return this;
    }

    public static void main(String[] args) {

        FieldValueHandler handler = new FieldValueHandler(
                "###COL AA ###FIELD_MAPPING ##_entity:moqui.basic.Enumeration ##_from:description ##_to:enumId ##_comp:equals ##enumTypeId:ProductType"
                , null);
        for (Iterator<ValueConverter> itor = handler.valueConverters.iterator();
             itor.hasNext(); ){
            ValueConverter converter = itor.next();
            System.out.println(converter.getClass().getName());
            if(converter instanceof ValueConverter.ColIndex) {
                System.out.println("Column index: " + ((ValueConverter.ColIndex) converter).getColIndex());
            }
            if(converter instanceof ValueConverter.FieldMapping) {
                ValueConverter.FieldMapping fieldMapping = ((ValueConverter.FieldMapping) converter);
                System.out.println("Column From: " + fieldMapping.fromFieldName);
                System.out.println("Field To: " + fieldMapping.toFieldName);
                System.out.println("Entity name: " + fieldMapping.entityName);
                for (Map.Entry<String, Object> entry:
                     fieldMapping.conditionMap.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
        }
    }
}
