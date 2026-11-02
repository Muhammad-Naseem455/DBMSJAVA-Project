package medicircle;

import java.time.LocalDate;

/* ===== OOP: Abstraction + Inheritance + Polymorphism + Encapsulation ===== */

abstract class Person {
    private int id;
    private final String name, email, phone, city;

    Person(int id, String name, String email, String phone, String city) {
        this.id = id; this.name = name; this.email = email; this.phone = phone; this.city = city;
    }
    abstract String getRole();                 // polymorphic
    String describe() { return "[" + getRole() + "] " + name + " (" + city + ")"; }

    int getId() { return id; }
    void setId(int id) { this.id = id; }
    String getName() { return name; }
    String getEmail() { return email; }
    String getPhone() { return phone; }
    String getCity() { return city; }

    String toJson() {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"city\":\"%s\",\"role\":\"%s\"}",
                id, Medicine.esc(name), Medicine.esc(email), Medicine.esc(phone), Medicine.esc(city), getRole());
    }
}

class Donor extends Person {
    Donor(int id, String n, String e, String p, String c) { super(id, n, e, p, c); }
    String getRole() { return "DONOR"; }
}

class Ngo extends Person {
    Ngo(int id, String n, String e, String p, String c) { super(id, n, e, p, c); }
    String getRole() { return "NGO"; }
    @Override String describe() { return super.describe() + " - verified receiver"; }
}

class Medicine implements Comparable<Medicine> {
    int id, donorId, quantity, daysLeft;
    String name, donor, city, phone, status = "AVAILABLE";
    LocalDate expiry;

    /** Natural ordering = sooner expiry first (use it before it is wasted). */
    public int compareTo(Medicine o) { return Integer.compare(daysLeft, o.daysLeft); }

    @Override public String toString() {
        return String.format("#%-3d %-22s qty:%-4d expires in %3d days [%s] | %s, %s (%s)",
                id, name, quantity, daysLeft, status, donor, city, phone);
    }

    String toJson() {
        return String.format("{\"id\":%d,\"donorId\":%d,\"name\":\"%s\",\"quantity\":%d,\"expiry\":\"%s\",\"daysLeft\":%d,\"donor\":\"%s\",\"city\":\"%s\",\"phone\":\"%s\",\"status\":\"%s\"}",
                id, donorId, esc(name), quantity, expiry, daysLeft, esc(donor), esc(city), esc(phone), esc(status));
    }
    static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}

/** Custom checked exception */
class MediException extends Exception {
    MediException(String msg) { super(msg); }
}
