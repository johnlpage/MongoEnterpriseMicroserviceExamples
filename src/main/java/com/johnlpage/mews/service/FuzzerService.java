package com.johnlpage.mews.service;

import java.util.Map;

public abstract class FuzzerService <T> {

    /** Optional function to take a HashMap and modify it for testing */
    abstract void modifyDataForTest(T document);

}
