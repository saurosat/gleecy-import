package io.gleecy.parser;

import io.gleecy.converter.FieldValueConverter;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvParser {
    public static final Logger LOGGER = LoggerFactory.getLogger(CsvParser.class);
    protected EntityValue template = null;
    protected EntityValue clonableValue = null;
    protected String entityName = null;
    protected HashMap<String, FieldValueConverter> converterMap = null;
    protected int fromRow = -1, toRow = -1;
    protected int fromCol = -1, toCol = -1;
    public char csvDelimiter() { return ','; }
    public char csvCommentStart() { return '#'; }
    public char csvQuoteChar() { return '"'; }

    public EntityValue parseRow(String[] rowValues, List<String> errors) {
        EntityValue entity = clonableValue.cloneValue();

        for(Map.Entry<String, FieldValueConverter> entry : converterMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue().convert(rowValues, errors);
            entity.set(key, value);
        }
        entity.setSequencedIdPrimary();
        return entity;
    }
    public List<EntityValue> parse(FileItem csvFile, List<String> errors) {
        List<EntityValue> eList = new ArrayList<>();
        InputStream is = null;
        InputStreamReader isReader = null;
        BufferedReader bufReader = null;

        try {
            is = csvFile.getInputStream();
            isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
            bufReader = new BufferedReader(isReader);

            CSVParser parser = CSVFormat.newFormat(csvDelimiter())
                    .withCommentMarker(csvCommentStart())
                    .withQuote(csvQuoteChar())
                    .withSkipHeaderRecord(true)
                    .withIgnoreEmptyLines(true)
                    .withIgnoreSurroundingSpaces(true)
                    .parse(bufReader);
            Iterator<CSVRecord> itor = parser.iterator();
            int rowIdx = 0;
            for (; rowIdx < fromRow && itor.hasNext(); rowIdx++) {
                itor.next();
            }
            for (; rowIdx < toCol && itor.hasNext(); rowIdx++) {
                CSVRecord record = itor.next();
                String[] rowValues = record.toList().toArray(new String[0]);
                EntityValue entity = parseRow(rowValues, errors);
                eList.add(entity);
            }
        } catch (IOException e) {
            String error = "Cannot read file from input stream: " + e.getMessage();
            errors.add(error);
            LOGGER.error(error, e);
        } finally {
            if(bufReader != null) try {
                bufReader.close();
            } catch (IOException e) {
                String error = "Cannot close input stream: " + e.getMessage();
                errors.add(error);
                LOGGER.error(error, e);
            }
        }
        return eList;
    }
    public boolean load(String templateId, EntityFacadeImpl efi, List<String> errors) {
        template = efi.fastFindOne("gleecy.import.ImportTemplate", true, false, templateId);
        if(template == null) {
            String error = "No template found for templateID=" + templateId;
            LOGGER.error(error);
            errors.add(error);
            return false;
        }
        Long rowFr = (Long) template.get("fromRow");
        fromRow = rowFr != null ? rowFr.intValue() : 0;
        Long rowTo = (Long) template.get("toRow");
        toRow = rowTo != null ? rowTo.intValue() : Integer.MAX_VALUE;
        Long colFr = (Long) template.get("fromCol");
        fromCol = colFr != null ? colFr.intValue() : 0;
        Long colTo = (Long) template.get("toCol");
        toCol = colTo != null ? colTo.intValue() : Integer.MAX_VALUE;

        entityName = (String) template.get("entityName");
        if(entityName == null) { //should not happen
            String error ="Template with templateID=" + templateId + " has empty entityName";
            LOGGER.error(error);
            errors.add(error);
            return false;
        }
        clonableValue = efi.makeValue(entityName);

        EntityList entityList = efi.find("gleecy.import.FieldConfig")
                .condition("templateId", templateId)
                .useCache(true).forUpdate(false).list();
        if(entityList == null || entityList.isEmpty()) {
            String error ="Template with templateID=" + templateId + " has no Field Configs";
            LOGGER.error(error);
            errors.add(error);
            return false;
        }
        converterMap = new HashMap<>();
        for (EntityValue entityValue : entityList) {
            String fieldName = entityValue.get("fieldName").toString();
            String configStr = entityValue.get("config").toString();
            FieldValueConverter fVHandler = new FieldValueConverter();
            if(fVHandler.initialize(configStr) && fVHandler.load(efi)) {
                converterMap.put(fieldName, fVHandler);
                LOGGER.debug("handlerMap added " + fieldName + "->" + fVHandler.getClass().getName());
            }

        }
        return true;
    }
}
