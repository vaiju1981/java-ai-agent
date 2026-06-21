package dev.vaijanath.aiagent.learn;

/** Judges whether an answer addresses a task, producing a lesson when it doesn't. */
public interface Reflector {

    Reflection reflect(String task, String answer);
}
