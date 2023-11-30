package io.gleecy.parser;

import io.gleecy.converter.EntityConverter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CsvParser extends BaseParser{
    public static final Logger LOGGER = LoggerFactory.getLogger(CsvParser.class);

    public CsvParser(EntityConverter entityConverter) {
        super(entityConverter);

    }

    public char csvDelimiter() { return ','; }
    public char csvCommentStart() { return '#'; }
    public char csvQuoteChar() { return '"'; }

    public List<EntityValue> parse(String fileName, InputStream is, StringBuilder errors) {
        if(entityConverter.hasCommonConfigs()) {
            String fileNameConfig = fileName.substring(0, fileName.lastIndexOf('.'));
            ArrayList<String> errs = new ArrayList<>();
            entityConverter.convert(fileNameConfig, errs);
            if(!errs.isEmpty()) {
                for(String err : errs) {
                    errors.append("\n").append(err);
                }
                return null;
            }
        }

        List<EntityValue> eList = new ArrayList<>();
        InputStreamReader isReader = null;
        BufferedReader bufReader = null;

        try {
            isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
            bufReader = new BufferedReader(isReader);
            /*CSVPrinter printer = new CSVPrinter(null, null);
            printer.printRecord( new String[]{});*/
            CSVParser parser = CSVFormat.newFormat(csvDelimiter())
                    .withCommentMarker(csvCommentStart())
                    .withQuote(csvQuoteChar())
                    .withSkipHeaderRecord(true)
                    .withIgnoreEmptyLines(true)
                    .withIgnoreSurroundingSpaces(true)
                    .parse(bufReader);
            Iterator<CSVRecord> itor = parser.iterator();
            int rowIdx = 0;
            for (; rowIdx < entityConverter.getFromRow() && itor.hasNext(); rowIdx++) {
                CSVRecord record = itor.next();
                String[] rowValues = record.toList().toArray(new String[0]);
                ignoreArray(fileName, rowValues);
            }
            for (; rowIdx < entityConverter.getToRow() && itor.hasNext(); rowIdx++) {
                CSVRecord record = itor.next();
                String[] rowValues = record.toList().toArray(new String[0]);
                EntityValue entity = parseArray(fileName, rowValues, errors);
                eList.add(entity);
            }
        } catch (IOException e) {
            String error = "Cannot read file from input stream: " + e.getMessage();
            errors.append(error);
            LOGGER.error(error, e);
        } /*finally {
            if(bufReader != null) try {
                bufReader.close();
            } catch (IOException e) {
                String error = "Cannot close input stream: " + e.getMessage();
                errors.add(error);
                LOGGER.error(error, e);
            }
        }*/
        return eList;
    }
}
