package dev.vaijanath.aiagent.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void propagateRunsWithTheCapturedContextAndRestoresAfter() throws Exception {
        MDC.put("traceId", "caller");
        Callable<String> task = Mdc.propagate(() -> MDC.get("traceId"));

        // Simulate running on a worker thread that has its own (different) context.
        MDC.put("traceId", "worker");
        String during = task.call();

        assertEquals("caller", during, "the task ran with the captured caller context");
        assertEquals("worker", MDC.get("traceId"), "the worker's prior context is restored after");
    }

    @Test
    void propagateClearsWhenCallerHadNoContext() throws Exception {
        MDC.clear();
        Callable<String> task = Mdc.propagate(() -> MDC.get("traceId"));

        MDC.put("traceId", "worker");
        String during = task.call();

        assertNull(during, "no caller context means the task runs with a cleared MDC");
        assertEquals("worker", MDC.get("traceId"), "the worker's prior context is restored after");
    }
}
