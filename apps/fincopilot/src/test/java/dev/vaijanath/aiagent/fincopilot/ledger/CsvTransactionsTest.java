package dev.vaijanath.aiagent.fincopilot.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTransactionsTest {

    @Test
    void parsesRowsSkippingHeaderAndApplyingDefaults() {
        String csv =
                """
                date,amount,merchant,category,description
                2026-01-15,-45.99,Whole Foods,groceries,weekly shop
                2026-01-16,2500.00,Acme Payroll,income,salary
                2026-01-17,-12.50,Coffee Bar,,
                """;
        List<CsvTransactions.Row> rows = CsvTransactions.parse(csv);

        assertEquals(3, rows.size());
        assertEquals(LocalDate.parse("2026-01-15"), rows.get(0).date());
        assertEquals(new BigDecimal("-45.99"), rows.get(0).amount());
        assertEquals("groceries", rows.get(0).category());
        assertEquals("uncategorized", rows.get(2).category()); // blank category -> default
    }

    @Test
    void handlesQuotedFieldsContainingCommas() {
        List<CsvTransactions.Row> rows =
                CsvTransactions.parse("2026-02-01,-9.99,\"Books, Inc\",shopping,\"a, b\"");
        assertEquals(1, rows.size());
        assertEquals("Books, Inc", rows.get(0).merchant());
        assertEquals("a, b", rows.get(0).description());
    }

    @Test
    void rejectsMalformedRows() {
        assertThrows(IllegalArgumentException.class, () -> CsvTransactions.parse("2026-13-99,-1.00")); // bad date
        assertThrows(
                IllegalArgumentException.class, () -> CsvTransactions.parse("2026-01-01,not-a-number")); // bad amount
    }

    @Test
    void emptyInputYieldsNoRows() {
        assertEquals(0, CsvTransactions.parse("").size());
        assertEquals(0, CsvTransactions.parse(null).size());
    }

    @Test
    void rejectsTooManyRows() {
        String csv = "date,amount\n" + "2026-01-01,-1.00\n".repeat(10_001);
        assertThrows(IllegalArgumentException.class, () -> CsvTransactions.parse(csv));
    }
}
