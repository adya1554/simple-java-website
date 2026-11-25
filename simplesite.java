import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.OutputStream;

public class Main {

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 8080), 0
        );

        server.createContext("/", exchange -> {

            String html = """
                <html>
                <head>
                    <title>My Java Website</title>
                </head>
                <body>
                    <h1>Welcome to My Java Website!</h1>
                    <h2>Hosted on Ubuntu using Nginx</h2>
                    <p>My first DevOps deployment!</p>
                </body>
                </html>
                """;

            exchange.getResponseHeaders().set(
                "Content-Type", "text/html; charset=UTF-8"
            );

            byte[] response = html.getBytes("UTF-8");

            exchange.sendResponseHeaders(200, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        server.start();

        System.out.println("Java website running on port 8080");
    }
}
