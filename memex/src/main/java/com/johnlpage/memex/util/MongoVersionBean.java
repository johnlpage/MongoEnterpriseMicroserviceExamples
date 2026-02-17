package com.johnlpage.memex.util;

import lombok.Getter;

@Getter
public class MongoVersionBean {
    private final String version;
    int majorVersion;
    int minorversion;

    public MongoVersionBean(String version) {
        this.version = version;
        String[] parts = version.split("\\.");
        majorVersion = Integer.parseInt(parts[0]);
        minorversion = Integer.parseInt(parts[1]);
    }

}
