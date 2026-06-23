package dev.vaijanath.aiagent.springboot.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class ModelEndpointHealthIndicatorTest {

    @Test
    void reportsUpWhenTheEndpointAnswers() throws Exception {
        try (ServerSocket server = serveOnce(200)) {
            Health health = new ModelEndpointHealthIndicator(urlOf(server)).health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("baseUrl");
        }
    }

    @Test
    void reportsDownWhenTheEndpointReturnsServerError() throws Exception {
        try (ServerSocket server = serveOnce(503)) {
            Health health = new ModelEndpointHealthIndicator(urlOf(server), Duration.ofSeconds(2)).health();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("status", 503);
        }
    }

    @Test
    void reportsDownWhenTheEndpointIsUnreachable() {
        // Port 1 on loopback is not bound -> a fast connection failure exercises the error path.
        Health health =
                new ModelEndpointHealthIndicator("http://127.0.0.1:1/", Duration.ofMillis(500)).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    private static String urlOf(ServerSocket server) {
        return "http://127.0.0.1:" + server.getLocalPort() + "/";
    }

    /** A one-shot HTTP server (no dependency on com.sun) that answers a single request with {@code status}. */
    private static ServerSocket serveOnce(int status) throws IOException {
        ServerSocket server = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        Thread worker = new Thread(() -> {
            try (Socket socket = server.accept()) {
                drainRequest(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 " + status + " X\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ignored) {
                // the test asserts on the client side; a closed socket here is harmless
            }
        });
        worker.setDaemon(true);
        worker.start();
        return server;
    }

    private static void drainRequest(InputStream in) throws IOException {
        byte[] buffer = new byte[2048];
        int total = 0;
        int read;
        while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) != -1) {
            total += read;
            if (new String(buffer, 0, total, StandardCharsets.US_ASCII).contains("\r\n\r\n")) {
                return;
            }
        }
    }
}
