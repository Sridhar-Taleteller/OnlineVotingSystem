import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class VotingServer {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
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
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Candidates
        server.createContext("/api/candidates", e -> {
            sendJson(e, 200,
                "{ \"candidates\": [" +
                "{\"id\":\"naruto\",\"name\":\"Naruto\",\"party\":\"Leaf\",\"image\":\"/images/naruto.jpg\"}," +
                "{\"id\":\"luffy\",\"name\":\"Luffy\",\"party\":\"Pirates\",\"image\":\"/images/luffy.jpg\"}," +
                "{\"id\":\"goku\",\"name\":\"Goku\",\"party\":\"Saiyan\",\"image\":\"/images/goku.jpg\"}" +
                "]}");
        });

        // Vote
        server.createContext("/api/vote", e -> {
            if (electionEnded) {
                sendJson(e, 403, "{\"error\":\"Voting stopped\"}");
                return;
            }

            String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> data = parse(body);

            String voter = data.get("voterId");
            String candidate = data.get("candidateId");

            if (VOTED.contains(voter)) {
                sendJson(e, 409, "{\"error\":\"Already voted\"}");
                return;
            }

            VOTED.add(voter);
            VOTES.get(candidate).add(voter);

            sendJson(e, 200, "{\"message\":\"Vote successful\"}");
        });

        // Check
        server.createContext("/api/check", e -> {
            String query = e.getRequestURI().getQuery();
            String voter = query.split("=")[1];

            if (electionEnded) {
                sendJson(e, 200, "{\"status\":\"stopped\"}");
                return;
            }

            boolean voted = VOTED.contains(voter);
            sendJson(e, 200, "{\"voted\":" + voted + "}");
        });

        // Admin results (FULL DATA)
        server.createContext("/api/results", e -> {
            String json = "{";

            for (String c : VOTES.keySet()) {
                json += "\"" + c + "\":[";
                List<String> voters = VOTES.get(c);

                for (int i = 0; i < voters.size(); i++) {
                    json += "\"" + voters.get(i) + "\"";
                    if (i < voters.size() - 1) json += ",";
                }
                json += "],";
            }

            json = json.substring(0, json.length() - 1) + "}";
            sendJson(e, 200, json);
        });

        // End election
        server.createContext("/api/end", e -> {
            electionEnded = true;
            sendJson(e, 200, "{\"message\":\"Election ended\"}");
        });

        // Static
        server.createContext("/", e -> {
            String path = e.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            Path file = WEB_ROOT.resolve("." + path);
            if (!Files.exists(file)) file = WEB_ROOT.resolve("index.html");

            byte[] data = Files.readAllBytes(file);
            e.sendResponseHeaders(200, data.length);
            e.getResponseBody().write(data);
            e.close();
        });

        server.start();
        System.out.println("Server running → http://localhost:8080");
    }

    static Map<String, String> parse(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] p = pair.split("=");
            map.put(p[0], p[1]);
        }
        return map;
    }

    static void sendJson(HttpExchange e, int code, String json) throws IOException {
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(code, json.length());
        e.getResponseBody().write(json.getBytes());
        e.close();
    }
}