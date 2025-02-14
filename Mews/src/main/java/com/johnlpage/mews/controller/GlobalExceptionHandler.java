package com.johnlpage.mews.controller;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ClientAbortException.class)
  public ResponseEntity<String> handleClientAbortException(ClientAbortException ex) {
    // Log the error or notify an error reporting service if necessary
    LOG.warn("ClientAbortException: {}", ex.getMessage(), ex);

    // Return a response entity, possibly with an error message and HTTP status
    // In many cases, it's sufficient to return an empty body with an internal server error
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Client aborted the connection");
  }
}
