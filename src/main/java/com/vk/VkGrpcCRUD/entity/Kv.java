package com.vk.VkGrpcCRUD.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonFormat(shape=JsonFormat.Shape.ARRAY)
@JsonIgnoreProperties(ignoreUnknown=true)
public class Kv {
    @JsonProperty("key")
    private String key;

    @JsonProperty("value")
    private byte[] value;

    public Kv(String key, byte[] value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }
}
