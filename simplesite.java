import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleSite {

    static final AtomicInteger visits = new AtomicInteger();
    static final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    static final String PAGE = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>My Java Website</title>
          <style>
            body { font-family: Arial, sans-serif; max-width: 600px; margin: 40px auto; padding: 0 16px; background: #f5f7fa; color: #222; }
            h1 { color: #1a5fb4; }
            .card { background: #fff; padding: 16px; border-radius: 8px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.1); }
            input, button { padding: 8px; font-size: 16px; }
            input { width: 65%; }
            button { background: #1a5fb4; color: #fff; border: 0; border-radius: 4px; cursor: pointer; }
            li { margin: 6px 0; }
          </style>
        </head>
        <body>
          <h1>Hello from Java!</h1>
          <div class="card">Visitors so far: <b id="visits">...</b></div>
          <div class="card">
            <h3>Guestbook</h3>
            <input id="msg" placeholder="Write a message">
            <button onclick="send()">Send</button>
            <ul id="list"></ul>
          </div>
          <script>
            async function load() {
              const r = await fetch('/api/messages');
              const d = await r.json();
              document.getElementById('visits').textContent = d.visits;
              document.getElementById('list').innerHTML =
                d.messages.map(m => '<li>' + m.replace(/</g, '&lt;') + '</li>').join('');
            }
            async function send() {
              const box = document.getElementById('msg');
              if (!box.value.trim()) return;
              await fetch('/api/messages', { method: 'POST', body: box.value });
              box.value = '';
              load();
            }
            load();
          </script>
        </body>
        </html>
        """;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", ex -> {
            if (!ex.getRequestURI().getPath().equals("/")) {
                respond(ex, 404, "text/plain", "Not found");
                return;
            }
            visits.incrementAndGet();
            respond(ex, 200, "text/html; charset=utf-8", PAGE);
        });

        server.createContext("/api/messages", ex -> {
            if (ex.getRequestMethod().equals("POST")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!body.isEmpty() && body.length() <= 200) {
                    messages.add(body);
                }
                respond(ex, 200, "application/json", "{\"ok\":true}");
            } else {
                respond(ex, 200, "application/json", toJson());
            }
        });

        server.start();
        System.out.println("Server running on http://0.0.0.0:" + port);
    }

    static String toJson() {
        StringBuilder sb = new StringBuilder("{\"visits\":" + visits.get() + ",\"messages\":[");
        synchronized (messages) {
            for (int i = 0; i < messages.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(messages.get(i)
                        .replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", " ").replace("\r", " ")).append("\"");
            }
        }
        return sb.append("]}").toString();
    }

    static void respond(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
