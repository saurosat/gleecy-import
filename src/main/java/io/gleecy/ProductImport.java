package io.gleecy;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.moqui.context.ExecutionContext;
import org.moqui.context.TransactionFacade;
import org.moqui.entity.EntityException;
import org.moqui.entity.EntityValue;
import org.moqui.impl.entity.EntityDefinition;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.util.ContextStack;

import org.apache.commons.fileupload.FileItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ProductImport {
    static class EntityValueHandler {
        public static Set<String> fieldNames = Set.of("pseudoId", "productTypeEnumId", "productClassEnumId"
            , "assetTypeEnumId", "assetClassEnumId", "statusId", "ownerPartyId", "productName", "description"
            , "salesIntroductionDate", "salesDiscontinuationDate", "salesDiscWhenNotAvail"
            , "supportDiscontinuationDate", "requireInventory", "chargeShipping", "signatureRequiredEnumId"
            , "shippingInsuranceReqd", "inShippingBox", "defaultShipmentBoxTypeId", "taxable" ,"taxCode"
            , "returnable", "amountUomId", "amountFixed", "amountRequire", "originGeoId");
        public final char csvDelimiter = ',';
        public final char csvCommentStart = '#';
        public final char csvQuoteChar = '"';
        public final String templateId;
        private final EntityFacadeImpl efi;
        private EntityValue importTemplate = null;

        private Map<String, FieldValueHandler> handlerMap = new HashMap<>();
        private int fromRow = 0, toRow = Integer.MAX_VALUE;
        private int fromCol = 0, toCol = Integer.MAX_VALUE;
        EntityValueHandler(String templateId, EntityFacadeImpl efi){
            this.templateId = templateId;
            this.efi = efi;
        }
        public boolean isEmpty() {
            return importTemplate == null || handlerMap.isEmpty();
        }
        public EntityValue readRow(String[] rowValues) {
            EntityValue entity = efi.makeValue("mantle.product.Product"); //TODO:
            for(Map.Entry<String, FieldValueHandler> entry : handlerMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue().getFieldValue(rowValues);
                entity.set(key, value);
            }
            return entity;
        }
        public List<EntityValue> parseCsvFile(FileItem csvFile) throws IOException {
            List<EntityValue> eList = new ArrayList<>();
            BufferedReader bufReader = null;
            try {
                InputStream is = null;
                InputStreamReader isReader = null;
                is = csvFile.getInputStream();
                isReader = new InputStreamReader(is, "UTF-8");
                bufReader = new BufferedReader(isReader);

                CSVParser parser = CSVFormat.newFormat(csvDelimiter)
                        .withCommentMarker(csvCommentStart)
                        .withQuote(csvQuoteChar)
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
                    EntityValue entity = readRow(rowValues);
                    eList.add(entity);
                }
            } finally {
                if(bufReader != null)
                    bufReader.close();
            }
            return eList;
        }
        public boolean loadTemplate(List<String> errors) {
            importTemplate = this.efi
                    .find("gleecy.import.ProductTemplate")
                    .condition("templateId", templateId)
                    .one();
            if(importTemplate == null) {
                System.out.println("No template found for templateID=" + templateId);
                return false;
            }
            EntityDefinition productEd = efi.getEntityDefinition("mantle.product.Product");
            for (Map.Entry<String, Object> entry: importTemplate.entrySet()) {
                if(entry.getValue() == null)
                    continue;
                if(entry.getKey().equals("templateId")) {
                    //TODO
                } else if(entry.getKey().equals("templateOwnerPartyId")) {
                    //TODO
                } else if(entry.getKey().equals("templateName")) {
                    //TODO
                } else if(entry.getKey().equals("fromRow")) {
                    Object fromRowObj = entry.getValue();
                    if(fromRowObj != null) {
                        fromRow = ((Long) fromRowObj).intValue();
                    }
                } else if(entry.getKey().equals("toRow")) {
                    Object toRowObj = entry.getValue();
                    if(toRowObj != null) {
                        toRow = ((Long) toRowObj).intValue();
                    }
                } else if(entry.getKey().equals("fromCol")) {
                    Object fromColObj = entry.getValue();
                    if(fromColObj != null) {
                        fromCol = ((Long) fromColObj).intValue();
                    }
                } else if(entry.getKey().equals("toCol")) {
                    Object toColObj = entry.getValue();
                    if(toColObj != null) {
                        toCol = ((Long) toColObj).intValue();
                    }
                } else {
                    String configStr = entry.getValue().toString().trim();
                    if(!fieldNames.contains(entry.getKey())) {
                        continue;
                    }
                    FieldValueHandler fVHandler = new FieldValueHandler(configStr, efi);
                    if(fVHandler.initialized) {
                        handlerMap.put(entry.getKey(), fVHandler);
                        System.out.println("handlerMap added " + entry.getKey() + "->" + fVHandler.getClass().getName());
                    }
                }
            }
            return true;
        }
    }

    public static Map<String, Object> importCsv(ExecutionContext ec) throws IOException {
        Map<String, Object> result = new HashMap<>();
        System.out.println("________________PRODUCT IMPORT____________________");
        System.out.println(ec.getUser().getUserId());
        ContextStack cs = ec.getContext();
        //ec.getUser().getUserAccount().
        for(Map.Entry<String, Object> item: cs.entrySet()) {
            System.out.println(item.getKey() + " : " + item.getValue());
        }

        List<String> errors = new ArrayList<>();
        EntityFacadeImpl efi = (EntityFacadeImpl) ec.getEntity();
        String templateId = (String) cs.get("templateId");
        FileItem csvFile = (FileItem) cs.get("csvFile");

        EntityValueHandler eVHandler = new EntityValueHandler(templateId, efi);
        eVHandler.loadTemplate(errors);
        List<EntityValue> entityList = eVHandler.parseCsvFile(csvFile);

        TransactionFacade tf = efi.ecfi.transactionFacade;
        boolean beganTransaction = tf.begin(600); //TODO
        try {
            for (EntityValue entity : entityList) {
                entity.setSequencedIdPrimary();
                entity.create();
            }
            tf.commit(beganTransaction);
        } catch (EntityException e) {
            tf.rollback(beganTransaction, "Error loading entity data from CSV", e);
            throw e;
        }
        result.put("entityList", entityList);
        return result;
    }
}
