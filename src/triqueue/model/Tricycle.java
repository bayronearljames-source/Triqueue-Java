package triqueue.model;

import triqueue.storage.FileManager;

import java.util.Map;

public class Tricycle extends Vehicle {
    public Tricycle(String plateNumber) {
        super(plateNumber, "Tricycle");
    }

    // Tricycle: base fare is charged per passenger.
    @Override
    public double computeFare(String route, Map<String, Integer> passengerCounts) {
        double perPassenger = FileManager.getRoutePrice(route, true);
        int totalPassengerCount = 0;
        for (int count : passengerCounts.values()) totalPassengerCount += count;
        return perPassenger * totalPassengerCount;
    }

    @Override
    public double computePassengerFareShare(String route, int totalPassengerCount) {
        return FileManager.getRoutePrice(route, true);
    }
}
