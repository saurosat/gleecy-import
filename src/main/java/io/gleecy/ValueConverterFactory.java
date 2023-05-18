package io.gleecy;

import org.moqui.impl.entity.EntityFacadeImpl;

public class ValueConverterFactory {
    public ValueConverter newInstance(String configStr, EntityFacadeImpl efi) {
        ValueConverter converter = null;

        if(configStr.startsWith(ValueConverter.ColIndex.PREFIX)) {
            converter = new ValueConverter.ColIndex();
        } else if (configStr.startsWith(ValueConverter.DefaultValue.PREFIX)) {
            converter = new ValueConverter.DefaultValue();
        } else if (configStr.startsWith(ValueConverter.ValueMapping.PREFIX)) {
            converter = new ValueConverter.ValueMapping();
        } else if (configStr.startsWith(ValueConverter.Date.PREFIX)) {
            converter = new ValueConverter.Date();
        } else if (configStr.startsWith(ValueConverter.Time.PREFIX)) {
            converter = new ValueConverter.Time();
        } else if (configStr.startsWith(ValueConverter.FieldMapping.PREFIX)) {
            converter = new ValueConverter.FieldMapping();
        } else if (configStr.startsWith(ValueConverter.Trim.PREFIX)) {
            converter = new ValueConverter.Trim();
        } else {
            return null;
        }
        if(!converter.initialize(configStr)) {
            return null;
        }
        converter.load(efi);
        return converter;
    }
    public static void main(String args[]) {
        ValueConverterFactory factory = new ValueConverterFactory();
        System.out.println("Index of 'A' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL A", null)).colIndex);
        System.out.println("Index of 'Z' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL Z", null)).colIndex);
        System.out.println("Index of 'AA' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL AA", null)).colIndex);
        System.out.println("Index of 'AZ' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL AZ", null)).colIndex);
        System.out.println("Index of 'BA' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL BA", null)).colIndex);
        System.out.println("Index of 'BZ' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL BZ", null)).colIndex);
        System.out.println("Index of 'ZA' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL ZA", null)).colIndex);
        System.out.println("Index of 'ZZ' is: " + ((ValueConverter.ColIndex) factory.newInstance("COL ZZ", null)).colIndex);

        /*final String regex = "\\#{3}(?![^\\[\\]]*])";
        Pattern pattern = Pattern.compile(regex);

        final String s = "a#b#c###d#[e#f#g#h#k]#l";
        String[] sArray = pattern.split(s);
        for(String item : sArray) {
            System.out.println(item);
        }*/
    }
}
