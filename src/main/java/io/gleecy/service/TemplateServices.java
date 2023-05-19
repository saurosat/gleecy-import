package io.gleecy.service;

import io.gleecy.converter.FieldValueConverter;
import org.moqui.context.*;
import org.moqui.entity.EntityException;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityDefinition;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.util.ContextStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TemplateServices {
    public static Map<String, Object> storeFieldConfig(ExecutionContext ec) {
        ContextStack cs = ec.getContext();
        String templateId = (String) cs.get("templateId");
        String fieldName = (String) cs.get("fieldName");
        String config = (String) cs.get("config");

        MessageFacade messages = ec.getMessage();
        if(templateId == null || (templateId = templateId.trim()).isEmpty()) {
            messages.addError("Unknown TemplateID");
        }
        if(fieldName == null || (fieldName = fieldName.trim()).isEmpty()) {
            messages.addError("Unknown Field Name");
        }
        if(config == null || (config = config.trim()).isEmpty()) {
            messages.addError("Field Import configuration is not set");
        }

        Map<String, Object> result = new HashMap<>();
        if(messages.hasError()) {
            return result;
        }

        EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();
        EntityValue template = efi.fastFindOne("gleecy.import.ImportTemplate", true, true, templateId);
        if(template == null) {
            messages.addError("Cannot find Import Template with ID " + templateId);
            return result;
        }
        String entityName = template.getString("entityName");
        EntityDefinition ed = efi.getEntityDefinition(entityName);
        if(ed == null) {
            messages.addError("Cannot find defined entity with name: " + entityName);
            return result;
        }
        ArrayList<String> fieldNames = ed.getNonPkFieldNames();
        fieldNames.remove("lastUpdatedStamp");
        if(!fieldNames.contains(fieldName)) {
            messages.addError("Field '" + fieldName + "' is not a valid field of entity '" + entityName + "'");
            return result;
        }
        FieldValueConverter fvConverter = new FieldValueConverter();
        boolean isConfigValid = false;
        try {
            isConfigValid = fvConverter.initialize(config);
            isConfigValid = isConfigValid && fvConverter.load(efi);
        } catch (IllegalArgumentException e) {
            messages.addError(e.getMessage());
            return result;
        }
        if(!isConfigValid) {
            messages.addError("Cannot parse and load Field Import Configuration");
            return result;
        }

        EntityValue fConfig = efi.find("gleecy.import.FieldConfig")
                                .condition("templateId", templateId)
                                .condition("fieldName", fieldName)
                                .one();
        TransactionFacade tf = efi.ecfi.transactionFacade;
        boolean beganTransaction = false;
        try {
            beganTransaction = tf.begin(100);
            if(fConfig != null) {
                fConfig.set("config", config);
                fConfig.update();
            } else {
                fConfig = efi.makeValue("gleecy.import.FieldConfig");
                fConfig.set("templateId", templateId);
                fConfig.set("fieldName", fieldName);
                fConfig.set("config", config);
                fConfig.create();
            }
            tf.commit(beganTransaction);
            messages.addMessage("Field Import Configuration is saved successfully"
                    , NotificationMessage.NotificationType.success);
            result.put("entity", fConfig);
        } catch (Exception e) {
            tf.rollback(beganTransaction, "Error loading entity data from CSV", e);
            messages.addError("Cannot save FieldConfig, templateName="
                    + template.get("templateName")
                    + ", entity=" + entityName
                    + ", fieldName=" + fieldName
                    + ", Field import configuration = " + config
                    + ", Error message: " + e.getMessage());
        }

        return result;
    }
    //public static boolean execInTransaction(TransactionFacade tf, Func)
    public static boolean beginTransaction(TransactionFacade tf, MessageFacade messages, int timeout) {
        try {
            return tf.begin(timeout);
        } catch (TransactionException e) {
            messages.addError("Cannot start DB transaction: " + e.getMessage());
            return false;
        }
    }
    public static boolean commitTransaction(TransactionFacade tf, MessageFacade messages, boolean transactionBegan) {
        try {
            tf.commit(transactionBegan);
            return true;
        } catch (TransactionException e) {
            messages.addError("Cannot commit DB transaction. Rolling back. Error message: " + e.getMessage());
            rollbackTransaction(tf, messages, transactionBegan, e);
            return false;
        }
    }
    public static boolean rollbackTransaction(TransactionFacade tf, MessageFacade messages
            , boolean transactionBegan, Exception cause) {
        try {
            tf.rollback(transactionBegan, cause.getMessage(), cause);
            return true;
        } catch (TransactionException e) {
            messages.addError("Cannot rollback DB transaction: " + e.getMessage());
            return false;
        }
    }
}
