package dev.vaijanath.aiagent.fincopilot.ledger;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accounts and transactions for the authenticated user — manual entry and CSV import, the data the
 * Analyst reasons over. Everything is scoped to the session principal; writes require the account to
 * belong to the caller.
 */
@RestController
@RequestMapping("/api")
class LedgerController {

    private static final int MAX_CSV_CHARS = 1_000_000;

    private final AccountStore accounts;
    private final TransactionStore transactions;

    LedgerController(AccountStore accounts, TransactionStore transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @PostMapping("/accounts")
    ResponseEntity<Account> createAccount(
            @RequestBody(required = false) AccountRequest body,
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        Account created = accounts.create(
                principal, body.name().strip(), blankTo(body.type(), "checking"), blankTo(body.currency(), "USD"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/accounts")
    List<Account> listAccounts(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return accounts.listByUser(principal);
    }

    @PostMapping("/transactions")
    ResponseEntity<Transaction> addTransaction(
            @RequestBody(required = false) TransactionRequest body,
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        if (body == null || body.accountId() == null || body.date() == null || body.amount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId, date, and amount are required");
        }
        requireOwnedAccount(principal, body.accountId());
        Transaction txn = new Transaction(
                UUID.randomUUID().toString(),
                principal,
                body.accountId(),
                body.date(),
                body.amount(),
                orEmpty(body.merchant()),
                blankTo(body.category(), "uncategorized"),
                orEmpty(body.description()));
        transactions.add(txn);
        return ResponseEntity.status(HttpStatus.CREATED).body(txn);
    }

    @PostMapping("/transactions/import")
    Map<String, Integer> importCsv(
            @RequestBody(required = false) ImportRequest body,
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        if (body == null || body.accountId() == null || body.csv() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId and csv are required");
        }
        if (body.csv().length() > MAX_CSV_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "csv exceeds " + MAX_CSV_CHARS + " characters");
        }
        requireOwnedAccount(principal, body.accountId());
        List<CsvTransactions.Row> rows;
        try {
            rows = CsvTransactions.parse(body.csv());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV parse error: " + e.getMessage());
        }
        List<Transaction> txns = rows.stream()
                .map(r -> new Transaction(
                        UUID.randomUUID().toString(),
                        principal,
                        body.accountId(),
                        r.date(),
                        r.amount(),
                        r.merchant(),
                        r.category(),
                        r.description()))
                .toList();
        return Map.of("imported", transactions.addAll(txns));
    }

    @GetMapping("/transactions")
    List<Transaction> listTransactions(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return transactions.listByUser(principal, optionalDate(from, "from"), optionalDate(to, "to"));
    }

    private void requireOwnedAccount(String principal, String accountId) {
        if (!accounts.existsForUser(principal, accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown account");
        }
    }

    private static LocalDate optionalDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be an ISO date (yyyy-MM-dd)");
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    record AccountRequest(String name, String type, String currency) {}

    record TransactionRequest(
            String accountId, LocalDate date, BigDecimal amount, String merchant, String category, String description) {}

    record ImportRequest(String accountId, String csv) {}
}
