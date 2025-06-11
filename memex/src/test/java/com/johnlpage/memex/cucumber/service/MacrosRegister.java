package com.johnlpage.memex.cucumber.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MacrosRegister {

    private final Map<String, String> macros = new java.util.HashMap<>();

    public void registerMacro(String macroName, String macroValue) {
        macros.put(macroName, macroValue);
    }

    public String replaceMacros(String originalString) {
        String processedString = originalString;
        for (Map.Entry<String, String> entry : macros.entrySet()) {
            processedString = processedString.replace(entry.getKey(), entry.getValue());
        }

        return processedString;
    }
}
