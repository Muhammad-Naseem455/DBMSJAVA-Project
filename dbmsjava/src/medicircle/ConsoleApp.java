package medicircle;

import java.time.LocalDate;
import java.util.Scanner;

/** Command-shell version. Run: java -cp "out;lib/*" medicircle.ConsoleApp */
public class ConsoleApp {
    public static void main(String[] args) {
        MediService svc = new MediService();
        Scanner sc = new Scanner(System.in);
        System.out.println("=== MediCircle : Share unused medicines, save lives ===");
        while (true) {
            System.out.println("\n1.Register  2.List users  3.Donate medicine  4.Smart search");
            System.out.println("5.Reserve (NGO)  6.Mark delivered  7.View requests  8.Impact stats  0.Exit");
            System.out.print("> ");
            String ch = sc.nextLine().trim();
            try {
                switch (ch) {
                    case "1" -> {
                        String role = ask(sc, "Role (DONOR/NGO)").toUpperCase();
                        String n = ask(sc, "Name"), e = ask(sc, "Email"), p = ask(sc, "Phone"), c = ask(sc, "City");
                        Person u = role.equals("NGO") ? new Ngo(0, n, e, p, c) : new Donor(0, n, e, p, c);
                        System.out.println("Registered with id " + svc.register(u));
                    }
                    case "2" -> svc.listUsers().forEach(System.out::println);
                    case "3" -> {
                        svc.donate(Integer.parseInt(ask(sc, "Donor id")), ask(sc, "Medicine name"),
                                Integer.parseInt(ask(sc, "Quantity")), LocalDate.parse(ask(sc, "Expiry (YYYY-MM-DD)")));
                        System.out.println("Thank you! Medicine listed.");
                    }
                    case "4" -> {
                        var list = svc.search(ask(sc, "Medicine keyword"), ask(sc, "Your city"));
                        if (list.isEmpty()) System.out.println("No matches.");
                        list.forEach(System.out::println);
                    }
                    case "5" -> {
                        svc.reserve(Integer.parseInt(ask(sc, "NGO id")), Integer.parseInt(ask(sc, "Medicine id")));
                        System.out.println("Reserved successfully.");
                    }
                    case "6" -> { svc.deliver(Integer.parseInt(ask(sc, "Request id"))); System.out.println("Marked delivered."); }
                    case "7" -> svc.requests().forEach(System.out::println);
                    case "8" -> svc.stats().forEach((k, v) -> System.out.println(k + " : " + v));
                    case "0" -> { System.out.println("Bye!"); return; }
                    default -> System.out.println("Invalid choice");
                }
            } catch (MediException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Invalid input: " + e.getMessage());
            }
        }
    }

    private static String ask(Scanner sc, String label) { System.out.print(label + ": "); return sc.nextLine().trim(); }
}
