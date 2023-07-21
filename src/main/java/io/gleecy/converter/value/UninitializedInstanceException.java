package io.gleecy.converter.value;

public class UninitializedInstanceException extends RuntimeException{
    public final String configStr;
    public UninitializedInstanceException(String configStr, String error) {
        super(error);
        this.configStr = configStr;
    }
}
