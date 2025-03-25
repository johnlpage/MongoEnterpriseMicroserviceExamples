package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.repository.VehicleInspectionRepository;
import java.util.Date;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

// This is intended for downstream service that want to get reported on or perhaps
// augmented data

@Service
public class VehicleInspectionHistoryService {
  private final VehicleInspectionRepository repository;

  public VehicleInspectionHistoryService(VehicleInspectionRepository repository) {
    this.repository = repository;
  }

  public Stream<VehicleInspection> asOfDate(Long id, Date asOfDate) {
    return repository.GetRecordByIdAsOfDate(id, asOfDate, VehicleInspection.class);
  }
}
