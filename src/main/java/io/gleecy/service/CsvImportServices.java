package io.gleecy.service;
import io.gleecy.parser.CsvParser;
import org.moqui.context.ExecutionContext;
import org.moqui.context.MessageFacade;
import org.moqui.context.NotificationMessage;
import org.moqui.context.TransactionFacade;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.util.ContextStack;

import org.apache.commons.fileupload.FileItem;

import java.io.IOException;
import java.util.*;
public class CsvImportServices {
    public static Map<String, Object> importCsv(ExecutionContext ec) {
        ContextStack cs = ec.getContext();
        MessageFacade messages = ec.getMessage();

        Map<String, Object> result = new HashMap<>();
        String templateId = (String) cs.get("templateId");
        if(templateId == null || (templateId = templateId.trim()).isEmpty()) {
            messages.addError("Not any import template selected");
        }
        FileItem csvFile = (FileItem) cs.get("csvFile");
        if(csvFile == null) {
            messages.addError("Not any CSV file uploaded");
        }
        if(messages.hasError()) {
            return result;
        }

        EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();

        CsvParser parser = new CsvParser();
        List<String> errors = new ArrayList<>();
        if(!parser.load(templateId, efi, errors)) {
            messages.addError("Cannot load template ID=" + templateId);
            for(String error: errors)
                messages.addError(error);

            return result;
        }

        List<EntityValue> entityValues = parser.parse(csvFile, errors);
        if(errors.size() > 0) {
            messages.addError("Error in parsing CSV file: " + csvFile.getName());
            for(String error: errors)
                messages.addError(error);

            return result;
        }

        TransactionFacade tf = efi.ecfi.transactionFacade;
        boolean beganTransaction = false;
        try {
            beganTransaction = tf.begin(100);
            efi.createBulk(entityValues);
            tf.commit(beganTransaction);
            messages.addMessage("CSV file" + csvFile.getName() + " imported successfully"
                    , NotificationMessage.NotificationType.success);
            result.put("list", entityValues);
        } catch (Exception e) {
            tf.rollback(beganTransaction, "Error loading entity data from CSV", e);
            messages.addError("Rolling back.  templateId=" + templateId
                    + ", csvFile=" + csvFile.getName()
                    + ", Error message: " + e.getMessage());
        }

        return result;
    }

}
