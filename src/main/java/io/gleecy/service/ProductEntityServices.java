package io.gleecy.service;

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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

public class ProductEntityServices {

    private static final Map<String, EntityValue> pendingEntities = new HashMap<>();
    private static synchronized EntityValue popPendingEntity(String key) {
        return pendingEntities.remove(key);
    }
    private static synchronized void pushPendingEntity(String key, EntityValue entity) {
        pendingEntities.put(key, entity);
    }

    public static String[] EMPTY_ARR = new String[0];
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductEntityServices.class);
    private static final ThreadLocal<ExecutionContext> _ec = new ThreadLocal<>();
    private static ExecutionContext getEc() {
        return _ec.get();
    }
    private static String getHashString(String str, int hashLen) {
        if(str == null || str.isEmpty() || str.equals("_NA_") ){
            return "";
        }
        int hashCode = str.hashCode();
        return StringUtils.leftPad(Integer.toHexString(hashCode), hashLen, '_');
    }
    private static EntityValue getProductFeature(String abbrev, String featureTypeEnumId, EntityFacade ef) {
        EntityList features = ef.find("mantle.product.feature.ProductFeature")
                .condition("abbrev", abbrev).forUpdate(false).useCache(true).disableAuthz()
                .condition("productFeatureTypeEnumId", featureTypeEnumId).list();
        return  features == null || features.isEmpty() ? null : features.getFirst();
    }
    private static synchronized EntityValue createProductFeature(
            String featureTypeEnumId, String abbrev, String tenantId, EntityFacade ef) {
        EntityValue featureEv = getProductFeature(abbrev, featureTypeEnumId, ef);
        if(featureEv != null) {
            return featureEv;
        }
        featureEv = ef.makeValue("mantle.product.feature.ProductFeature");
        featureEv.setString("productFeatureTypeEnumId", featureTypeEnumId);
        featureEv.setString("abbrev", abbrev);
        featureEv.set("ownerPartyId", tenantId);
        featureEv.setSequencedIdPrimary();
        featureEv.create();
        return featureEv;
    }

    private static EntityValue getCategory(String pseudoId, String tenantId, EntityFacade ef) {
        return ef.find("mantle.product.category.ProductCategory")
                .condition("pseudoId", pseudoId)
                .condition("ownerPartyId", tenantId)
                .forUpdate(true)
                .one();
    }
    public static String[] splitStr(String s) {
        if(s == null || (s = s.trim()).isEmpty() || s.equalsIgnoreCase("_NA_")) {
            return EMPTY_ARR;
        }

        String[] a = s.split(",");
        for(int i = a.length - 1; i >= 0; i--) {
            a[i] = a[i].trim();
        }
        return a;
    }
    private static String pendingRollupKey(String pseudoId, String tenantId) {
        return "rollup_" + pseudoId + "_" + tenantId;
    }
    public static void generateCategoryRelates(ExecutionContext ec) {
        EntityFacade ef = ec.getEntity();
        UserFacade uf = ec.getUser();
        String tenantId = uf.getTenantId();

        ContextStack cs = ec.getContext();
        EntityValue category = (EntityValue) cs.get("entityValue");
        EntityValue pendingRollup = popPendingEntity(pendingRollupKey((String) category.getNoCheckSimple("pseudoId"), tenantId));
        if(pendingRollup != null) {
            pendingRollup.set("parentProductCategoryId", category.getNoCheckSimple("productCategoryId"));
            pendingRollup.create();
        }

        String sParentCats = (String) category.getNoCheckSimple("parentCategories");
        if(sParentCats != null && sParentCats.isBlank()) {
            Set<String> parentCats = sParentCats.equals("_NA_") ? Set.of() : new HashSet<>(Arrays.asList(splitStr(sParentCats)));
            Timestamp today = new Timestamp(System.currentTimeMillis());
            String categoryId = (String) category.getNoCheckSimple("productCategoryId");
            EntityList listRollup = ef.find("mantle.product.category.ProductCategoryRollup")
                    .condition("productCategoryId", categoryId)
                    .forUpdate(true).list();
            for(EntityValue rollup: listRollup) {
                EntityValue cat = ef.fastFindOne("mantle.product.category.ProductCategory",
                        true, true, rollup.getNoCheckSimple("productCategoryId"));
                String pseudoId = (String) cat.getNoCheckSimple("pseudoId");
                if(!parentCats.contains(pseudoId)) {
                    rollup.set("thruDate", today);
                    rollup.update();
                } else {
                    rollup.set("thruDate", null);
                    rollup.update();
                    parentCats.remove(pseudoId);
                }
            }
            for(String pseudoId : parentCats) {
                EntityValue rollup = ef.makeValue("mantle.product.category.ProductCategoryRollup");
                rollup.setString("productCategoryId", categoryId);
                rollup.set("fromDate", today);
                EntityValue parent = getCategory(pseudoId, tenantId, ef);
                if (parent == null) {
                    pushPendingEntity(pendingRollupKey(pseudoId, tenantId), rollup);
                } else {
                    rollup.set("parentProductCategoryId", parent.getNoCheckSimple("productCategoryId"));
                    rollup.create();
                }
            }
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
        String _pseudoId = (String) productValueMap.remove("pseudoId");
        final String pseudoId = (_pseudoId == null) ? productId : _pseudoId;

        if(!"PtVirtual".equalsIgnoreCase(productType)) {
            LOGGER.info("Cannot generate selectable features for a non-virtual product: "
                    + pseudoId + ", productTypeEnumId = " + productType);
            return;
        }

        /**
         * GENERATE ProductCategoryMember. Categories must be imported prior
         */
        String sCatPseudoIds = (String) product.getNoCheckSimple("categories");
        if(sCatPseudoIds != null && !sCatPseudoIds.isBlank()) {
            Set<String> catPseudoIds = sCatPseudoIds.equals("_NA_") ?
                    Set.of() : new HashSet<>(Arrays.asList(splitStr(sCatPseudoIds)));
            EntityList catMems = ef.find("mantle.product.category.ProductCategoryMember")
                    .condition("productId", productId)
                    .forUpdate(true).list();
            for(EntityValue catMem : catMems) {
                EntityValue cat = ef.fastFindOne("mantle.product.category.ProductCategory",
                        true, true, catMem.getNoCheckSimple("productCategoryId"));
                String catPseudoId = (String) cat.getNoCheckSimple("pseudoId");
                if(!catPseudoIds.contains(catPseudoId)) {
                    catMem.set("thruDate", today);
                    catMem.update();
                } else {
                    catMem.set("thruDate", null);
                    catMem.update();
                    catPseudoIds.remove(catPseudoId);
                }
            }
            for(String catPseudoId : catPseudoIds) {
                EntityValue cat = getCategory(catPseudoId, tenantId, ef);
                if (cat != null) {
                    EntityValue catMember = ef.makeValue("mantle.product.category.ProductCategoryMember");
                    catMember.setString("productId", productId);
                    catMember.set("fromDate", today);
                    catMember.set("productCategoryId", cat.getNoCheckSimple("productCategoryId"));
                    catMember.create();
                }
            }
        }

        /**
         * GENERATE ProductFeature, ProductFeatureAppl, ProductVariant, ProductVariantAppl
         * GENERATE duplicated product records for variants
         */
        EntityList featureTypeEnums = ef.find("moqui.basic.Enumeration")
                .condition("enumTypeId", "ProductFeatureType")
                .useCache(true).disableAuthz().list();

        HashMap<String, Set<String>> abbrevMap = new HashMap<>();
        for(EntityValue featureTypeEnum : featureTypeEnums) {
            String featureType = (String) featureTypeEnum.getNoCheckSimple("enumId");
            String infoFieldName = "e" + featureType;
            String featureAbbrevStr = (String) productValueMap.remove(infoFieldName);
            if(StringUtils.isBlank(featureAbbrevStr)) {
                LOGGER.debug("Feature type" + featureType + " is blank in imported product: " + pseudoId +
                        "( field e" + featureType + " is empty)");
                continue;
            }
            Set<String> abbrevs = featureAbbrevStr.equals("_NA_") ?
                    Set.of() : new HashSet<>(Arrays.asList(splitStr(featureAbbrevStr)));
            abbrevMap.put(featureType, abbrevs);
        }

        TreeMap<String, List<EntityValue>> featureMap = new TreeMap<>();
        EntityList allFeatureAppls = ef.find("mantle.product.feature.ProductFeatureAppl")
                .condition("productId", productId)
                .condition("applTypeEnumId", "PfatSelectable")
                .forUpdate(true)
                .list();
        for(EntityValue appl : allFeatureAppls) {
            EntityValue feature = ef.fastFindOne( "mantle.product.feature.ProductFeature", true,
                    true, (String) appl.getNoCheckSimple("productFeatureId"));
            String fType = (String) feature.getNoCheckSimple("productFeatureTypeEnumId");
            String abbrev = (String) feature.getNoCheckSimple("abbrev");
            Set<String> abbrevs = abbrevMap.get(fType);
            if(abbrevs == null) { //Not mentioned in imported file
                featureMap.computeIfAbsent(fType, k -> new ArrayList<>()).add(feature);
            } else if(abbrevs.contains(abbrev)) {
                featureMap.computeIfAbsent(fType, k -> new ArrayList<>()).add(feature);
                abbrevs.remove(abbrev); //already exist, remove to avoid inserting again
                appl.set("thruDate", null);
                appl.update();
            } else { //Delete:
                appl.set("thruDate", today);
                appl.update();
            }
        }

        abbrevMap.forEach((fType, abbrevs) -> {
            if(!abbrevs.isEmpty()) {
                List<EntityValue> features = featureMap.computeIfAbsent(fType, k -> new ArrayList<>());
                for (String abbrev : abbrevs) {
                    EntityValue featureEv = getProductFeature(abbrev, fType, ef);
                    if(featureEv == null) {
                        featureEv = createProductFeature(fType, abbrev, tenantId, ef);
                    }
                    features.add(featureEv);
                    EntityValue appl = ef.makeValue("mantle.product.feature.ProductFeatureAppl");
                    appl.setString("applTypeEnumId", "PfatSelectable");
                    appl.setString("productId", productId);
                    appl.set("productFeatureId", featureEv.getNoCheckSimple("productFeatureId"));
                    appl.set("fromDate", today);
                    appl.create();
                }
            }
        });

        int numFeatureType = featureMap.size();
        EntityValue[][] featuresByType = new EntityValue[numFeatureType][];
        final int[] iFType = {0};
        featureMap.forEach((fType, fList) -> {
            featuresByType[iFType[0]] = fList.toArray(new EntityValue[0]);
            iFType[0]++;
        });

        //GENERATE Sets of Distinguish features
        List<EntityValue[]> distinguishList = new ArrayList<>();
        if(featuresByType.length > 0) {
            //Pick first one feature in each feature type
            for (int i = 0; i < featuresByType[0].length; i++) {
                EntityValue[] distinguish = new EntityValue[numFeatureType];
                distinguish[0] = featuresByType[0][i];
                distinguishList.add(distinguish);
            }
            for (int iType = 1; iType < numFeatureType; iType++) {
                EntityValue[] selectable = featuresByType[iType];
                ArrayList<EntityValue[]> tempList = new ArrayList<>();
                for (EntityValue[] prevDistinguish : distinguishList) {
                    for (int i = 1; i < selectable.length; i++) {
                        EntityValue[] distinguishes = Arrays.copyOf(prevDistinguish, numFeatureType);
                        distinguishes[iType] = selectable[i];
                        tempList.add(distinguishes);
                    }
                    prevDistinguish[iType] = selectable[0]; //already added
                }
                distinguishList.addAll(tempList);
            }
        }
        //Generate Product Variants, Assocs and Distinguish featureAppl:
        HashMap<String, EntityValue[]> abbrToFeaturesMap = new HashMap<>();
        for(EntityValue[] features : distinguishList) {
            StringBuilder abbrevsSb = new StringBuilder("_");
            for(EntityValue feature : features) {
                String abbrev = (String) feature.getNoCheckSimple("abbrev");
                if(!abbrev.isEmpty()) {
                    abbrevsSb.append(abbrev);
                }
            }
            String abbrevStr = abbrevsSb.toString();
            abbrToFeaturesMap.put(abbrevStr, features);
        }
        EntityList assocList = ef.find("mantle.product.ProductAssoc")
                .condition("productId", productId)
                .condition("productAssocTypeEnumId", "PatVariant")
                .forUpdate(true)
                .list();
        for(EntityValue assoc : assocList) {
            String toProductId = (String) assoc.getNoCheckSimple("toProductId");
            String abbrev = toProductId.substring(productId.length());
            if(abbrToFeaturesMap.containsKey(getHashString(abbrev, 8))) { //already exist
                assoc.set("thruDate", null);
                assoc.update();
                abbrToFeaturesMap.remove(abbrev);
            } else { //delete this assoc:
                assoc.set("thruDate", today);
                assoc.update();
            }
        }
        final int[] seqNums = {0};
        abbrToFeaturesMap.forEach((abbrev, features) -> {
            String variantId = productId + getHashString(abbrev, 8);
            EntityValue variant = ef.fastFindOne("mantle.product.Product", true, true, variantId);
            if(variant == null) {
                variant = ef.fastFindOne("mantle.product.Product", false, true, variantId);
            }
            if(variant == null) {
                LOGGER.debug("Creating variant: " + abbrev + " for product ID " + productId);
                //Generate Variant
                variant = ef.makeValue("mantle.product.Product");
                variant.setAll(productValueMap);
                variant.setString("productId", variantId);

                String variantPseudoId = pseudoId + abbrev;
//                    if (variantPseudoId.length() > 63) {
//                        variantPseudoId = variantPseudoId.substring(0, 63);
//                    }
                variant.setString("pseudoId", variantPseudoId);
                variant.create();
            }
            EntityValue productAssoc = ef.makeValue("mantle.product.ProductAssoc");
            productAssoc.setString("productId", productId);
            productAssoc.setString("toProductId", variantId);
            productAssoc.setString("productAssocTypeEnumId", "PatVariant");
            productAssoc.setString("reason", "Auto generated");
            productAssoc.set("sequenceNum", seqNums[0]);
            productAssoc.set("fromDate", today);
            seqNums[0]++;
            productAssoc.create();

            //Generate Distinguish featureAppl
            for(EntityValue feature : features) {
                EntityValue appl = ef.makeValue("mantle.product.feature.ProductFeatureAppl");
                appl.setString("applTypeEnumId", "PfatDistinguishing");
                appl.setString("productId", variantId);
                appl.setString("productFeatureId", (String) feature.getNoCheckSimple("productFeatureId"));
                appl.set("fromDate", today);
                appl.create();
            }
        });

        /**
         *
         */
        final String[] priceTypes = new String[]{"PptList", "PptCurrent", "PptWholesale"};
//                , "PptAverage", "PptMinimum", "PptMaximum"
//                , "PptPromotional", "PptCompetitive", "PptSpecialPromo"};

        String sUomPrices = (String) product.getNoCheckSimple("prices");
        String[] uomPrices = sUomPrices.split("-");
        for (String uomPrice : uomPrices) {
            uomPrice = uomPrice.trim();
            if(uomPrice.length() <=3) {
                LOGGER.error("Invalid price value " + uomPrice);
                continue;
            }

            String uomId = uomPrice.substring(0, 3);
            EntityValue uom = ef.fastFindOne("moqui.basic.Uom", true, true, uomId);
            if(uom == null) {
                LOGGER.error("Invalid currency " + uomId);
                continue;
            }
            String sPrices = uomPrice.substring(3).trim();
            String[] newPrices = splitStr(sPrices);
            if (newPrices != EMPTY_ARR) {
                EntityList priceList = ef.find("mantle.product.ProductPrice")
                        .condition("productId", productId)
                        .condition("priceUomId", uomId)
                        .conditionDate("fromDate", "thruDate", today)
                        .forUpdate(true)
                        .list();
                for (EntityValue price : priceList) {
                    String priceType = (String) price.getNoCheckSimple("priceTypeEnumId");
                    int priceTypeIdx = getIndex(priceType, priceTypes);
                    if (priceTypeIdx < 0) {
                        continue;
                    }
                    String newPriceVal = newPrices[priceTypeIdx];
                    if (newPriceVal != null && !newPriceVal.isEmpty()) {
                        newPrices[priceTypeIdx] = null;
                        if (newPriceVal.equals("_NA_")) {
                            //DELETE (set expired)
                            price.set("thruDate", today);
                            price.update();
                        } else {
                            try {
                                //UPDATE
                                BigDecimal priceVal = new BigDecimal(newPriceVal);
                                price.set("price", priceVal);
                                price.update();
                            } catch (NumberFormatException e) {
                                LOGGER.info("Failed to import prices. value is not a valid number: " + newPriceVal);
                            }
                        }
                    }
                }
                int i = newPrices.length;
                if (i > priceTypes.length) i = priceTypes.length;
                for (i--; i >= 0; i--) {
                    String newPriceVal = newPrices[i];
                    if (newPriceVal == null || newPriceVal.equals("_NA_")) {
                        continue;
                    }
                    try {
                        //UPDATE
                        BigDecimal priceVal = new BigDecimal(newPriceVal);
                        EntityValue price = ef.makeValue("mantle.product.ProductPrice");
                        price.set("priceTypeEnumId", priceTypes[i]);
                        price.set("price", priceVal);
                        price.set("priceUomId", uomId);
                        price.set("minQuantity", 1);
                        price.set("fromDate", today);
                        price.set("pricePurposeEnumId", "PppPurchase");
                        price.set("productId", productId);
                        price.set("vendorPartyId", tenantId);
                        price.set("quantityUomId", "OTH_ea");
                        price.setSequencedIdPrimary();
                        price.create();
                    } catch (Exception e) {
                        LOGGER.info("Failed to import prices. value is not a valid number: " + newPriceVal);
                    }
                }
            }
        }

    }
    private static int getIndex(String s, String[] source) {
        if(s == null) return -1;
        int i =  source.length - 1;
        for(; i >= 0; i--) if(source[i].equals(s)) break;
        return i;
    }
}
