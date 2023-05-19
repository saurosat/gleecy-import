package io.gleecy.converter;

import org.moqui.impl.entity.EntityFacadeImpl;

import java.util.*;

public interface ValueConverter {
    Object convert(Object value, List<String> errors);
    boolean initialize(String configStr);
    default boolean load(EntityFacadeImpl efi) { //To be override
        return true;
    }

}
