package io.gleecy.service;

import io.gleecy.converter.FieldConverter;
import org.moqui.context.ExecutionContext;
import org.moqui.context.MessageFacade;
import org.moqui.context.NotificationMessage;
import org.moqui.context.TransactionFacade;
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
        ArrayList<String> fieldNames = ed.getAllFieldNames();
        fieldNames.remove("lastUpdatedStamp");
        if(!fieldNames.contains(fieldName)) {
            messages.addError("Field '" + fieldName + "' is not a valid field of entity '" + entityName + "'");
            return result;
        }
        FieldConverter fvConverter = new FieldConverter(config, efi);
        if(fvConverter.initError != null) {
            messages.addError("Cannot parse and load Field Import Configuration: " + fvConverter.initError);
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
            tf.rollback(beganTransaction, "Error saving configuration data", e);
            messages.addError("Cannot save FieldConfig, templateName="
                    + template.get("templateName")
                    + ", entity=" + entityName
                    + ", fieldName=" + fieldName
                    + ", Field import configuration = " + config
                    + ", Error message: " + e.getMessage());
        }

        return result;
    }
}
