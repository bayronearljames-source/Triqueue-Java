package triqueue.model;

import triqueue.storage.FileManager;

import java.util.Map;

public abstract class Vehicle {
    private final String plateNumber;
private final String vehicleType;

    public Vehicle(String plateNumber, String vehicleType) {
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
    }

    public String getPlateNumber() { return plateNumber; }
    public String getVehicleType() { return vehicleType; }

    // Each vehicle type computes its base fare and the share eligible for discounts.
    public abstract double computeFare(String route, Map<String, Integer> passengerCounts);
    public abstract double computePassengerFareShare(String route, int totalPassengerCount);
}
