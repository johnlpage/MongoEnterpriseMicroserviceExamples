package com.johnlpage.memex.generics.service;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvalidDataHandlerService<T> {
    private static final Logger LOG = LoggerFactory.getLogger(InvalidDataHandlerService.class);

    // Override to decide what to do with invalid data
    public boolean handleInvalidData(
            T document, Set<ConstraintViolation<T>> violations, Class<T> clazz) {
        LOG.warn(
                "Invalid data detected ({} violations) in document, but no explicit handler provided, discarding.",
                violations.size());
        return false;
    }
}
