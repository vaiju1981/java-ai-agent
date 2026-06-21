package dev.vaijanath.aiagent.learn;

/** A self-critique verdict: was the answer good enough, and if not, the lesson to apply. */
public record Reflection(boolean satisfactory, String lesson) {

    public static Reflection ok() {
        return new Reflection(true, "");
    }

    public static Reflection issue(String lesson) {
        return new Reflection(false, lesson);
    }
}
