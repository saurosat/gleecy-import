package io.gleecy.service;

import io.gleecy.converter.EntityConverter;
import io.gleecy.parser.BaseParser;
import io.gleecy.parser.CsvParser;
import org.apache.commons.fileupload.FileItem;
import org.moqui.context.*;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.resource.ResourceReference;
import org.moqui.util.ContextStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileImportServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileImportServices.class);

    private static boolean saveEntity(ExecutionContext ec, EntityValue entity, InputStream is
            , String ownerPartyId) {
        EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();
        UserFacade uf = ec.getUser();
        ResourceFacade rf = ec.getResource();
        if(entity.isField("ownerPartyId") && entity.get("ownerPartyId") == null) {
            if(entity.containsKey("ownerPartyId"))
                entity.set("ownerPartyId", ownerPartyId);
        }
        if(entity.isField("contentDate")) {
            entity.set("contentDate", uf.getNowTimestamp());
        }
        if(entity.isField("userId")) {
            entity.set("userId", uf.getUserId());
        }

        String contentRoot = uf.getPreference("mantle.content.large.root").trim();
        String contentLocation = entity.getString("contentLocation");
        if(contentLocation != null && !contentLocation.startsWith(contentRoot)) {
            contentLocation = contentRoot + contentLocation.trim();
            entity.set("contentLocation", contentLocation);
        }
        TransactionFacade tf = efi.ecfi.transactionFacade;
        boolean beganTransaction = false;
        try {
            beganTransaction = tf.begin(100);
            entity.create();
            if (contentLocation != null) {
                ResourceReference rRef = rf.getLocationReference(contentLocation);
                rRef.putStream(is);
            }
            tf.commit(beganTransaction);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save entities.", e);
            tf.rollback(beganTransaction, "Error loading entity data from file", e);
            return false;
        }
    }
    private static boolean saveEntities(ExecutionContext ec, List<EntityValue> entityValues, InputStream is
            , String ownerPartyId) {
        EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();
        UserFacade uf = ec.getUser();
        ResourceFacade rf = ec.getResource();
        for (EntityValue entity : entityValues) {
            if(entity.isField("ownerPartyId") && entity.get("ownerPartyId") == null) {
                if(entity.containsKey("ownerPartyId"))
                    entity.set("ownerPartyId", ownerPartyId);
            }
            if(entity.isField("contentDate")) {
                entity.set("contentDate", uf.getNowTimestamp());
            }
            if(entity.isField("userId")) {
                entity.set("userId", uf.getUserId());
            }

            String contentRoot = uf.getPreference("mantle.content.large.root").trim();
            if(entity.isField("contentLocation")) {
                String contentLocation = entity.getString("contentLocation");
                if (contentLocation != null && !contentLocation.startsWith(contentRoot)) {
                    contentLocation = contentRoot + contentLocation.trim();
                    entity.set("contentLocation", contentLocation);
                }
            }
        }

        TransactionFacade tf = efi.ecfi.transactionFacade;
        boolean beganTransaction = false;
        try {
            beganTransaction = tf.begin(100);
            efi.createBulk(entityValues);
            for (EntityValue entity : entityValues) {
                if(!entity.isField("contentLocation")) {
                    break; //All entities are same type
                }
                String contentLocation = entity.getString("contentLocation");
                if (contentLocation != null) {
                    ResourceReference rRef = rf.getLocationReference(contentLocation);
                    rRef.putStream(is);
                }
            }
            tf.commit(beganTransaction);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save entities.", e);
            tf.rollback(beganTransaction, "Error loading entity data from file", e);
            return false;
        }
    }
    public static Map<String, Object> importFile(ExecutionContext ec) {
        //TODO: not tested yet
        final ContextStack cs = ec.getContext();
        final MessageFacade messages = ec.getMessage();
        final UserFacade uf = ec.getUser();
        final ResourceFacade rf = ec.getResource();
        final EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();

        final EntityValue userAcc = uf.getUserAccount();

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
        if(dataFiles == null || dataFiles.isEmpty()) {
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

        final EntityValue party = userAcc.findRelatedOne("Party", true, false);
        final String ownerPartyId = (party != null) ? party.getString("partyId") : null;

        for (FileItem dataFile : dataFiles) {
            final String fileName = dataFile.getName();
            InputStream is = null;
            try {
                is = dataFile.getInputStream();
                List<EntityValue> entityValues =
                        processFile(fileName, ownerPartyId, is, converter, ec);
                if(!messages.hasError()) {
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
        return result;
    }

    private static List<EntityValue> processFile(String fileName, String ownerPartyId
            , InputStream is
            , EntityConverter converter, ExecutionContext ec) throws IOException {
        final MessageFacade messages = ec.getMessage();
        final UserFacade uf = ec.getUser();

        final int dotPos = fileName.lastIndexOf(".");
        final String fileType = fileName.substring(dotPos + 1).toLowerCase();
        List<String> errors = new ArrayList<>();
        BaseParser parser;
        List<EntityValue> entityValues = new ArrayList<>();
        switch (fileType) {
            case "csv":
                parser = new CsvParser(new EntityConverter(converter));
                List<EntityValue> entities = parser.parse(fileName, is, errors);
                if(errors.isEmpty()) {
                    if(saveEntities(ec, entities, is, ownerPartyId)) { //save and commit each file separately
                        messages.addMessage("File" + fileName + " imported successfully"
                                , NotificationMessage.NotificationType.success);
                        entityValues.addAll(entities);
                    } else {
                        messages.addError("Failed to import file " + fileName);
                    }
                }
                break;
            case "jpeg":
            case "jpg":
            case "png":
            case "gif":
                parser = new BaseParser(converter);
                EntityValue entityValue = parser.parseItem(fileName, is, errors);
                if(errors.isEmpty()) {
                    if(saveEntity(ec, entityValue, is, ownerPartyId)) { //save and commit each file separately
                        messages.addMessage("File" + fileName + " imported successfully"
                                , NotificationMessage.NotificationType.success);
                        entityValues.add(entityValue);
                    } else {
                        messages.addError("Failed to import file " + fileName);
                    }
                }
                break;
            case "zip":
                EntityConverter zipConverter = new EntityConverter(converter);
                if(zipConverter.hasCommonConfigs())
                    zipConverter.convert(fileName.substring(0, fileName.lastIndexOf(".")), errors);
                ZipInputStream zis = new ZipInputStream(is);
                int entryNo = 0;
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
                            processFile(entryName, ownerPartyId, zis, zipConverter, ec);
                    if(zipItemEntities != null)
                        entityValues.addAll(zipItemEntities);
                    zis.closeEntry();
                    if(messages.hasError()) {
                        break; // break for, Stop processing next files
                    }
                }
                break;
            default:
                break;
        }
        for (String error : errors) {
            messages.addError(error);
        }

        return entityValues;
    }
}
