package triqueue.core;

import triqueue.model.*;
import triqueue.storage.FileManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Dispatcher extends Person {
    private final String terminalName;

    public Dispatcher(String id, String name, String contactNumber, String terminalName) {
        super(id, name, contactNumber);
        this.terminalName = terminalName;
    }

    public String getTerminalName() { return terminalName; }

    public void addDriverToQueue(TerminalQueue queue, Driver driver) {
        if (queue.addDriver(driver)) {
            System.out.println(driver.getName() + " added to the queue.");
        }
    }

    public void callNextDriver(TerminalQueue queue) {
        Driver loading = queue.getLoadingDriver();
        if (loading != null) {
            System.out.println(loading.getName() + " is already NOW LOADING. Confirm departure before calling the next driver.");
            return;
        }
        Driver d = queue.getNextDriver();
        if (d == null) System.out.println("No drivers in the queue, or the queue could not be saved.");
        else System.out.println("NOW LOADING: " + d.getName() + " (" + d.getVehicle().getPlateNumber() + ")");
    }

    public void skipNextDriver(TerminalQueue queue) {
        Driver d = queue.skipDriver();
        if (d == null) System.out.println("No drivers in the queue.");
        else System.out.println(d.getName() + " was skipped and moved to the back.");
    }

    // Returns one row per driver: {id, name, trips, fares, boundary, net}
    public List<Object[]> buildReportRows(List<Driver> drivers, String date) {
        LocalDate reportDate;
        try {
            reportDate = LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Date must use yyyy-MM-dd format.", e);
        }
        return buildReportRows(drivers, reportDate, reportDate);
    }

    public List<Object[]> buildReportRows(List<Driver> drivers, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new IllegalArgumentException("Report date range is invalid.");
        Set<String> voidedTrips = FileManager.getVoidedTripIds();
        Map<String, String> statuses = FileManager.getTripStatuses();
        Map<String, String> effectiveTrips = FileManager.getEffectiveTrips();
        List<Object[]> rows = new ArrayList<>();
        for (Driver d : drivers) {
            int trips = 0;
            double fares = 0, boundary = 0;

            for (String line : effectiveTrips.values()) {
                String[] p = line.split("\\|");
                if (p.length >= 9 && !voidedTrips.contains(p[0])
                        && !"ARCHIVED".equals(statuses.get(p[0])) && p[1].equals(d.getId())) {
                    try {
                        LocalDate tripDate = LocalDate.parse(p[8].substring(0, Math.min(10, p[8].length())));
                        if (tripDate.isBefore(startDate) || tripDate.isAfter(endDate)) continue;
                        double amount = Double.parseDouble(p[7]);
                        if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
                        fares += amount;
                        trips++;
                    } catch (DateTimeParseException | NumberFormatException e) {
                        System.err.println("Skipping trip with invalid date or fare: " + line);
                    }
                }
            }
            for (String line : FileManager.readFile(FileManager.BOUNDARY)) {
                String[] p = line.split("\\|");
                if (p.length >= 4 && p[1].equals(d.getId())) {
                    try {
                        LocalDate boundaryDate = LocalDate.parse(p[2]);
                        if (boundaryDate.isBefore(startDate) || boundaryDate.isAfter(endDate)) continue;
                        double amount = Double.parseDouble(p[3]);
                        if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
                        boundary += amount;
                    } catch (DateTimeParseException | NumberFormatException e) {
                        System.err.println("Skipping boundary record with invalid date or amount: " + line);
                    }
                }
            }

            d.resetEarnings();
            d.addFare(fares);
            d.payBoundary(boundary);
            rows.add(new Object[]{d.getId(), d.getName(), trips, fares, boundary, d.getNetEarnings()});
        }
        return rows;
    }

    public void generateDailyReport(List<Driver> drivers, String date) {
        System.out.println("\n===== DAILY EARNINGS REPORT: " + date + " (" + terminalName + ") =====");
        System.out.printf("%-6s %-20s %6s %10s %10s %10s%n", "ID", "Driver", "Trips", "Fares", "Boundary", "Net");
        double totalFares = 0, totalBoundary = 0;
        for (Object[] r : buildReportRows(drivers, date)) {
            totalFares += (Double) r[3];
            totalBoundary += (Double) r[4];
            System.out.printf("%-6s %-20s %6d %10.2f %10.2f %10.2f%n", r[0], r[1], r[2], r[3], r[4], r[5]);
        }
        System.out.printf("%-27s %6s %10.2f %10.2f %10.2f%n", "TOTAL", "", totalFares, totalBoundary,
                totalFares - totalBoundary);
    }
}
