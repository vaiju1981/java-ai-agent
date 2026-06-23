package dev.vaijanath.aiagent.fincopilot.me;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Data-subject and usage endpoints for the authenticated user (export, delete account, today's usage). */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserDataService data;
    private final UsageMeter usage;

    MeController(UserDataService data, UsageMeter usage) {
        this.data = data;
        this.usage = usage;
    }

    @GetMapping("/export")
    UserDataService.Export export(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return data.export(principal);
    }

    @GetMapping("/usage")
    Map<String, Integer> usage(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return Map.of("today", usage.today(principal));
    }

    @DeleteMapping
    ResponseEntity<Void> deleteAccount(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        data.delete(principal);
        return ResponseEntity.noContent().build();
    }
}
