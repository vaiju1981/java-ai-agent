package dev.vaijanath.aiagent.supervise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link SpeakerSelector} that cycles through the agents in registration order, so each takes turns
 * speaking. It never ends the chat on its own — the {@link GroupChatAgent}'s round budget bounds the
 * discussion. Deterministic and model-free; a good default.
 */
public final class RoundRobinSelector implements SpeakerSelector {

    @Override
    public String next(String task, List<Turn> transcript, Map<String, String> roster) {
        List<String> names = new ArrayList<>(roster.keySet());
        if (names.isEmpty()) {
            return null;
        }
        // The most recent turn by a roster member, if any.
        String previous = null;
        for (int i = transcript.size() - 1; i >= 0; i--) {
            if (roster.containsKey(transcript.get(i).speaker())) {
                previous = transcript.get(i).speaker();
                break;
            }
        }
        if (previous == null) {
            return names.get(0);
        }
        return names.get((names.indexOf(previous) + 1) % names.size());
    }
}
