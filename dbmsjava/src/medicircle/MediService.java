package medicircle;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data-access + business logic layer.
 * Supports dual-mode:
 *  1. Production: MySQL database with JDBC, PreparedStatements, and Triggers.
 *  2. Standalone / Demo: In-memory Relational DBMS Emulation (when MySQL is offline).
 */
public class MediService {
    private static final String URL  = env("DB_URL",  "jdbc:mysql://localhost:3306/medicircle?useSSL=false&allowPublicKeyRetrieval=true");
    private static final String USER = env("DB_USER", "root");
    private static final String PASS = env("DB_PASS", "root");

    private static String env(String k, String d) { String v = System.getenv(k); return v == null ? d : v; }

    private final boolean useMySQL;

    // In-memory DBMS simulation stores (used when MySQL is offline)
    private final Map<Integer, Person> memUsers = new ConcurrentHashMap<>();
    private final Map<Integer, MemMed> memMeds = new ConcurrentHashMap<>();
    private final Map<Integer, MemReq> memReqs = new ConcurrentHashMap<>();
    private final AtomicInteger userSeq = new AtomicInteger(0);
    private final AtomicInteger medSeq = new AtomicInteger(0);
    private final AtomicInteger reqSeq = new AtomicInteger(0);

    static class MemMed {
        int id, donorId, qty;
        String name, status = "AVAILABLE";
        LocalDate expiry;
    }
    static class MemReq {
        int id, ngoId, medId;
        String status = "PENDING";
    }

    public MediService() {
        boolean dbOk = false;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
                dbOk = true;
                System.out.println("[MediCircle] Connected successfully to MySQL database (" + URL + ")");
            }
        } catch (Throwable t) {
            dbOk = false;
        }
        this.useMySQL = dbOk;
        if (!useMySQL) {
            System.out.println("[MediCircle] MySQL not active - running with Built-in Relational In-Memory Store.");
            initMemData();
        }
    }

    private void initMemData() {
        // Seed default sample data matching schema.sql
        Donor d1 = new Donor(userSeq.incrementAndGet(), "Ravi Kumar", "ravi@mail.com", "9000000001", "Chennai");
        Donor d2 = new Donor(userSeq.incrementAndGet(), "Priya S", "priya@mail.com", "9000000002", "Bengaluru");
        Ngo n1 = new Ngo(userSeq.incrementAndGet(), "Helping Hands NGO", "hh@ngo.org", "9000000003", "Chennai");
        Ngo n2 = new Ngo(userSeq.incrementAndGet(), "CareFirst Trust", "cf@ngo.org", "9000000004", "Bengaluru");
        memUsers.put(d1.getId(), d1);
        memUsers.put(d2.getId(), d2);
        memUsers.put(n1.getId(), n1);
        memUsers.put(n2.getId(), n2);

        addMemMed(1, "Paracetamol 500mg", 20, LocalDate.now().plusDays(120));
        addMemMed(1, "Metformin 500mg", 30, LocalDate.now().plusDays(45));
        addMemMed(2, "Paracetamol 500mg", 10, LocalDate.now().plusDays(60));
        addMemMed(2, "Amoxicillin 250mg", 14, LocalDate.now().plusDays(200));
    }

    private void addMemMed(int donorId, String name, int qty, LocalDate exp) {
        MemMed m = new MemMed();
        m.id = medSeq.incrementAndGet();
        m.donorId = donorId;
        m.name = name;
        m.qty = qty;
        m.expiry = exp;
        memMeds.put(m.id, m);
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(URL, USER, PASS); }

    /* ---------- Users ---------- */
    public synchronized int register(Person p) throws MediException {
        if (useMySQL) {
            String sql = "INSERT INTO users(name,email,phone,city,role) VALUES(?,?,?,?,?)";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, p.getName()); ps.setString(2, p.getEmail());
                ps.setString(3, p.getPhone()); ps.setString(4, p.getCity()); ps.setString(5, p.getRole());
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys(); rs.next();
                p.setId(rs.getInt(1));
                return p.getId();
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            for (Person existing : memUsers.values()) {
                if (existing.getEmail().equalsIgnoreCase(p.getEmail())) {
                    throw new MediException("Email already registered (UNIQUE constraint)");
                }
            }
            int id = userSeq.incrementAndGet();
            p.setId(id);
            memUsers.put(id, p);
            return id;
        }
    }

    public Person login(String query) throws MediException {
        if (query == null || query.trim().isEmpty()) {
            throw new MediException("Please provide an Email or User ID to log in.");
        }
        String q = query.trim();
        int queryId = -1;
        try { queryId = Integer.parseInt(q); } catch (Exception ignored) {}

        if (useMySQL) {
            String sql = "SELECT * FROM users WHERE email=? OR id=?";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, q);
                ps.setInt(2, queryId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) throw new MediException("Account not found. Please register first.");
                return rs.getString("role").equals("NGO")
                        ? new Ngo(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("city"))
                        : new Donor(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("city"));
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            for (Person p : memUsers.values()) {
                if (p.getEmail().equalsIgnoreCase(q) || p.getId() == queryId) {
                    return p;
                }
            }
            throw new MediException("Account not found. Please register first.");
        }
    }

    public List<Medicine> myDonations(int donorId) throws MediException {
        List<Medicine> list = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT id, donor_id, name, quantity, expiry_date, status, DATEDIFF(expiry_date, CURDATE()) days_left " +
                         "FROM medicines WHERE donor_id=? ORDER BY id DESC";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, donorId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Medicine m = new Medicine();
                    m.id = rs.getInt("id");
                    m.donorId = rs.getInt("donor_id");
                    m.name = rs.getString("name");
                    m.quantity = rs.getInt("quantity");
                    m.expiry = rs.getDate("expiry_date").toLocalDate();
                    m.daysLeft = rs.getInt("days_left");
                    m.status = rs.getString("status");
                    list.add(m);
                }
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            LocalDate today = LocalDate.now();
            for (MemMed mm : memMeds.values()) {
                if (mm.donorId == donorId) {
                    Medicine m = new Medicine();
                    m.id = mm.id;
                    m.donorId = mm.donorId;
                    m.name = mm.name;
                    m.quantity = mm.qty;
                    m.expiry = mm.expiry;
                    m.daysLeft = (int) ChronoUnit.DAYS.between(today, mm.expiry);
                    m.status = mm.status;
                    list.add(m);
                }
            }
            list.sort(Comparator.comparingInt((Medicine m) -> m.id).reversed());
        }
        return list;
    }

    public static class ReservationDto {
        public int requestId, medicineId, quantity;
        public String medicineName, donorName, donorPhone, donorCity, status;
        public String toJson() {
            return String.format("{\"requestId\":%d,\"medicineId\":%d,\"medicineName\":\"%s\",\"quantity\":%d,\"donorName\":\"%s\",\"donorPhone\":\"%s\",\"donorCity\":\"%s\",\"status\":\"%s\"}",
                    requestId, medicineId, Medicine.esc(medicineName), quantity, Medicine.esc(donorName), Medicine.esc(donorPhone), Medicine.esc(donorCity), Medicine.esc(status));
        }
    }

    public List<ReservationDto> myReservations(int ngoId) throws MediException {
        List<ReservationDto> list = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT r.id req_id, r.medicine_id, m.name med_name, m.quantity, u.name donor_name, u.phone, u.city, r.status " +
                         "FROM requests r JOIN medicines m ON m.id=r.medicine_id JOIN users u ON u.id=m.donor_id " +
                         "WHERE r.ngo_id=? ORDER BY r.id DESC";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, ngoId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ReservationDto dto = new ReservationDto();
                    dto.requestId = rs.getInt("req_id");
                    dto.medicineId = rs.getInt("medicine_id");
                    dto.medicineName = rs.getString("med_name");
                    dto.quantity = rs.getInt("quantity");
                    dto.donorName = rs.getString("donor_name");
                    dto.donorPhone = rs.getString("phone");
                    dto.donorCity = rs.getString("city");
                    dto.status = rs.getString("status");
                    list.add(dto);
                }
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            for (MemReq r : memReqs.values()) {
                if (r.ngoId == ngoId) {
                    MemMed med = memMeds.get(r.medId);
                    Person donor = med != null ? memUsers.get(med.donorId) : null;
                    ReservationDto dto = new ReservationDto();
                    dto.requestId = r.id;
                    dto.medicineId = r.medId;
                    dto.medicineName = med != null ? med.name : "Unknown";
                    dto.quantity = med != null ? med.qty : 0;
                    dto.donorName = donor != null ? donor.getName() : "Unknown";
                    dto.donorPhone = donor != null ? donor.getPhone() : "-";
                    dto.donorCity = donor != null ? donor.getCity() : "-";
                    dto.status = r.status;
                    list.add(dto);
                }
            }
            list.sort(Comparator.comparingInt((ReservationDto d) -> d.requestId).reversed());
        }
        return list;
    }

    public List<String> listUsers() throws MediException {
        List<String> out = new ArrayList<>();
        if (useMySQL) {
            try (Connection c = conn(); ResultSet rs = c.createStatement().executeQuery("SELECT * FROM users ORDER BY id")) {
                while (rs.next()) {
                    Person p = rs.getString("role").equals("NGO")
                            ? new Ngo(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("city"))
                            : new Donor(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getString("city"));
                    out.add("#" + p.getId() + " " + p.describe());
                }
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            memUsers.values().stream().sorted(Comparator.comparingInt(Person::getId))
                    .forEach(p -> out.add("#" + p.getId() + " " + p.describe()));
        }
        return out;
    }

    /* ---------- Medicines ---------- */
    public synchronized void donate(int donorId, String name, int qty, LocalDate expiry) throws MediException {
        if (qty <= 0) throw new MediException("Quantity must be greater than 0");
        if (expiry.isBefore(LocalDate.now().plusDays(30))) {
            throw new MediException("Medicine must have at least 30 days before expiry (Trigger trg_med_check)");
        }

        if (useMySQL) {
            String sql = "INSERT INTO medicines(donor_id,name,quantity,expiry_date) VALUES(?,?,?,?)";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, donorId); ps.setString(2, name); ps.setInt(3, qty); ps.setDate(4, java.sql.Date.valueOf(expiry));
                ps.executeUpdate();
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            if (!memUsers.containsKey(donorId)) throw new MediException("Donor ID " + donorId + " not found (FK constraint)");
            addMemMed(donorId, name, qty, expiry);
        }
    }

    /**
     * Smart matching: same-city first, then soonest expiry (minimises waste).
     */
    public List<Medicine> search(String keyword, String city) throws MediException {
        List<Medicine> list = new ArrayList<>();
        String kw = (keyword == null ? "" : keyword).trim().toLowerCase();

        if (useMySQL) {
            String sql = "SELECT * FROM v_available WHERE name LIKE ?";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                c.prepareCall("{CALL mark_expired()}").execute();
                ps.setString(1, "%" + (keyword == null ? "" : keyword) + "%");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Medicine m = new Medicine();
                    m.id = rs.getInt("id"); m.donorId = rs.getInt("donor_id"); m.name = rs.getString("name"); m.quantity = rs.getInt("quantity");
                    m.expiry = rs.getDate("expiry_date").toLocalDate(); m.daysLeft = rs.getInt("days_left");
                    m.donor = rs.getString("donor"); m.city = rs.getString("city"); m.phone = rs.getString("phone");
                    list.add(m);
                }
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            LocalDate today = LocalDate.now();
            for (MemMed mm : memMeds.values()) {
                if ("AVAILABLE".equals(mm.status) && mm.expiry.isBefore(today)) {
                    mm.status = "EXPIRED"; // simulated mark_expired()
                }
                if ("AVAILABLE".equals(mm.status) && (kw.isEmpty() || mm.name.toLowerCase().contains(kw))) {
                    Person donor = memUsers.get(mm.donorId);
                    Medicine m = new Medicine();
                    m.id = mm.id;
                    m.donorId = mm.donorId;
                    m.name = mm.name;
                    m.quantity = mm.qty;
                    m.expiry = mm.expiry;
                    m.daysLeft = (int) ChronoUnit.DAYS.between(today, mm.expiry);
                    m.donor = donor != null ? donor.getName() : "Unknown";
                    m.city = donor != null ? donor.getCity() : "-";
                    m.phone = donor != null ? donor.getPhone() : "-";
                    list.add(m);
                }
            }
        }

        String home = city == null ? "" : city.trim();
        list.sort(Comparator.<Medicine, Boolean>comparing(m -> !m.city.equalsIgnoreCase(home))
                .thenComparing(Comparator.naturalOrder()));
        return list;
    }

    /** Reserve with transaction & row lock semantics */
    public synchronized void reserve(int ngoId, int medId) throws MediException {
        if (useMySQL) {
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                try (PreparedStatement chk = c.prepareStatement("SELECT status, donor_id FROM medicines WHERE id=? FOR UPDATE");
                     PreparedStatement role = c.prepareStatement("SELECT role FROM users WHERE id=?")) {
                    role.setInt(1, ngoId);
                    ResultSet r = role.executeQuery();
                    if (!r.next() || !r.getString(1).equals("NGO")) throw new MediException("Only verified NGOs can reserve");
                    chk.setInt(1, medId);
                    ResultSet rs = chk.executeQuery();
                    if (!rs.next() || !rs.getString(1).equals("AVAILABLE")) throw new MediException("Medicine not available");
                    if (rs.getInt("donor_id") == ngoId) throw new MediException("Conflict of Interest: A donor cannot reserve their own medicine");
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO requests(ngo_id,medicine_id) VALUES(?,?)")) {
                        ins.setInt(1, ngoId); ins.setInt(2, medId); ins.executeUpdate();
                    }
                    c.commit();
                } catch (MediException | SQLException e) { c.rollback(); throw e; }
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            Person ngo = memUsers.get(ngoId);
            if (ngo == null || !"NGO".equals(ngo.getRole())) throw new MediException("Only verified NGOs can reserve");
            MemMed mm = memMeds.get(medId);
            if (mm == null || !"AVAILABLE".equals(mm.status)) throw new MediException("Medicine is no longer available");
            if (mm.donorId == ngoId) throw new MediException("Conflict of Interest: A donor cannot reserve their own medicine");
            mm.status = "RESERVED";
            MemReq req = new MemReq();
            req.id = reqSeq.incrementAndGet();
            req.ngoId = ngoId;
            req.medId = medId;
            memReqs.put(req.id, req);
        }
    }

    public synchronized void deliver(int requestId) throws MediException {
        if (useMySQL) {
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE requests SET status='DELIVERED' WHERE id=? AND status='PENDING'")) {
                ps.setInt(1, requestId);
                if (ps.executeUpdate() == 0) throw new MediException("No pending request with that id");
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            MemReq r = memReqs.get(requestId);
            if (r == null || !"PENDING".equals(r.status)) throw new MediException("No pending request with that id");
            r.status = "DELIVERED";
            MemMed mm = memMeds.get(r.medId);
            if (mm != null) mm.status = "DELIVERED";
        }
    }

    public List<String> requests() throws MediException {
        List<String> out = new ArrayList<>();
        if (useMySQL) {
            String sql = "SELECT r.id, u.name ngo, m.name med, r.status FROM requests r " +
                         "JOIN users u ON u.id=r.ngo_id JOIN medicines m ON m.id=r.medicine_id ORDER BY r.id";
            try (Connection c = conn(); ResultSet rs = c.createStatement().executeQuery(sql)) {
                while (rs.next()) out.add(String.format("#%d %s -> %s [%s]", rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            for (MemReq r : memReqs.values()) {
                Person ngo = memUsers.get(r.ngoId);
                MemMed med = memMeds.get(r.medId);
                String ngoName = ngo != null ? ngo.getName() : "NGO#" + r.ngoId;
                String medName = med != null ? med.name : "Med#" + r.medId;
                out.add(String.format("#%d %s -> %s [%s]", r.id, ngoName, medName, r.status));
            }
        }
        return out;
    }

    public Map<String, Integer> stats() throws MediException {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (useMySQL) {
            String sql = "SELECT status, COUNT(*) FROM medicines GROUP BY status";
            try (Connection c = conn(); ResultSet rs = c.createStatement().executeQuery(sql)) {
                while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
                ResultSet r2 = c.createStatement().executeQuery("SELECT COALESCE(SUM(units_saved),0) FROM v_impact");
                r2.next(); m.put("UNITS_SAVED", r2.getInt(1));
            } catch (SQLException e) { throw new MediException(e.getMessage()); }
        } else {
            int avail = 0, res = 0, del = 0, saved = 0;
            for (MemMed mm : memMeds.values()) {
                if ("AVAILABLE".equals(mm.status)) avail++;
                else if ("RESERVED".equals(mm.status)) res++;
                else if ("DELIVERED".equals(mm.status)) { del++; saved += mm.qty; }
            }
            m.put("AVAILABLE", avail);
            m.put("RESERVED", res);
            m.put("DELIVERED", del);
            m.put("UNITS_SAVED", saved);
        }
        return m;
    }
}
