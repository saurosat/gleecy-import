package io.gleecy.parser;

import io.gleecy.converter.EntityConverter;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class BaseParser {
    public static final Logger LOGGER = LoggerFactory.getLogger(BaseParser.class);
    public static final String TOKEN_DELIM = "__";
    protected EntityConverter entityConverter;

    private BaseParser() {
        entityConverter = null;
    }
    public BaseParser(EntityConverter entityConverter) {
        this.entityConverter = entityConverter;
    }

    /**
     * parse file content and return a list of entities
     * parse fileName to get common values to all entities in the file content
     * @param fileName
     * @param is
     * @param errors
     * @return
     */
    public final List<EntityValue> parse(String fileName, InputStream is, List<String> errors) {
        if(entityConverter.hasCommonConfigs()) {
            String fileNameConfig = fileName.substring(0, fileName.lastIndexOf('.'));
            entityConverter.convert(fileNameConfig, errors);
        }
        return parseItems(fileName, is, errors);
    }

    /**
     * Parse file to create entity without updating common values
     * For use with file that has only one entity
     * Entities' information is parsed from file name.
     * Override this method to support other file type
     * @param fileName
     * @param is
     * @param errors
     * @return
     */
    public EntityValue parseItem(String fileName, InputStream is, List<String> errors) {
        fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        return parseString(fileName, errors);
    }

    /**
     * Parse file to create entities without updating common values
     * Entities' information is parsed from file name.
     * Override this method to support other file type
     * @param fileName
     * @param is
     * @param errors
     * @return
     */
    protected List<EntityValue> parseItems(String fileName, InputStream is, List<String> errors) {
        List<EntityValue> entities = new ArrayList<>();
        entities.add(parseItem(fileName, is, errors));
        return entities;
    }

    protected EntityValue parseArray(String[] fieldValues, List<String> errors) {
        return (EntityValue) entityConverter.convert(fieldValues, errors);
    }
    protected EntityValue parseString(String fieldValuesStr, List<String> errors) {
        String[] fieldValues = fieldValuesStr.trim().split(TOKEN_DELIM);
        return parseArray(fieldValues, errors);
    }
}
