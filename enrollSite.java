import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class EnrollSite {

    
    //Database settings (override with environment variables if you want)
    static final String DB_URL  = env("DB_URL",  "jdbc:mariadb://localhost:3306/offers");
    static final String DB_USER = env("DB_USER", "offeruser");
    static final String DB_PASS = env("DB_PASS", "offerpass");

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null ? fallback : v;
    }

    static Connection db() throws Exception {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        // Create the table 
        //if it does not exist yet
        try (Connection c = db(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS enrollments (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    phone VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        System.out.println("Connected to database, table ready.");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", EnrollSite::homePage);
        server.createContext("/enroll", EnrollSite::enroll);
        server.createContext("/list", EnrollSite::list);
        server.start();
        System.out.println("Server running on port " + port);
    }

    // GET / -> the enrollment form
    static void homePage(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, "Not found");
            return;
        }
        send(ex, 200, """
            <html><body>
            <h1>Enroll for our next offer</h1>
            <form method="POST" action="/enroll">
              Name: <input name="name" required maxlength="100"><br><br>
              Phone number: <input name="phone" required maxlength="20"><br><br>
              <button type="submit">Enroll</button>
            </form>
            <p><a href="/list">See enrolled people</a></p>
            </body></html>
            """);
    }

    // POST /enroll -> save to the database
    static void enroll(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            send(ex, 405, "Use the form on the home page.");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = parseForm(body);
        String name = form.getOrDefault("name", "").trim();
        String phone = form.getOrDefault("phone", "").trim();

        if (name.isEmpty() || name.length() > 100 || !phone.matches("[0-9+ \\-]{7,20}")) {
            send(ex, 400, "<html><body><p>Please enter a name and a valid phone number.</p>"
                    + "<a href=\"/\">Go back</a></body></html>");
            return;
        }

        // PreparedStatement with ? placeholders protects against SQL injection
        try (Connection c = db();
             PreparedStatement ps = c.prepareStatement("INSERT INTO enrollments (name, phone) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "Database error. Please try again later.");
            return;
        }
        send(ex, 200, "<html><body><h2>Thank you, " + esc(name) + "!</h2>"
                + "<p>You are enrolled for our next offer.</p><a href=\"/\">Back</a></body></html>");
    }

    // GET /list -> show everyone saved in the database
    static void list(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("<html><body><h1>Enrolled people</h1>"
                + "<table border=\"1\" cellpadding=\"5\"><tr><th>ID</th><th>Name</th><th>Phone</th><th>Time</th></tr>");
        try (Connection c = db();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name, phone, created_at FROM enrollments ORDER BY id DESC")) {
            while (rs.next()) {
                sb.append("<tr><td>").append(rs.getInt("id"))
                  .append("</td><td>").append(esc(rs.getString("name")))
                  .append("</td><td>").append(esc(rs.getString("phone")))
                  .append("</td><td>").append(rs.getTimestamp("created_at"))
                  .append("</td></tr>");
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "Database error.");
            return;
        }
        sb.append("</table><p><a href=\"/\">Back</a></p></body></html>");
        send(ex, 200, sb.toString());
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    // Escape HTML so names like <script> cannot break the page
    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static void send(HttpExchange ex, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
