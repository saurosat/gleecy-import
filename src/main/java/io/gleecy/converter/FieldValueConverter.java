package io.gleecy.converter;

import io.gleecy.converter.basic.BasicConverter;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class FieldValueConverter implements ValueConverter {
    public static final Pattern CONFIG_DELIM = Pattern.compile("\\#{3}(?![^\\[\\]]*])"); //"###";
    public final static Logger logger = LoggerFactory.getLogger(FieldValueConverter.class);

    protected LinkedList<ValueConverter> valueConverters = new LinkedList<>();
    public FieldValueConverter(){ }
    protected FieldValueConverter addConverter(ValueConverter converter) {
        if(converter != null) {
            valueConverters.add(converter);
        }
        return this;
    }

    @Override
    public Object convert(Object value, List<String> errors) {
        for (Iterator<ValueConverter> itor = valueConverters.iterator();
             itor.hasNext(); ){
            ValueConverter converter = itor.next();
            value = converter.convert(value, errors);
        }
        return value;
    }

    @Override
    public boolean initialize(String configStr) {
        configStr = configStr.trim();
        if(configStr.isEmpty()) {
            return false;
        }
        String[] configs = CONFIG_DELIM.split(configStr);
        for (int i = 0; i < configs.length; i++) {
            String config = configs[i].trim();
            if(config.isEmpty()) {
                continue;
            }
            ValueConverter converter = BasicConverter.newInstance(config);
            if(converter == null) {
                String errMsg = "Illegal import configuration: " + config;
                throw new IllegalArgumentException(errMsg);
            }
            this.addConverter(converter);
        }
        return valueConverters.size() > 0;
    }

    @Override
    public boolean load(EntityFacadeImpl efi) {
        boolean result = true;
        for(ValueConverter converter : valueConverters) {
            result = result && converter.load(efi);
        }
        return result;
    }

    /*
    public static void main(String[] args) {

        FieldValueConverter handler = new FieldValueConverter();
        handler.initialize(
                "###COL AA ###FIELD_MAPPING ##_entity:moqui.basic.Enumeration ##_from:description ##_to:enumId ##_comp:equals ##enumTypeId:ProductType");
        for (Iterator<ValueConverter> itor = handler.valueConverters.iterator();
             itor.hasNext(); ){
            ValueConverter converter = itor.next();
            System.out.println(converter.getClass().getName());
            if(converter instanceof ColIndex) {
                System.out.println("Column index: " + ((ColIndex) converter).getColIndex());
            }
            if(converter instanceof FieldMapping) {
                FieldMapping fieldMapping = ((FieldMapping) converter);
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

     */

}
