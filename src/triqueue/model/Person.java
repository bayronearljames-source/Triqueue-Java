package triqueue.model;

public class Person {
    private final String id;
    private String name;
    private String contactNumber;

    public Person(String id, String name, String contactNumber) {
        this.id = id;
        this.name = name;
        this.contactNumber = contactNumber;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getContactNumber() { return contactNumber; }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            System.out.println("Name cannot be empty.");
            return;
        }
        this.name = name.trim();
    }

    public void setContactNumber(String contactNumber) {
        if (contactNumber == null || !contactNumber.matches("\\d{7,11}")) {
            System.out.println("Contact number must be 7-11 digits.");
            return;
        }
        this.contactNumber = contactNumber;
    }
}
