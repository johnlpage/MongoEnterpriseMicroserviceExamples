package com.johnlpage.memex.VehicleInspection.service;

import com.johnlpage.memex.VehicleInspection.model.VehicleInspection;
import com.johnlpage.memex.VehicleInspection.repository.VehicleInspectionRepository;
import org.bson.json.JsonObject;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

// This is intended for downstream service that want to get reported on or perhaps
// augmented data

@Service
public class VehicleInspectionDownstreamService {
    private final VehicleInspectionRepository repository;

    public VehicleInspectionDownstreamService(VehicleInspectionRepository repository) {
        this.repository = repository;
    }

    public Stream<JsonObject> nativeJsonExtractStream(String formatRequired) {
        return repository.nativeJsonExtract(formatRequired, VehicleInspection.class);
    }

    public Stream<VehicleInspection> jsonExtractStream() {
        return repository.findAllBy();
    }
}
