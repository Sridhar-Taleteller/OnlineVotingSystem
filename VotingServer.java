import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class VotingServer {

    // ✅ FIXED PORT (Render compatible)
    private static final int PORT = Integer.parseInt(
        System.getenv().getOrDefault("PORT", "8080")
    );

    private static final Path WEB_ROOT = Paths.get("web").toAbsolutePath();

    private static final Map<String, List<String>> VOTES = new ConcurrentHashMap<>();
    private static final Set<String> VOTED = ConcurrentHashMap.newKeySet();
    private static boolean electionEnded = false;

    static {
        VOTES.put("naruto", new ArrayList<>());
        VOTES.put("luffy", new ArrayList<>());
        VOTES.put("goku", new ArrayList<>());
    }

    public static void main(String[] args) throws Exception {

        // ✅ FIXED BINDING
        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", PORT), 0
        );

        // =========================
        // 🎯 CANDIDATES API
        // =========================
        server.createContext("/api/candidates", e -> {
            sendJson(e, 200,
                "{ \"candidates\": [" +
                "{\"id\":\"naruto\",\"name\":\"Naruto\",\"party\":\"Leaf\",\"image\":\"/images/naruto.jpg\"}," +
                "{\"id\":\"luffy\",\"name\":\"Luffy\",\"party\":\"Pirates\",\"image\":\"/images/luffy.jpg\"}," +
                "{\"id\":\"goku\",\"name\":\"Goku\",\"party\":\"Saiyan\",\"image\":\"/images/goku.jpg\"}" +
                "]}");
        });

        // =========================
        // 🗳️ VOTE API
        // =========================
        server.createContext("/api/vote", e -> {

            if (electionEnded) {
                sendJson(e, 403, "{\"error\":\"Voting stopped\"}");
                return;
            }

            String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parse(body);

            String voter = data.getOrDefault("voterId", "");
            String candidate = data.getOrDefault("candidateId", "");

            if (voter.isEmpty() || candidate.isEmpty()) {
                sendJson(e, 400, "{\"error\":\"Invalid data\"}");
                return;
            }

            if (VOTED.contains(voter)) {
                sendJson(e, 409, "{\"error\":\"Already voted\"}");
                return;
            }

            VOTED.add(voter);
            VOTES.get(candidate).add(voter);

            sendJson(e, 200, "{\"message\":\"Vote successful\"}");
        });

        // =========================
        // 🔍 CHECK API
        // =========================
        server.createContext("/api/check", e -> {

            String query = e.getRequestURI().getQuery();
            String voter = "";

            if (query != null && query.contains("=")) {
                voter = query.split("=")[1];
            }

            if (electionEnded) {
                sendJson(e, 200, "{\"status\":\"stopped\"}");
                return;
            }

            boolean voted = VOTED.contains(voter);
            sendJson(e, 200, "{\"voted\":" + voted + "}");
        });

        // =========================
        // 📊 RESULTS API
        // =========================
        server.createContext("/api/results", e -> {

            StringBuilder json = new StringBuilder("{");

            for (String c : VOTES.keySet()) {
                json.append("\"").append(c).append("\":[");
                List<String> voters = VOTES.get(c);

                for (int i = 0; i < voters.size(); i++) {
                    json.append("\"").append(voters.get(i)).append("\"");
                    if (i < voters.size() - 1) json.append(",");
                }

                json.append("],");
            }

            json.deleteCharAt(json.length() - 1);
            json.append("}");

            sendJson(e, 200, json.toString());
        });

        // =========================
        // 🛑 END ELECTION
        // =========================
        server.createContext("/api/end", e -> {
            electionEnded = true;
            sendJson(e, 200, "{\"message\":\"Election ended\"}");
        });

        // =========================
        // 🌐 STATIC FILES (FIXED)
        // =========================
        server.createContext("/", e -> {

            String path = e.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            Path file = WEB_ROOT.resolve(path.substring(1));

            if (!Files.exists(file)) {
                file = WEB_ROOT.resolve("index.html");
            }

            byte[] data = Files.readAllBytes(file);

            String contentType = "text/html";
            if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".png")) contentType = "image/png";

            e.getResponseHeaders().set("Content-Type", contentType);
            e.sendResponseHeaders(200, data.length);
            e.getResponseBody().write(data);
            e.close();
        });

        // ✅ START SERVER (CRITICAL)
        server.start();

        System.out.println("Server started on port " + PORT);
    }

    // =========================
    // 🔧 PARSER
    // =========================
    static Map<String, String> parse(String body) {
        Map<String, String> map = new HashMap<>();

        for (String pair : body.split("&")) {
            String[] p = pair.split("=");
            if (p.length == 2) {
                map.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(p[1], StandardCharsets.UTF_8));
            }
        }

        return map;
    }

    // =========================
    // 📤 JSON RESPONSE
    // =========================
    static void sendJson(HttpExchange e, int code, String json) throws IOException {
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(code, json.getBytes().length);
        e.getResponseBody().write(json.getBytes());
        e.close();
    }
}
