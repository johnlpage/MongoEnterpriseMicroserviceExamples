package com.johnlpage.memex.generics.service;

import lombok.Getter;

@Getter
public class DataLoadException extends Exception {
    private final long updates;
    private final long deletes;
    private final long inserts;

    public DataLoadException(long updates, long deletes, long inserts,
                             String message, Throwable cause) {
        super(message, cause);
        this.updates = updates;
        this.deletes = deletes;
        this.inserts = inserts;
    }

}
