package io.gleecy.service;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.moqui.Moqui;
import org.moqui.context.ExecutionContext;
import org.moqui.context.UserFacade;
import org.moqui.entity.EntityFacade;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.context.ExecutionContextFactoryImpl;
import org.moqui.impl.context.ExecutionContextImpl;
import org.moqui.impl.entity.EntityDefinition;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.util.ContextStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;

public class ProductEntityServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductEntityServices.class);
    private static ThreadLocal<ExecutionContext> _ec = new ThreadLocal<>();
    private static ExecutionContext getEc() {
        return _ec.get();
    }
    public static List<EntityValue> createProductFeatures(String featureTypeEnumId, String[] abbrevs,
                                                          Map<String, Object> valueMap) {
        return createProductFeatures(getEc(), featureTypeEnumId, abbrevs,
                valueMap);
    }
    public static List<EntityValue> createProductFeatures(ExecutionContext ec, String featureTypeEnumId, String[] abbrevs,
                                                            Map<String, Object> valueMap) {

        EntityFacadeImpl efi = ((ExecutionContextImpl) ec).getEntityFacade();
        UserFacade uf = ec.getUser();
        String tenantId = uf.getTenantId();

        EntityDefinition featureEd = efi.getEntityDefinition("mantle.product.feature.ProductFeature");
        List<EntityValue> evs = new ArrayList<>(abbrevs.length);
        for(String abbrev : abbrevs) {
            EntityValue featureEv = efi.find("mantle.product.feature.ProductFeature")
                    .condition("abbrev", abbrev)
                    .condition("productFeatureTypeEnumId", featureTypeEnumId).one();
            if(featureEv == null) {
                featureEv = featureEd.makeEntityValue();
                if(valueMap != null) {
                    featureEv.setAll(valueMap);
                }
                featureEv.setString("productFeatureTypeEnumId", featureTypeEnumId);
                featureEv.setString("abbrev", abbrev);
                featureEv.set("ownerPartyId", tenantId);
                featureEv.setSequencedIdPrimary();
                featureEv.create();
            }
            evs.add(featureEv);
        }
        return evs;
    }

    public static EntityValue createProductFeatureAppl(EntityValue product, EntityValue feature, String applType,
                                                       Timestamp fromDate, Map<String, Object> valueMap) {
        return createProductFeatureAppl(product, feature, applType, fromDate, valueMap,
                getEc().getEntity());
    }

    public static EntityValue createProductFeatureAppl(EntityValue product, EntityValue feature, String applType,
                                                       Timestamp fromDate, Map<String, Object> valueMap, EntityFacade ef) {
        String productId = (String) product.getNoCheckSimple("productId");
        String featureId = (String) feature.getNoCheckSimple("productFeatureId");
        LOGGER.debug("Creating FeatureAppl for product " + productId + ", feature " + featureId + ", fromdate " + fromDate.toString());
        EntityValue appl = ef.find("mantle.product.feature.ProductFeatureAppl")
                .condition("productId", productId)
                .condition("productFeatureId", featureId)
                .conditionDate("fromDate", "thruDate", fromDate)
                .one();
        if(appl == null) {
            appl = ef.makeValue("mantle.product.feature.ProductFeatureAppl");
            if(valueMap != null) {
                appl.setAll(valueMap);
            }
            appl.setString("applTypeEnumId", applType);
            appl.setString("productId", productId);
            appl.setString("productFeatureId", featureId);
            appl.set("fromDate", fromDate);
            appl.create();
        }
        return appl;
    }
    public static void generateSelectableFeaturesAndVariants(ExecutionContext ec) {
        _ec.set(ec);
//        UserFacade uf = ec.getUser();
//        String tenantId = uf.getTenantId();

        ContextStack cs = ec.getContext();
        EntityValue product = (EntityValue) cs.get("entityValue");
        EntityValue oriProduct = (EntityValue) cs.get("originalValue");

        Timestamp today = new Timestamp(System.currentTimeMillis());

        Map<String, Object> productValueMap = new HashMap<>(product.getEtlValues());
        String productType = (String) productValueMap.put("productTypeEnumId", "PtAsset");
        productValueMap.put("lastUpdatedStamp", today);
        String productId = (String) productValueMap.remove("productId");
        String pseudoId = (String) productValueMap.remove("pseudoId");
        if(pseudoId == null) pseudoId = productId;

        if(!productType.equals("PtVirtual")) {
            LOGGER.info("Cannot generate selectable features for a non-virtual product: "
                    + pseudoId + ", productTypeEnumId = " + productType);
            return;
        }

        EntityFacade ef = ec.getEntity();
        EntityList featureTypeEnums = ef.find("moqui.basic.Enumeration")
                .condition("enumTypeId", "ProductFeatureType")
                .useCache(true).disableAuthz().list();

        Map<String, List<EntityValue>> featureMap = new HashMap<>(featureTypeEnums.size());
        for(EntityValue featureTypeEnum : featureTypeEnums) {
            String featureType = (String) featureTypeEnum.getNoCheckSimple("enumId");
            String infoFieldName = "e" + featureType;
            String featureAbbrevStr = (String) productValueMap.remove(infoFieldName);
            if(StringUtils.isBlank(featureAbbrevStr)) {
                LOGGER.debug("Feature type" + featureType + " is not available in product: " + pseudoId +
                        "( field e" + featureType + " is empty)");
                continue;
            }
            if(oriProduct != null && featureAbbrevStr.equals(oriProduct.getNoCheckSimple(infoFieldName))) {
                LOGGER.debug("Feature type" + featureType + ", product: " + pseudoId +
                        " field e" + featureType + " is not changed");
                continue;
            }

            String[] abbrevs = featureAbbrevStr.split(",");
            for (int i = abbrevs.length - 1; i >= 0; i--) {
                abbrevs[i] = abbrevs[i].trim();
                if(!abbrevs[i].isEmpty()) {
                    ArrayUtils.remove(abbrevs, i);
                }
            }
            if(abbrevs.length == 0) {
                LOGGER.debug("Feature type" + featureType + " is not valid in product: " + pseudoId +
                        "field e" + featureType + " is: " +featureAbbrevStr);
                continue;
            }

            //GENERATE features:
            List<EntityValue> features = createProductFeatures(featureType, abbrevs, null);
            if(!features.isEmpty()) {
                for (EntityValue feature : features) {
                    //GENERATE Selectable FeatureAppl for Virtual Product:
                    createProductFeatureAppl(product, feature, "PfatSelectable", today, null, ef);
                }
                featureMap.put(featureType, features);
            }
        }
        if(featureMap.isEmpty()) {
            LOGGER.info("Found no new features, skip generating selectable features for a product: " + pseudoId);
            return;
        }

        Set<Map.Entry<String, List<EntityValue>>> fEntrySet = featureMap.entrySet();
        String[] featureTypes = featureMap.keySet().toArray(new String[0]);
        int numFeatureType = featureTypes.length;
        int[] fMaxIndexes = new int[numFeatureType];
        int[] fIndexes = new int[numFeatureType];
        EntityValue[][] featuresByType = new EntityValue[numFeatureType][];
        for(int i = 0; i < numFeatureType; i++) {
            featuresByType[i] = featureMap.get(featureTypes[i]).toArray(new EntityValue[]{});
            fMaxIndexes[i] = featuresByType[i].length - 1;
            fIndexes[i] = 0;
        }

        //GENERATE Sets of Distinguish features
        List<EntityValue[]> featureArrList = new ArrayList<>();
        for(int i = 0; i < featuresByType[0].length; i++) {
            EntityValue[] arrFeatures = new EntityValue[numFeatureType];
            arrFeatures[0] = featuresByType[0][i];
            featureArrList.add(arrFeatures);
        }
        for(int iType = 1; iType < numFeatureType; iType++) {
            EntityValue[] sameTypeFeatures = featuresByType[iType];
            ArrayList<EntityValue[]> tempList = new ArrayList<>();
            for(EntityValue[] prevFeatures : featureArrList) {
                for (int i = 1; i < sameTypeFeatures.length; i++) {
                    EntityValue[] arrFeatures = Arrays.copyOf(prevFeatures, numFeatureType);
                    arrFeatures[iType] = sameTypeFeatures[i];
                    tempList.add(arrFeatures);
                }
                prevFeatures[iType] = sameTypeFeatures[0]; //already added
            }
            featureArrList.addAll(tempList);
        }

        //Generate Product Variants, Assocs and Distinguish featureAppl:
        int assocSeqNo = 1;


        for(EntityValue[] features : featureArrList) {
            StringBuilder abbrevsSb = new StringBuilder("_");
            for(EntityValue feature : features) {
                String abbrev = (String) feature.getNoCheckSimple("abbrev");
                if(!abbrev.isEmpty()) {
                    abbrevsSb.append(abbrev);
                }
            }
            String abbrevStr = abbrevsSb.toString();

            String variantId = (productId + abbrevStr);
            if(variantId.length() > 50) {
                variantId = variantId.substring(0, 50);
            }
            EntityValue variant = ef.find("mantle.product.Product").condition("productId", variantId).one();
            if(variant == null) {
                LOGGER.debug("Creating variant: " + variantId);
                //Generate Variant
                variant = ef.makeValue("mantle.product.Product");
                variant.setAll(productValueMap);
                variant.setString("productId", variantId);

                String variantPseudoId = (pseudoId + abbrevStr);
                if (variantPseudoId.length() > 50) {
                    variantPseudoId = variantPseudoId.substring(0, 50);
                }
                variant.setString("pseudoId", variantPseudoId);
                variant.create();
            }

            EntityValue productAssoc =ef.find("mantle.product.ProductAssoc")
                    .condition("productId", productId)
                    .condition("toProductId", variantId)
                    .conditionDate("fromDate", "thruDate", today)
                    .condition("productAssocTypeEnumId", "PatVariant")
                    .one();
            if(productAssoc == null) {
                LOGGER.debug("Created product assoc from " + productId + " to " + variantId);
                //Generate ProductAssoc:
                productAssoc = ef.makeValue("mantle.product.ProductAssoc");
                productAssoc.setString("productId", productId);
                productAssoc.setString("toProductId", variantId);
                productAssoc.setString("productAssocTypeEnumId", "PatVariant");
                productAssoc.setString("reason", "Auto generated");
                productAssoc.set("sequenceNum", assocSeqNo);
                productAssoc.set("fromDate", today);
                assocSeqNo++;
                productAssoc.create();
            }

            //Generate Distinguish featureAppl
            for(EntityValue feature : features) {
                createProductFeatureAppl(variant, feature,
                        "PfatDistinguishing", today, null);
            }
        }
    }
}
