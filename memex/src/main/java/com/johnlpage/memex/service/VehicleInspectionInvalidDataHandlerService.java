package com.johnlpage.memex.service;

import com.johnlpage.memex.model.VehicleInspection;
import com.johnlpage.memex.service.generic.InvalidDataHandlerService;
import jakarta.validation.ConstraintViolation;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VehicleInspectionInvalidDataHandlerService
    extends InvalidDataHandlerService<VehicleInspection> {
  private static final Logger LOG =
      LoggerFactory.getLogger(VehicleInspectionInvalidDataHandlerService.class);

  public boolean handleInvalidData(
      VehicleInspection document,
      Set<ConstraintViolation<VehicleInspection>> violations,
      Class<VehicleInspection> clazz) {

    LOG.warn(
        "Invalid Inspection data detected  in document, but no explicit handler provided, "
            + "discarding. in VehicleInspectionInvalidDataHandlerService.java : {} errors in document ",
        violations.size());
    return false;
  }
}
