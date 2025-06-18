package com.johnlpage.memex.cucumber.steps;

import com.johnlpage.memex.cucumber.service.MacrosRegister;
import io.cucumber.java.en.Given;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class TimeManagementSteps {

    @Autowired
    MacrosRegister macroRegister;

    @Given("I capture the current timestamp to {string} with {string} pattern")
    public void iCaptureTheCurrentTimestamp(String macroName, String datePattern) {
        ZonedDateTime capturedTimestamp = ZonedDateTime.now();
        macroRegister.registerMacro(macroName, capturedTimestamp.format(DateTimeFormatter.ofPattern(datePattern)));
    }

    @Given("I wait for {int} second(s)")
    public void iWaitForSeconds(int seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
