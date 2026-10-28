package triqueue.model;

import triqueue.storage.FileManager;

import java.util.Map;

public class HabalHabal extends Vehicle {
    public HabalHabal(String plateNumber) {
        super(plateNumber, "HabalHabal");
    }

    // HabalHabal: base fare is charged per trip, then shared evenly for discounts.
    @Override
    public double computeFare(String route, Map<String, Integer> passengerCounts) {
        return FileManager.getRoutePrice(route, false);
    }

    @Override
    public double computePassengerFareShare(String route, int totalPassengerCount) {
        if (totalPassengerCount <= 0) return 0;
        return FileManager.getRoutePrice(route, false) / totalPassengerCount;
    }
}
