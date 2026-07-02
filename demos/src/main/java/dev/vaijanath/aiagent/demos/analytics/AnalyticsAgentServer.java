package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.model.ModelPort;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP app: the agent IS the app. Serves a chat UI and three endpoints — /api/ask (the
 * agent), /api/profile and /api/model (deterministic, no LLM). Point it at CSV file(s); the model
 * is chosen by config ({@link AnalyticsModels}). Replaces the old marketing dashboard server.
 */
final class AnalyticsAgentServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor;
    private final String profileJson;
    private final String modelJson;
    private final Agent agent;

    private AnalyticsAgentServer(HttpServer server, ExecutorService executor,
            String profileJson, String modelJson, Agent agent) {
        this.server = server;
        this.executor = executor;
        this.profileJson = profileJson;
        this.modelJson = modelJson;
        this.agent = agent;
    }

    static AnalyticsAgentServer start(CsvSource source, ModelPort model) {
        DatasetProfile profile = DatasetProfiler.profile(source);
        SemanticModel semantic = SemanticModel.fromProfile(profile);
        Agent agent = model == null ? null : AnalyticsAgent.create(source, semantic, model);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port()), 0);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AnalyticsAgentServer hosted = new AnalyticsAgentServer(
                    server, executor, profile.toJson(), semantic.toJson(), agent);
            server.createContext("/", hosted::route);
            server.setExecutor(executor);
            server.start();
            return hosted;
        } catch (IOException e) {
            throw new IllegalStateException("failed to start analytics agent server", e);
        }
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                respond(exchange, 200, "text/html; charset=utf-8", HTML);
            } else if ("GET".equals(method) && "/api/profile".equals(path)) {
                respond(exchange, 200, "application/json; charset=utf-8", profileJson);
            } else if ("GET".equals(method) && "/api/model".equals(path)) {
                respond(exchange, 200, "application/json; charset=utf-8", modelJson);
            } else if ("POST".equals(method) && "/api/ask".equals(path)) {
                handleAsk(exchange);
            } else {
                respond(exchange, 404, "text/plain; charset=utf-8", "not found");
            }
        } catch (RuntimeException e) {
            respond(exchange, 500, "application/json; charset=utf-8",
                    toJson(Map.of("error", String.valueOf(e.getMessage()))));
        }
    }

    private void handleAsk(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> request = body.isBlank()
                ? Map.of()
                : MAPPER.readValue(body, new TypeReference<>() {});
        String question = String.valueOf(request.getOrDefault("question", "")).strip();
        if (question.isEmpty()) {
            respond(exchange, 400, "application/json; charset=utf-8",
                    toJson(Map.of("error", "question is required")));
            return;
        }
        if (agent == null) {
            respond(exchange, 400, "application/json; charset=utf-8", toJson(Map.of(
                    "error", "no model configured; set ANALYTICS_MODEL_PROVIDER and ANALYTICS_MODEL")));
            return;
        }
        // A stable session id keeps multi-turn context (follow-up questions). One default session
        // for the single-user demo; a client may pass its own "session" to isolate conversations.
        String session = String.valueOf(request.getOrDefault("session", "web")).strip();
        AgentResponse response = agent.run(new AgentRequest(
                question, RequestContext.session(session.isEmpty() ? "web" : session)));
        respond(exchange, 200, "application/json; charset=utf-8",
                toJson(Map.of("answer", response.output(), "blocked", response.blocked())));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize response", e);
        }
    }

    private static int port() {
        String value = System.getenv("ANALYTICS_PORT");
        try {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ANALYTICS_PORT must be a number", e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: AnalyticsAgentServer <table=file.csv> [<table=file.csv> ...]  (or bare file.csv paths)");
            return;
        }
        Map<String, Path> csvs = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            String name = eq > 0 ? arg.substring(0, eq) : Path.of(arg).getFileName().toString().replaceFirst("\\.csv$", "");
            String file = eq > 0 ? arg.substring(eq + 1) : arg;
            csvs.put(name, Path.of(file));
        }
        CsvSource source = CsvSource.of(Path.of("workspace", "analytics.duckdb"), csvs);
        ModelPort model;
        try {
            model = AnalyticsModels.fromEnv();
        } catch (RuntimeException e) {
            model = null;
            System.out.println("[warn] " + e.getMessage() + " — /api/ask disabled, profile still served.");
        }
        AnalyticsAgentServer hosted = start(source, model);
        Runtime.getRuntime().addShutdownHook(new Thread(hosted::close));
        System.out.println("Analytics agent: " + hosted.uri());
        new CountDownLatch(1).await();
    }

    private static final String HTML = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"><title>Analytics Agent</title>
            <style>body{font:15px -apple-system,system-ui,sans-serif;max-width:760px;margin:40px auto;padding:0 16px;color:#1a1a1a}
            textarea{width:100%;min-height:72px;padding:10px;border:1px solid #ccc;border-radius:8px;font:inherit}
            button{margin-top:8px;padding:9px 16px;border:0;border-radius:8px;background:#0f766e;color:#fff;font-weight:600;cursor:pointer}
            #answer{white-space:pre-wrap;margin-top:16px;padding:14px;background:#f5f5f4;border-radius:8px;min-height:24px}
            .muted{color:#666;font-size:13px}</style></head><body>
            <h2>Analytics Agent</h2>
            <p class="muted">Ask a question about the loaded dataset in plain English.</p>
            <textarea id="q" placeholder="e.g. What is the total coinIn by clubLevel?"></textarea>
            <button id="ask">Ask</button>
            <div id="answer"></div>
            <script>
            const q=document.getElementById('q'),a=document.getElementById('answer'),b=document.getElementById('ask');
            b.onclick=async()=>{const question=q.value.trim();if(!question)return;a.textContent='Thinking…';
            try{const r=await fetch('/api/ask',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question})});
            const j=await r.json();a.textContent=j.answer||j.error||'(no answer)';}catch(e){a.textContent='Error: '+e;}};
            </script></body></html>
            """;
}
