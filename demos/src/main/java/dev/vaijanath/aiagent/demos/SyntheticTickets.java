package dev.vaijanath.aiagent.demos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generates a deterministic batch of synthetic support tickets across a few themes. */
final class SyntheticTickets {

    private SyntheticTickets() {}

    private static final String[][] TEMPLATES = {
        {"I was charged twice this month", "My card shows two subscription charges for the same period."},
        {"Refund still not received", "I was promised a refund two weeks ago and nothing has arrived."},
        {"App crashes on login", "Every time I tap sign in, the app closes immediately on my phone."},
        {"Page returns a 500 error", "The checkout page shows 'Internal Server Error' when I try to pay."},
        {"How do I reset my password?", "I forgot my password and can't find the reset link."},
        {"Where can I export my data?", "I'd like to download all my data as a CSV — is that possible?"},
        {"Locked out of my account", "After a few wrong tries my account got locked. Please help."},
        {"Please add a dark mode", "It would be great if the app had a dark theme for night use."},
        {"Wrong search results", "Searching for 'invoice' returns unrelated items."},
        {"Update my email address", "I changed jobs and need to update the email on my account."},
        {"Urgent: data appears deleted", "All my saved reports are gone after the latest update!"},
        {"Can you support SSO?", "Our company would like to log in via single sign-on."},
    };

    static List<Ticket> generate(int n) {
        Random rnd = new Random(13); // deterministic
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] t = TEMPLATES[rnd.nextInt(TEMPLATES.length)];
            tickets.add(new Ticket(i + 1, t[0], t[1]));
        }
        return tickets;
    }
}
