package dev.vaijanath.aiagent.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    @Test
    void substitutesNamedPlaceholders() {
        PromptTemplate t = PromptTemplate.of("Hello {name}, you are a {role}.");
        assertEquals("Hello Ada, you are a engineer.", t.render(Map.of("name", "Ada", "role", "engineer")));
    }

    @Test
    void exposesDistinctVariableNamesInOrder() {
        assertEquals(Set.of("name", "role"), PromptTemplate.of("{name} the {role}, hi {name}").variables());
    }

    @Test
    void throwsOnAMissingValue() {
        PromptTemplate t = PromptTemplate.of("Hi {name}");
        assertThrows(IllegalArgumentException.class, () -> t.render(Map.of()));
    }

    @Test
    void treatsNonPlaceholderBracesAsLiteralText() {
        PromptTemplate t = PromptTemplate.of("a { b } c {x}");
        assertEquals(Set.of("x"), t.variables(), "spaced braces are not placeholders");
        assertEquals("a { b } c 1", t.render(Map.of("x", "1")));
    }
}
