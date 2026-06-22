package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeuristicTokenizerTest {

    @Test
    void approximatesFourCharactersPerToken() {
        Tokenizer t = new HeuristicTokenizer();
        assertEquals(0, t.countTokens(null));
        assertEquals(0, t.countTokens(""));
        assertEquals(1, t.countTokens("a"));
        assertEquals(1, t.countTokens("abcd"));
        assertEquals(2, t.countTokens("abcde"));
        assertEquals(3, t.countTokens("123456789")); // 9 chars -> ceil(9/4) = 3
    }
}
