package triqueue.model;

public class FareDetails {
    private double baseFare;
    private double discountApplied;
    private double finalFare;

    public FareDetails(double baseFare, double discountApplied) {
        this.baseFare = baseFare;
        this.discountApplied = discountApplied;
        this.finalFare = baseFare - discountApplied;
    }

    public double getBaseFare() { return baseFare; }
    public double getDiscountApplied() { return discountApplied; }
    public double getFinalFare() { return finalFare; }

    public void updateFare(double newFare) {
        if (newFare < 0) {
            System.out.println("Fare cannot be negative.");
            return;
        }
        this.finalFare = newFare;
    }
}
