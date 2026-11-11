package dev.che1s.betalands.service;

import java.util.HashMap;
import java.util.Map;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.util.Text;

/**
 * Tracks the last land occupied by each player so boundary messages are only
 * sent when the effective main/subclaim/wilderness state changes.
 */
public final class TerritoryTracker {
    private final Map<String, Land> territories = new HashMap<String, Land>();

    public void remember(String playerName, Land land) {
        territories.put(Text.key(playerName), land);
    }

    public Change update(String playerName, Land land) {
        String key = Text.key(playerName);
        if (!territories.containsKey(key)) {
            territories.put(key, land);
            return new Change(false, null, land);
        }
        Land previous = territories.get(key);
        if (sameTerritory(previous, land)) {
            return new Change(false, previous, land);
        }
        territories.put(key, land);
        return new Change(true, previous, land);
    }

    public void forget(String playerName) {
        territories.remove(Text.key(playerName));
    }

    public Land get(String playerName) {
        return territories.get(Text.key(playerName));
    }

    static boolean sameTerritory(Land first, Land second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.getId().equalsIgnoreCase(second.getId())
                && first.getWorld().equalsIgnoreCase(second.getWorld());
    }

    public static final class Change {
        private final boolean changed;
        private final Land previous;
        private final Land current;

        private Change(boolean changed, Land previous, Land current) {
            this.changed = changed;
            this.previous = previous;
            this.current = current;
        }

        public boolean changed() {
            return changed;
        }

        public Land getPrevious() {
            return previous;
        }

        public Land getCurrent() {
            return current;
        }
    }
}
