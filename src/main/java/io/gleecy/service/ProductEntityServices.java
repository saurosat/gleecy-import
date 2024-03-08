package io.gleecy.service;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.moqui.context.ExecutionContext;
import org.moqui.context.UserFacade;
import org.moqui.entity.EntityFacade;
import org.moqui.entity.EntityList;
import org.moqui.entity.EntityValue;
import org.moqui.impl.context.ExecutionContextImpl;
import org.moqui.impl.entity.EntityDefinition;
import org.moqui.impl.entity.EntityFacadeImpl;
import org.moqui.util.ContextStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;

public class ProductEntityServices {
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductEntityServices.class);
    private static final ThreadLocal<ExecutionContext> _ec = new ThreadLocal<>();
    private static ExecutionContext getEc() {
        return _ec.get();
    }
    public static List<EntityValue> createProductFeatures(String featureTypeEnumId, String[] abbrevs,
                                                          Map<String, Object> valueMap) {
        ExecutionContext ec = getEc();

        EntityFacadeImpl efi = ((ExecutionContextImpl) ec).getEntityFacade();
        UserFacade uf = ec.getUser();
        String tenantId = uf.getTenantId();

        EntityDefinition featureEd = efi.getEntityDefinition("mantle.product.feature.ProductFeature");
        List<EntityValue> evs = new ArrayList<>(abbrevs.length);
        for(String abbrev : abbrevs) {
            EntityValue featureEv = getProductFeature(abbrev, featureTypeEnumId, efi);
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
    private static EntityValue getProductFeature(String abbrev, String featureTypeEnumId, EntityFacade ef) {
        return ef.find("mantle.product.feature.ProductFeature")
                .condition("abbrev", abbrev).useCache(true).disableAuthz()
                .condition("productFeatureTypeEnumId", featureTypeEnumId).one();
    }
    private static List<EntityValue> removeFeatureAppl(String pId, String featureId, Timestamp validDate, EntityFacade ef) {
        List<EntityValue> updated = new ArrayList<>();
        EntityList appls = ef.find("mantle.product.feature.ProductFeatureAppl")
                .condition("productId", pId)
                .condition("productFeatureId", featureId)
                .conditionDate("fromDate", "thruDate", validDate)
                .forUpdate(true)
                .list();
        for (EntityValue appl : appls) {
            appl.set("thruDate", validDate);
            appl.update();
            updated.add(appl);
        }
        return updated;
    }
    public static List<EntityValue> removeProductFeatures(String productId, String featureTypeEnumId, String[] abbrevs) {
        List<EntityValue> entities = new ArrayList<>();
        EntityFacade ef = getEc().getEntity();
        Timestamp today = new Timestamp(System.currentTimeMillis());
//        EntityList assocs =ef.find("mantle.product.ProductAssoc")
//                .condition("productId", productId)
//                .conditionDate("fromDate", "thruDate", today)
//                .condition("productAssocTypeEnumId", "PatVariant")
//                .forUpdate(true)
//                .list();
        // if(assocs == null || assocs.isEmpty()) return entities;
        for(String abbrev : abbrevs) {
            EntityValue feature = getProductFeature(abbrev, featureTypeEnumId, ef);
            if(feature == null) continue;
            entities.add(feature);
            String featureId = (String) feature.getNoCheckSimple("productFeatureId");
            entities.addAll(removeFeatureAppl(productId, featureId, today, ef));

//            for(EntityValue assoc : assocs) {
//                String pId = (String) assoc.getNoCheckSimple("toProductId");
//                appl = removeFeatureAppl(pId, featureId, today, ef);
//                if(appl != null) {
//                    entities.add(appl);
//                    assoc.set("thruDate", today);
//                    assoc.update();
//                    entities.add(assoc);
//                }
//            }
        }
        return  entities;
    }

    public static EntityValue createProductFeatureAppl(EntityValue product, EntityValue feature, String applType,
                                                       Timestamp fromDate, Map<String, Object> valueMap) {
        EntityFacade ef = getEc().getEntity();
        String productId = (String) product.getNoCheckSimple("productId");
        String featureId = (String) feature.getNoCheckSimple("productFeatureId");
        LOGGER.debug("Creating FeatureAppl for product " + productId + ", feature " + featureId + ", fromdate " + fromDate.toString());
        EntityValue appl = ef.find("mantle.product.feature.ProductFeatureAppl")
                .condition("productId", productId)
                .condition("productFeatureId", featureId).useCache(true).disableAuthz()
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
    private static EntityValue getCategory(String pseudoId, String tenantId, EntityFacade ef) {
        return ef.find("mantle.product.category.ProductCategory")
                .condition("pseudoId", pseudoId)
                .condition("ownerPartyId", tenantId)
                .forUpdate(true)
                .one();
    }
    private static EntityValue getCategoryRollup(String categoryId, String parentId, Timestamp checkTime, EntityFacade ef) {
        return ef.find("mantle.product.category.ProductCategoryRollup")
                .condition("productCategoryId", categoryId)
                .condition("parentProductCategoryId", parentId)
                .conditionDate("fromDate", "thruDate", checkTime)
                .forUpdate(true)
                .one();
    }
    private static EntityValue getCategoryMember(String productId, String categoryId, Timestamp checkTime, EntityFacade ef) {
        return ef.find("mantle.product.category.ProductCategoryMember")
                .condition("productCategoryId", categoryId)
                .condition("productId", productId)
                .conditionDate("fromDate", "thruDate", checkTime)
                .forUpdate(true)
                .one();
    }
    private static final class ArrayNewOld {
        public static String[] EMPTY_ARR = new String[0];
        public final String[] oldValues;
        public final String[] newValues;
        public final boolean hasNew;
        public ArrayNewOld(String newStr, String oldStr) {
            String[] newVals = splitStr(newStr);
            String[] oldVals = splitStr(oldStr);
            if(newVals != null && newVals != EMPTY_ARR
                    && oldVals != null && oldVals != EMPTY_ARR) {
                for(int i = oldVals.length - 1; i >= 0; i--) {
                    for (int j = newVals.length - 1; j >= 0; j--) {
                        if(oldVals[i].equalsIgnoreCase(newVals[j])) {
                            ArrayUtils.remove(oldVals, i);
                            ArrayUtils.remove(newVals, j);
                        }
                    }
                }
                if(newVals.length == 0) newVals = EMPTY_ARR;
                if(oldVals.length == 0) oldVals = EMPTY_ARR;
            }
            this.newValues = newVals;
            this.oldValues = oldVals;
            hasNew = (newVals != null && newVals != oldVals);
        }
        public String[] splitStr(String s) {
            if(s == null || (s = s.trim()).isEmpty()) {
                return null;
            }
            if(s.equalsIgnoreCase("_NA_")) {
                return EMPTY_ARR;
            }

            String[] a = s.split(",");
            for(int i = a.length - 1; i >= 0; i--) {
                a[i] = a[i].trim();
            }
            return a;
        }
    }
    private static ArrayNewOld getArrayPair(EntityValue eNew, EntityValue eOld, String fieldName) {
        String newStr = eNew != null ? (String) eNew.getNoCheckSimple(fieldName) : null;
        String oldStr = eOld != null ? (String) eOld.getNoCheckSimple(fieldName) : null;
        return new ArrayNewOld(newStr, oldStr);
    }
    public static void generateCategoryRelates(ExecutionContext ec) {
        EntityFacade ef = ec.getEntity();
        UserFacade uf = ec.getUser();
        String tenantId = uf.getTenantId();

        ContextStack cs = ec.getContext();
        EntityValue category = (EntityValue) cs.get("entityValue");
        EntityValue oriCategory = (EntityValue) cs.get("originalValue");

        ArrayNewOld newOldPseudos = getArrayPair(category, oriCategory, "parentCategories");
        if(!newOldPseudos.hasNew) {
            return;
        }
        Timestamp today = new Timestamp(System.currentTimeMillis());
        String categoryId = (String) category.getNoCheckSimple("productCategoryId");

        String[] oldParentPseudoIds = newOldPseudos.oldValues;
        if(oldParentPseudoIds != null && oldParentPseudoIds != ArrayNewOld.EMPTY_ARR) {
            for(String oldParentPseudoId : oldParentPseudoIds) {
                EntityValue parentCategory = getCategory(oldParentPseudoId, tenantId, ef);
                if(parentCategory == null) {
                    LOGGER.info("Category not found: " + oldParentPseudoId);
                    continue;
                }

                EntityValue rollup = getCategoryRollup(categoryId,
                        (String) parentCategory.getNoCheckSimple("productCategoryId"),
                        today, ef);
                if(rollup == null) {
                    continue;
                }
                rollup.set("thruDate", today);
                rollup.update();
            }
        }

        String[] newParentPseudoIds = newOldPseudos.newValues;
        int[] attempts = new int[newParentPseudoIds.length];
        Arrays.fill(attempts, 0);
        for(int i = 0; i < newParentPseudoIds.length; i++) {
            String newParentPseudoId = newParentPseudoIds[i];
            EntityValue parent = getCategory(newParentPseudoId, tenantId, ef);
            if(parent == null) {
                if(attempts[i] < MAX_ATTEMPTS) {
                    attempts[i]++;
                    LOGGER.info("RETRIES: " + attempts[i] +  " times: Parent category '" + newParentPseudoId +
                            "' not found. Waiting it to be inserted");
                    i--;
                    continue;
                }
                LOGGER.info("RETRIES: " + attempts[i] +  " times: Parent category '" + newParentPseudoId + "' not found. Skipped!");
                continue;
            }
            String parentId = (String) parent.getNoCheckSimple("productCategoryId");
            EntityValue rollup = getCategoryRollup(categoryId, parentId, today, ef);
            if(rollup != null) {
                continue;
            }
            rollup = ef.makeValue("mantle.product.category.ProductCategoryRollup");
            rollup.setString("productCategoryId", categoryId);
            rollup.setString("parentProductCategoryId", parentId);
            rollup.set("fromDate", today);
            rollup.create();
        }
    }
    public static void generateProductRelates(ExecutionContext ec) {
        _ec.set(ec);
        EntityFacade ef = ec.getEntity();
        UserFacade uf = ec.getUser();
        String tenantId = uf.getTenantId();

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

        if(!"PtVirtual".equalsIgnoreCase(productType)) {
            LOGGER.info("Cannot generate selectable features for a non-virtual product: "
                    + pseudoId + ", productTypeEnumId = " + productType);
            return;
        }

        ArrayNewOld newOldPseudos = getArrayPair(product, oriProduct, "categories");
        if(newOldPseudos.hasNew) {
            String[] oldCategories = newOldPseudos.oldValues;
            if(oldCategories != null && oldCategories != ArrayNewOld.EMPTY_ARR) {
                for(String catPseudo : oldCategories) {
                    EntityValue cat = getCategory(catPseudo, tenantId, ef);
                    if(cat == null) {
                        LOGGER.info("Category not found: " + catPseudo);
                        continue;
                    }
                    String categoryId = (String) cat.getNoCheckSimple("productCategoryId");
                    EntityValue catMember = getCategoryMember(productId, categoryId, today, ef);
                    if(catMember != null) {
                        catMember.set("thruDate", today);
                        catMember.update();
                    }
                }
            }
            String[] newCategories = newOldPseudos.newValues;
            for(String catPseudo : newCategories) {
                EntityValue cat = getCategory(catPseudo, tenantId, ef);
                if(cat == null) {
                    LOGGER.info("Category not found: " + catPseudo);
                    continue;
                }
                String categoryId = (String) cat.getNoCheckSimple("productCategoryId");
                EntityValue catMember = getCategoryMember(productId, categoryId, today, ef);
                if(catMember != null) {
                    continue;
                }
                catMember = ef.makeValue("mantle.product.category.ProductCategoryMember");
                catMember.setString("productId", productId);
                catMember.setString("productCategoryId", categoryId);
                catMember.set("fromDate", today);
                catMember.create();
            }
        }

        EntityList featureTypeEnums = ef.find("moqui.basic.Enumeration")
                .condition("enumTypeId", "ProductFeatureType")
                .useCache(true).disableAuthz().list();

        TreeMap<String, List<EntityValue>> featureMap = new TreeMap<>();
        for(EntityValue featureTypeEnum : featureTypeEnums) {
            String featureType = (String) featureTypeEnum.getNoCheckSimple("enumId");
            String infoFieldName = "e" + featureType;
            String featureAbbrevStr = (String) productValueMap.remove(infoFieldName);
            if(StringUtils.isBlank(featureAbbrevStr)) {
                LOGGER.debug("Feature type" + featureType + " is blank in imported product: " + pseudoId +
                        "( field e" + featureType + " is empty)");
                continue;
            }
            String oriAbbrStr = oriProduct != null ? (String) oriProduct.getNoCheckSimple(infoFieldName) : null;
            ArrayNewOld newOldAbbrev = new ArrayNewOld(featureAbbrevStr, oriAbbrStr);
            if(!newOldAbbrev.hasNew) {
                LOGGER.debug("Feature type" + featureType + ", product: " + pseudoId +
                        " field e" + featureType + " is not changed");
                continue;
            }
            String[] oriAbbrevs = newOldAbbrev.oldValues;
            if(oriAbbrevs != null && oriAbbrevs.length > 0) {
                removeProductFeatures(productId, featureType, oriAbbrevs);
            }

            String[] abbrevs = newOldAbbrev.newValues;
            if(abbrevs.length == 0) {
                LOGGER.debug("Feature type '" + featureType + "', product '" + pseudoId +
                        "', imported features: " + featureAbbrevStr + ": no new abbrevs.");
                continue;
            }

            //GENERATE features:
            List<EntityValue> features = createProductFeatures(featureType, abbrevs, null);
            if(!features.isEmpty()) {
                for (EntityValue feature : features) {
                    //GENERATE Selectable FeatureAppl for Virtual Product:
                    createProductFeatureAppl(product, feature, "PfatSelectable", today, null);
                }
                featureMap.put(featureType, features);
            }
        }
        if(featureMap.isEmpty()) {
            LOGGER.info("Found no new features, skip generating selectable features for a product: " + pseudoId);
            return;
        }

        String[] featureTypes = featureMap.keySet().toArray(new String[0]);
        int numFeatureType = featureTypes.length;
        EntityValue[][] featuresByType = new EntityValue[numFeatureType][];
        for(int i = 0; i < numFeatureType; i++) {
            featuresByType[i] = featureMap.get(featureTypes[i]).toArray(new EntityValue[]{});
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

                LOGGER.debug("Created product assoc from " + productId + " to " + variantId);
                //Generate ProductAssoc:
                EntityValue productAssoc = ef.makeValue("mantle.product.ProductAssoc");
                productAssoc.setString("productId", productId);
                productAssoc.setString("toProductId", variantId);
                productAssoc.setString("productAssocTypeEnumId", "PatVariant");
                productAssoc.setString("reason", "Auto generated");
                productAssoc.set("sequenceNum", assocSeqNo);
                productAssoc.set("fromDate", today);
                assocSeqNo++;
                productAssoc.create();

                //Generate Distinguish featureAppl
                for(EntityValue feature : features) {
                    createProductFeatureAppl(variant, feature,
                            "PfatDistinguishing", today, null);
                }

            }
        }
    }
}
