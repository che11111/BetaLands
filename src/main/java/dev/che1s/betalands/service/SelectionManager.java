package dev.che1s.betalands.service;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;

import dev.che1s.betalands.util.Text;

public final class SelectionManager {
    private final Map<String, Selection> selections = new HashMap<String, Selection>();

    public Selection get(String playerName) {
        return selections.get(Text.key(playerName));
    }

    public void setFirst(String playerName, Location location) {
        Selection selection = get(playerName);
        if (selection == null) {
            selection = new Selection();
            selections.put(Text.key(playerName), selection);
        }
        selection.first = clone(location);
    }

    public void setSecond(String playerName, Location location) {
        Selection selection = get(playerName);
        if (selection == null) {
            selection = new Selection();
            selections.put(Text.key(playerName), selection);
        }
        selection.second = clone(location);
    }

    public void clear(String playerName) {
        selections.remove(Text.key(playerName));
    }

    private Location clone(Location location) {
        return new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public static final class Selection {
        private Location first;
        private Location second;

        public Location getFirst() {
            return first;
        }

        public Location getSecond() {
            return second;
        }

        public boolean isComplete() {
            return first != null && second != null && first.getWorld() != null && second.getWorld() != null;
        }

        public boolean sameWorld() {
            return isComplete() && first.getWorld().getName().equals(second.getWorld().getName());
        }

        public String getWorldName() {
            return first == null || first.getWorld() == null ? null : first.getWorld().getName();
        }
    }
}
