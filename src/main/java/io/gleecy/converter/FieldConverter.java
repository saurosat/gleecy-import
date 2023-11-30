package io.gleecy.converter;

import io.gleecy.converter.value.ColIndex;
import io.gleecy.converter.value.Default;
import io.gleecy.converter.value.ValueConverterFactory;
import io.gleecy.util.StringUtil;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Syntax: [SequenceNumber]###[ConfigType] [Template]###[COLINDEX config]###[DEFAULT config]... <br>
 * ConfigType can be one of: COMMON, SPECIFIC and MAP2STRING <br>
 * In case of MAP2STRING, the next config token must be a String template to <br>
 * format a string using value from input map
 */
public class FieldConverter extends Converter{
    protected final LinkedList<Converter> converters = new LinkedList<>();
    protected ConfigType configType = ConfigType.SPECIFIC;
    protected String fieldName = null;
    protected int sequence = 0;
    protected String map2StringTemplate = null;

    public ConfigType getConfigType() {
        return configType;
    }


    public String getFieldName() {
        return fieldName;
    }

    public int getSequence() {
        return sequence;
    }

    public FieldConverter(String configStr, EntityFacadeImpl efi) {
        super(configStr, efi);
        initError = initialize();
    }
    public FieldConverter(String fieldName, String configStr, EntityFacadeImpl efi) {
        this(configStr, efi);
        this.fieldName = fieldName;
    }
    protected FieldConverter addConverter(Converter converter) {
        if(converter != null) {
            converters.add(converter);
        }
        return this;
    }

    protected Object doConvert(Object value, List<String> errors) {
        if(value == null || "NULL".equals(value)) {
            return null;
        }
        if(this.configType == ConfigType.MAP2STRING) {
            value = StringUtil.format(map2StringTemplate, (Map) value);
        }

        for (Converter converter : converters) {
            value = converter.convert(value, errors);
        }
        return value;
    }

    protected String initialize() {
        String[] configs = CONFIG_DELIM.split(configStr);
        if(configs.length < 1) { //At least one config
            return  "Illegal import configuration: " + configStr;
        }
        boolean haveRequiredConverter = false;
        int i = 0;
        for (; i < configs.length && !haveRequiredConverter; i++) {
            if((configs[i] = configs[i].trim()).isEmpty())
                continue;

            //Sequence number, if any, should be at the first position:
            int sequenceNo = StringUtil.toInt(configs[i]);
            if(sequenceNo >= 0) {
                this.sequence = sequenceNo;
                continue;
            }

            //ConfigType, if any, should be at the first or right after Sequence Number
            ConfigType cfgType = ConfigType.fromString(configs[i]);
            if(cfgType != null) {
                this.configType = cfgType;
                if(this.configType == ConfigType.MAP2STRING) {
                    //Next config token must be a String pattern
//                    String[] configTokens = Converter.ENTRY_DELIM.split(configs[i]);
//                    if(configTokens.length < 2 || (configTokens[1] = configTokens[1].trim()).isEmpty()) {
//                        return "String template is missing";
//                    }

                    map2StringTemplate = configs[i].substring(configType.name().length());
                    //TODO validate the template??
                    haveRequiredConverter = true;
                }
                continue;
            } //else: this.configType = ConfigType.frContent

            //ColIndex and Default, if any, should be the next:
            if(configs[i].startsWith(ColIndex.PREFIX)) {
                Converter converter = new ColIndex(configs[i], efi);
                if (converter.initError != null) {
                    return converter.initError;
                }
                this.addConverter(converter);
                haveRequiredConverter = true;
                continue;
            }
            //Default value converter, if any, should be at the next position
            if(configs[i].startsWith(Default.PREFIX)) {
                Converter converter = new Default(configs[i], efi);
                if (converter.initError != null) {
                    return converter.initError;
                }
                this.addConverter(converter);
                haveRequiredConverter = true;
                continue;
            }
        }
        if(!haveRequiredConverter) {
            return "Illegal import configuration: " + configStr
                    + ". At least one 'COL xx' or 'DEFAULT ' configuration is required.";
        }
        for (; i < configs.length; i++) {
            String config = configs[i].trim();
            if (config.isEmpty()) {
                continue;
            }
            Converter converter = ValueConverterFactory.newInstance(config, efi);
            if (converter == null) {
                return  "Illegal import configuration: " + configStr + ". Error config: " + config;
            }
            this.addConverter(converter);
        }
        return null;
    }

    public enum ConfigType {
        COMMON("Common"), SPECIFIC("Specific"), MAP2STRING("Map to String");
        public final String description;
        private ConfigType(String description) {
            this.description = description;
        }
        public static ConfigType fromString(String value) {
            if(value == null || (value = value.trim()).isEmpty())
                return null;
            value = value.toUpperCase();
            if(value.equals(COMMON.name()))
                return COMMON;
            if(value.equals(SPECIFIC.name()))
                return SPECIFIC;
            if(value.startsWith(MAP2STRING.name()))
                return MAP2STRING;
            return null;
        }
    }
    public static final Pattern CONFIG_DELIM = Pattern.compile("#{3}(?![^\\[\\]]*])"); //"###";
    public final static Logger logger = LoggerFactory.getLogger(FieldConverter.class);


    /*
    public static void main(String[] args) {

        FieldConverter handler = new FieldConverter();
        handler.initialize(
                "###COL AA ###FIELD_MAPPING ##_entity:moqui.basic.Enumeration ##_from:description ##_to:enumId ##_comp:equals ##enumTypeId:ProductType");
        for (Iterator<Converter> itor = handler.converters.iterator();
             itor.hasNext(); ){
            Converter converter = itor.next();
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
