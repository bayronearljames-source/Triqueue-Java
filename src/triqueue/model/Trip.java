package triqueue.model;

import triqueue.storage.FileManager;

import java.util.LinkedHashMap;
import java.util.Map;

public class Trip {
    private final String tripId;
    private final Driver driver;
    private final Vehicle vehicle;
    private final String route;
    private final Map<String, Integer> passengerCounts;
    private final String timestamp;
    private FareDetails fareDetails; // composition: a Trip owns its FareDetails

    public Trip(String tripId, Driver driver, String route,
                Map<String, Integer> passengerCounts, String timestamp) {
        this.tripId = tripId;
        this.driver = driver;
        this.vehicle = driver.getVehicle();
        this.route = route;
        this.passengerCounts = new LinkedHashMap<>(passengerCounts);
        this.timestamp = timestamp;
    }

    public double calculateFare() {
        int totalPassengerCount = getTotalPassengerCount();
        double base = vehicle.computeFare(route, passengerCounts); // polymorphic call
        double passengerFareShare = vehicle.computePassengerFareShare(route, totalPassengerCount);
        double discount = 0;
        for (Map.Entry<String, Integer> entry : passengerCounts.entrySet()) {
            double percent = FileManager.getDiscountPercent(entry.getKey());
            discount += passengerFareShare * entry.getValue() * percent / 100.0;
        }
        fareDetails = new FareDetails(base, discount);
        return fareDetails.getFinalFare();
    }

    public int getTotalPassengerCount() {
        int total = 0;
        for (int count : passengerCounts.values()) total += count;
        return total;
    }

    private String serializePassengerCounts() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Integer> entry : passengerCounts.entrySet()) {
            if (result.length() > 0) result.append(';');
            result.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return result.toString();
    }

    public FareDetails getFareDetails() { return fareDetails; }
    public String getDriverId() { return driver.getId(); }

    public String toFileString() {
        return tripId + "|" + driver.getId() + "|" + route + "|" + serializePassengerCounts()
                + "|" + getTotalPassengerCount()
                + "|" + String.format("%.2f", fareDetails.getBaseFare())
                + "|" + String.format("%.2f", fareDetails.getDiscountApplied())
                + "|" + String.format("%.2f", fareDetails.getFinalFare())
                + "|" + timestamp + "|" + vehicle.getVehicleType();
    }
}
