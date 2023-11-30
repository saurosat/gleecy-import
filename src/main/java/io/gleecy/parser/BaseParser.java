package io.gleecy.parser;

import io.gleecy.converter.EntityConverter;
import io.gleecy.db.DBWorker;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BaseParser {
    public static final Logger LOGGER = LoggerFactory.getLogger(BaseParser.class);
    public static final String TOKEN_DELIM = "__";
    protected EntityConverter entityConverter;
    public DBWorker dbWorker = null;

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
    public List<EntityValue> parse(String fileName, InputStream is, StringBuilder errors) {
        String[] fieldValues = fileName.trim().split(TOKEN_DELIM);
        EntityValue entity = parseArray(fileName, fieldValues, errors);
        return Collections.singletonList(entity);
    }

    protected EntityValue parseArray(String fileName, String[] fieldValues, StringBuilder errors) {
        ArrayList<String> errs = new ArrayList<>();
        EntityValue entity = (EntityValue) entityConverter.convert(fieldValues, errs);
        if(dbWorker != null) {
            ArrayList<String> rowVals = new ArrayList<>() {
                {add(fileName); add(null);addAll(List.of(fieldValues));}
            };
            if(errs.isEmpty()) {
                dbWorker.submit(rowVals, entity);
            } else {
                rowVals.set(1, errs.get(0));
                dbWorker.addResult(rowVals);
            }
        } else if(!errs.isEmpty()) {
            for(String err : errs) {
                errors.append("\n").append(err);
            }
            return null;
        }
        return entity;
    }
    protected void ignoreArray(String fileName, String[] fieldValues) {
        if(dbWorker != null) {
            ArrayList<String> rowVals = new ArrayList<>() {
                {add(null); add(null);addAll(List.of(fieldValues));}
            };
            dbWorker.setHeader(rowVals);
        }
    }
}
