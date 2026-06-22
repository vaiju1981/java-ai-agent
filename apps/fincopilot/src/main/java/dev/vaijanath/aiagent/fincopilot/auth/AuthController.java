package dev.vaijanath.aiagent.fincopilot.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public auth endpoints: signup and login issue a session token; logout invalidates it. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    ResponseEntity<TokenResponse> signup(@RequestBody(required = false) Credentials body) {
        validate(body);
        return auth.signup(body.email(), body.password())
                .map(token -> ResponseEntity.status(HttpStatus.CREATED).body(new TokenResponse(token)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "email already registered"));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@RequestBody(required = false) Credentials body) {
        validate(body);
        return auth.login(body.email(), body.password())
                .map(token -> ResponseEntity.ok(new TokenResponse(token)))
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password"));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(SessionAuthenticationFilter.bearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

    private static void validate(Credentials body) {
        if (body == null
                || body.email() == null
                || !body.email().contains("@")
                || body.password() == null
                || body.password().length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "a valid email and a password of at least " + MIN_PASSWORD_LENGTH + " characters are required");
        }
    }

    record Credentials(String email, String password) {}

    record TokenResponse(String token) {}
}
