package medicircle;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Zero-framework REST backend using JDK's built-in HttpServer. */
public class WebServer {
    static final MediService svc = new MediService();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null) {
            try { port = Integer.parseInt(envPort); } catch (Exception ignored) {}
        }

        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Root path serves web/index.html
        s.createContext("/", ex -> {
            Path p = Paths.get("web/index.html");
            if (!Files.exists(p)) {
                // Try relative to scratch directory if launched elsewhere
                p = Paths.get("C:/Users/Muhammad Naseem/.gemini/antigravity/scratch/MediCircle/web/index.html");
            }
            if (!Files.exists(p)) {
                String notFound = "<h1>404: web/index.html not found</h1>";
                ex.sendResponseHeaders(404, notFound.length());
                ex.getResponseBody().write(notFound.getBytes());
                ex.close();
                return;
            }
            byte[] b = Files.readAllBytes(p);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });

        // REST API routes
        s.createContext("/api/search", ex -> handle(ex, q ->
                "[" + svc.search(q.get("q"), q.get("city")).stream().map(Medicine::toJson).collect(Collectors.joining(",")) + "]"));
        s.createContext("/api/register", ex -> handle(ex, q -> {
            Person p = "NGO".equalsIgnoreCase(q.get("role"))
                    ? new Ngo(0, q.get("name"), q.get("email"), q.get("phone"), q.get("city"))
                    : new Donor(0, q.get("name"), q.get("email"), q.get("phone"), q.get("city"));
            int newId = svc.register(p);
            return "{\"message\":\"Registered successfully!\",\"user\":" + p.toJson() + "}";
        }));
        s.createContext("/api/login", ex -> handle(ex, q -> {
            Person p = svc.login(q.get("query"));
            return "{\"message\":\"Welcome back, " + Medicine.esc(p.getName()) + "!\",\"user\":" + p.toJson() + "}";
        }));
        s.createContext("/api/my-donations", ex -> handle(ex, q -> {
            int donorId = Integer.parseInt(q.get("donorId"));
            return "[" + svc.myDonations(donorId).stream().map(Medicine::toJson).collect(Collectors.joining(",")) + "]";
        }));
        s.createContext("/api/my-reservations", ex -> handle(ex, q -> {
            int ngoId = Integer.parseInt(q.get("ngoId"));
            return "[" + svc.myReservations(ngoId).stream().map(MediService.ReservationDto::toJson).collect(Collectors.joining(",")) + "]";
        }));
        s.createContext("/api/deliver", ex -> handle(ex, q -> {
            svc.deliver(Integer.parseInt(q.get("requestId")));
            return msg("Marked as Received / Delivered! Impact recorded.");
        }));
        s.createContext("/api/donate", ex -> handle(ex, q -> {
            svc.donate(Integer.parseInt(q.get("donorId")), q.get("name"), Integer.parseInt(q.get("qty")), LocalDate.parse(q.get("expiry")));
            return msg("Medicine listed successfully! Thank you for saving lives.");
        }));
        s.createContext("/api/reserve", ex -> handle(ex, q -> {
            svc.reserve(Integer.parseInt(q.get("ngoId")), Integer.parseInt(q.get("medId")));
            return msg("Reserved successfully! Please contact donor to arrange handover.");
        }));
        s.createContext("/api/stats", ex -> handle(ex, q -> svc.stats().entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue()).collect(Collectors.joining(",", "{", "}"))));
        
        s.start();
        System.out.println("==================================================");
        System.out.println("💊 MediCircle Server Started!");
        System.out.println("🌐 Open in browser: http://localhost:" + port);
        System.out.println("==================================================");
    }

    interface Api { String run(Map<String, String> q) throws Exception; }

    static void handle(HttpExchange ex, Api api) throws IOException {
        int code = 200; String body;
        try {
            String raw = ex.getRequestMethod().equalsIgnoreCase("POST")
                    ? new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    : ex.getRequestURI().getRawQuery();
            body = api.run(parse(raw));
        } catch (Exception e) { code = 400; body = "{\"error\":\"" + Medicine.esc(e.getMessage()) + "\"}"; }
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b); ex.close();
    }

    static String msg(String m) { return "{\"message\":\"" + Medicine.esc(m) + "\"}"; }

    static Map<String, String> parse(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String kv : raw.split("&")) {
            String[] p = kv.split("=", 2);
            m.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }
}
