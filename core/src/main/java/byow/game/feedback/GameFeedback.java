package byow.game.feedback;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmetic events, advanced only by the playing simulation clock. Never consumes
 * gameplay randomness or participates in saves, collisions or damage decisions.
 */
public final class GameFeedback {
    public enum Impact { DAMAGE, SHIELD, HEAL }
    public enum Channel { PICKUP, EFFECT, EXIT }
    public record Notice(Channel channel, String text, int ticks) {}
    private final List<Notice> notices = new ArrayList<>();
    private Impact impact;
    private String impactText;
    private int impactTicks;

    public Impact impact() { return impact; }
    public String impactText() { return impactText; }
    public int impactTicks() { return impactTicks; }
    public List<Notice> notices() { return List.copyOf(notices); }

    public void impact(Impact kind, String text) {
        impact = kind;
        impactText = text;
        impactTicks = 48;
    }

    public void notice(Channel channel, String text) {
        notices.removeIf(n -> n.channel() == channel);
        notices.add(new Notice(channel, text, channel == Channel.EXIT ? 300 : 240));
    }

    public void tick() {
        if (impactTicks > 0 && --impactTicks == 0) { impact = null; impactText = null; }
        notices.replaceAll(n -> new Notice(n.channel(), n.text(), n.ticks() - 1));
        notices.removeIf(n -> n.ticks() <= 0);
    }

    public void clear() {
        notices.clear();
        impact = null;
        impactText = null;
        impactTicks = 0;
    }
}
