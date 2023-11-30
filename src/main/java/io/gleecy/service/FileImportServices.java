package io.gleecy.service;

import io.gleecy.converter.EntityConverter;
import io.gleecy.db.DBWorker;
import io.gleecy.parser.BaseParser;
import io.gleecy.parser.CsvParser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.fileupload.FileItem;
import org.moqui.context.*;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.resource.ResourceReference;
import org.moqui.util.ContextStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileImportServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImportServices.class);

    private static boolean saveResource(ExecutionContext ec, EntityValue entity, InputStream is, String fileName) {
        if (is == null || entity == null || !entity.isField("contentLocation")) {
            return false;
        }
        String contentRoot = ec.getUser().getPreference("mantle.content.large.root").trim();
        if(!contentRoot.endsWith("/")) {
            contentRoot = contentRoot + "/";
        }
        String contentLocation = (String) entity.getNoCheckSimple("contentLocation");
        if(contentLocation == null) {
            contentLocation = contentRoot;
        } else {
            contentLocation = contentLocation.trim();
            if (!contentLocation.endsWith("/")) {
                contentLocation = contentLocation + "/";
            }
            if(!contentLocation.startsWith(contentRoot)) {
                String[] paths = contentLocation.split("/");
                StringBuilder sb = new StringBuilder(contentRoot);
                for(int i = 0; i < paths.length; i++) {
                    if(paths[i] == null || paths[i].isBlank()) {
                        continue;
                    }
                    sb.append(paths[i].trim()).append("/");
                }
                contentLocation = sb.toString();
            }
        }

        //Add content fileName. The file name will be as [ID Hash].[Original file name]
        contentLocation = contentLocation + Integer.toHexString(entity.getPrimaryKeysString().hashCode())
                + "." + fileName;
        entity.put("contentLocation", contentLocation);
        ResourceReference rRef = ec.getResource().getLocationReference(contentLocation);
        rRef.putStream(is);
        return true;
    }

    /**
     * Java implementation of FileImportService.xml. Called by Moqui.
     * @param ec passed in by Moqui
     * @return a map from string value "list" to a list of entities
     */
    public static Map<String, Object> importFile(ExecutionContext ec) {
        final ContextStack cs = ec.getContext();
        final MessageFacade messages = ec.getMessage();
        final EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();

        final List<EntityValue> entityList = new ArrayList<>();
        final Map<String, Object> result = new HashMap<>();
        result.put("list", entityList);

        String templateId = (String) cs.get("templateId");
        if(templateId == null || (templateId = templateId.trim()).isEmpty()) {
            messages.addError(new ValidationError("templateId", "Not any import template selected", null));
        }

        List<FileItem> dataFiles = new ArrayList<>();
        Object data = cs.get("dataFiles");
        if(data instanceof FileItem) {
            dataFiles.add((FileItem) data);
        } else if(data instanceof List) {
            dataFiles = (List<FileItem>) data;
        }
        if(dataFiles.isEmpty()) {
            messages.addError(new ValidationError("dataFile", "Not any data file uploaded", null));
        }

        if(messages.hasError()) {
            return result;
        }

        //Below start to query DB:
        EntityConverter converter = new EntityConverter(templateId, efi);
        if(converter.initError != null) {
            messages.addError("Can not initialized converter for " + templateId);
            return result;
        }

        DBWorker dbWorker = new DBWorker(ec, 5, 3);
        for (FileItem dataFile : dataFiles) {
            final String fileName = dataFile.getName();
            InputStream is = null;
            try {
                is = dataFile.getInputStream();
                List<EntityValue> entityValues =
                        processFile(fileName, is, converter, ec, dbWorker);
                if(entityValues != null && !messages.hasError()) {
                    entityList.addAll(entityValues);
                }
            } catch (IOException e) {
                String error = "Error while importing file: " + fileName;
                messages.addError(error);
                LOGGER.error(error, e);
                //break; //Stop processing next files
            } finally {
                if(is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        LOGGER.error("Cannot close input stream of uploaded file: " + dataFile.getName(), e);
                    }
                }
            }
        }
        dbWorker.shutdown();

        String nowStr = ec.getL10n().format(ec.getUser().getNowTimestamp(), "yyyyMMdd_HHmmss");
        HttpServletResponse response = ec.getWeb().getResponse();
        response.setCharacterEncoding("UTF-8");
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(dbWorker.headers.toArray(new String[]{})).build();
        try {
            PrintWriter writer = response.getWriter();
            CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat);
            Collection<List<String>> results = dbWorker.getResults();
            System.out.println("Numbers of CSV ROWs: " + results.size());
            for(List<String> item : results) {
                csvPrinter.printRecord(item);
/*
                for(String fieldStr : item) {
                    // write the field value
                    if (fieldStr.contains(",") || fieldStr.contains("\"") || fieldStr.contains("\n")) {
                        writer.write("\"");
                        writer.write(fieldStr.replace("\"", "\"\""));
                        writer.write("\"");
                    } else {
                        writer.write(fieldStr);
                    }
                }
                writer.println();
*/
            }
            response.setHeader("Cache-Control", "no-cache, must-revalidate, private");
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"DataImport_" + nowStr + ".csv\";");
            return null;
        } catch (IOException e) {
            messages.addError("Cannot render CSV report");
            LOGGER.error("Cannot render CSV report", e);
        }
        return result;
    }

    private static List<EntityValue> processFile(String fileName, InputStream is
            , EntityConverter converter, ExecutionContext ec, DBWorker dbWorker) throws IOException {
        final MessageFacade messages = ec.getMessage();

        final int dotPos = fileName.lastIndexOf(".");
        final String fileType = fileName.substring(dotPos + 1).toLowerCase();
        StringBuilder errors = new StringBuilder();
        BaseParser parser;
        List<EntityValue> entities = null;
        switch (fileType) {
            case "csv":
                parser = new CsvParser(new EntityConverter(converter));
                parser.dbWorker = dbWorker;
                entities = parser.parse(fileName, is, errors);
                if(entities == null || entities.isEmpty()) {
                    String error = errors.length() == 0 ? "Failed to update DB" : errors.toString();
                    dbWorker.addResult(new String[]{fileName, error});
                }
                break;
            case "jpeg":
            case "jpg":
            case "png":
            case "gif":
                parser = new BaseParser(converter);
                entities = parser.parse(fileName, is, errors);
                if(entities == null || entities.isEmpty()) {
                    String error = errors.length() == 0 ? "Failed to update DB" : errors.toString();
                    dbWorker.addResult(new String[]{fileName, error});
                } else {
                    EntityValue entityValue = entities.get(0);
                    if (saveResource(ec, entityValue, is, fileName)) { //save and commit each file separately
                        messages.addMessage("File" + fileName + " imported successfully"
                                , NotificationMessage.NotificationType.success);
                        dbWorker.submit(Arrays.asList(fileName, "Imported"), entityValue);
                    } else {
                        messages.addError("Failed to import file " + fileName);
                        dbWorker.addResult(new String[]{fileName, "Failed to save content file"});
                        entities = null;
                    }
                }
                break;
            case "zip":
                EntityConverter zipConverter = new EntityConverter(converter);
                if(zipConverter.hasCommonConfigs()) {
                    ArrayList<String> errs = new ArrayList<>();
                    zipConverter.convert(fileName.substring(0, fileName.lastIndexOf(".")), errs);
                    if(!errs.isEmpty()) {
                        for(String err : errs) {
                            errors.append("\n").append(err);
                        }
                        return null;
                    }
                }
                ZipInputStream zis = new ZipInputStream(is);
                int entryNo = 0;
                entities = new ArrayList<>();
                for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                    entryNo++;
                    String entryName = entry.getName();
                    System.out.println("EntryNo" + entryNo + ": " + entryName);
                    int pos = entryName.lastIndexOf("/");
                    if(pos >= 0) {
                        continue;
                    }
                   /* while (entryName.startsWith(".")) {
                        entryName = entryName.substring(1);
                    }*/
                    List<EntityValue> zipItemEntities =
                            processFile(entryName, zis, zipConverter, ec, dbWorker);
                    if(zipItemEntities != null && !zipItemEntities.isEmpty())
                        entities.addAll(zipItemEntities);
                    zis.closeEntry();
                    if(messages.hasError()) {
                        break; // break for, Stop processing next files
                    }
                }
                break;
            default:
                break;
        }
        if(errors.length() == 0) {
            messages.addMessage("File" + fileName + " imported successfully"
                    , NotificationMessage.NotificationType.success);
        } else {
            messages.addError("Failed to import file " + fileName );
            messages.addError(errors.toString());
        }

        return entities;
    }
}
