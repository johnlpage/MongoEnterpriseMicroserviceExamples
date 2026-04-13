package com.johnlpage.memex.VehicleInspection.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class VehicleInspectionTest {

    @Test
    void toString_noStackOverflow_whenVehicleHasInspections() {
        VehicleInspection inspection = new VehicleInspection();
        inspection.setTestid(1L);

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleid(100L);
        vehicle.setInspections(List.of(inspection));

        inspection.setVehicle(vehicle);

        assertDoesNotThrow(() -> inspection.toString());
    }

    @Test
    void equals_noStackOverflow_whenVehicleHasInspections() {
        VehicleInspection a = new VehicleInspection();
        a.setTestid(1L);
        VehicleInspection b = new VehicleInspection();
        b.setTestid(1L);

        // Separate Vehicle instances so equals() can't short-circuit on reference equality
        Vehicle vehicleA = new Vehicle();
        vehicleA.setVehicleid(100L);
        vehicleA.setInspections(List.of(a));

        Vehicle vehicleB = new Vehicle();
        vehicleB.setVehicleid(100L);
        vehicleB.setInspections(List.of(b));

        a.setVehicle(vehicleA);
        b.setVehicle(vehicleB);

        assertDoesNotThrow(() -> a.equals(b));
    }

    @Test
    void hashCode_noStackOverflow_whenVehicleHasInspections() {
        VehicleInspection inspection = new VehicleInspection();
        inspection.setTestid(1L);

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleid(100L);
        vehicle.setInspections(List.of(inspection));

        inspection.setVehicle(vehicle);

        assertDoesNotThrow(() -> inspection.hashCode());
    }
}
