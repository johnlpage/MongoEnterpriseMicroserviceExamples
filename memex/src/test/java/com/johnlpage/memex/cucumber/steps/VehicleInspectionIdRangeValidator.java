package com.johnlpage.memex.cucumber.steps;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class VehicleInspectionIdRangeValidator {

    @Value("${memex.test.data.vehicleinspection-testid-range.start:10000}")
    private long rangeStart;

    @Value("${memex.test.data.vehicleinspection-testid-range.end:20000}")
    private long rangeEnd;

    public void validate(long id) {
        if (id < rangeStart || id > rangeEnd) {
            throw new AssertionError("Vehicle inspection testid: " + id + " outside of specified test range");
        }
    }

    public void validateRange(long startId, long endId) {
        if (endId < startId) {
            throw new AssertionError("Vehicle inspection end testid: " + endId + " is less than start testId: " + startId);
        }
        validate(startId);
        validate(endId);
    }
}
