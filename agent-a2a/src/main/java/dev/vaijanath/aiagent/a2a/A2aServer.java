package dev.vaijanath.aiagent.a2a;

import dev.vaijanath.aiagent.agent.Agent;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tiny, dependency-free HTTP server that exposes one {@link Agent} over A2A: {@code POST} a JSON
 * {@link A2aRequest} to run a turn, {@code GET} to fetch the {@link AgentCard}. Built on a raw
 * {@link ServerSocket} (no third-party server, no {@code com.sun.*} APIs); each connection is handled on
 * a virtual thread. Good for services, sidecars, tests, and demos — put it behind a gateway for auth/TLS
 * in production, and hand it a {@code Trust.govern(...)}-wrapped agent.
 *
 * <pre>{@code
 * A2aServer server = new A2aServer(governedAgent, "billing", "handles invoices", 8080);
 * // ... serving on virtual threads until ...
 * server.close();
 * }</pre>
 */
public final class A2aServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(A2aServer.class);

    private final A2aHandler handler;
    private final ServerSocket socket;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running = true;

    /** Serves on an ephemeral port (see {@link #port()}). */
    public A2aServer(Agent agent, String name, String description) throws IOException {
        this(agent, name, description, 0);
    }

    public A2aServer(Agent agent, String name, String description, int port) throws IOException {
        this.handler = new A2aHandler(agent, name, description);
        this.socket = new ServerSocket(port);
        Thread acceptor = new Thread(this::acceptLoop, "a2a-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** The bound port — useful when constructed with port {@code 0}. */
    public int port() {
        return socket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket conn = socket.accept();
                workers.submit(() -> serve(conn));
            } catch (IOException e) {
                if (running) {
                    log.warn("a2a accept failed", e);
                }
                return; // socket closed on shutdown
            }
        }
    }

    private void serve(Socket conn) {
        try (conn) {
            RawRequest request = RawRequest.read(conn.getInputStream());
            if (request == null) {
                return;
            }
            String body;
            int status;
            try {
                if ("GET".equals(request.method())) {
                    body = handler.cardJson();
                    status = 200;
                } else if ("POST".equals(request.method())) {
                    body = handler.handle(request.body());
                    status = 200;
                } else {
                    body = "{\"error\":\"method not allowed\"}";
                    status = 405;
                }
            } catch (IllegalArgumentException e) {
                body = "{\"error\":\"bad request\"}";
                status = 400;
            } catch (RuntimeException e) {
                log.warn("a2a handler error", e);
                body = "{\"error\":\"internal error\"}";
                status = 500;
            }
            writeResponse(conn.getOutputStream(), status, body);
        } catch (IOException e) {
            log.debug("a2a connection closed early", e);
        }
    }

    private static void writeResponse(OutputStream out, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        BufferedOutputStream sink = new BufferedOutputStream(out);
        sink.write(head.getBytes(StandardCharsets.US_ASCII));
        sink.write(bytes);
        sink.flush();
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 405 -> "Method Not Allowed";
            default -> "Internal Server Error";
        };
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            log.debug("a2a socket close failed", e);
        }
        workers.shutdownNow();
    }
}
