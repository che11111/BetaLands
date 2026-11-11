package dev.che1s.betalands.service;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.util.Text;

/**
 * Stores pending player-to-player land invitations for the current server session.
 */
public final class LandInvitationService {
    private final Map<String, Invitation> invitations =
            new LinkedHashMap<String, Invitation>();

    public Invitation create(String inviterName, String targetName, Land land) {
        Invitation invitation = new Invitation(
                Text.key(targetName), targetName, land.getId(),
                land.getDisplayName(), inviterName, System.currentTimeMillis());
        invitations.put(key(targetName, land.getId()), invitation);
        return invitation;
    }

    public Invitation get(String playerName, Land land) {
        if (playerName == null || land == null) {
            return null;
        }
        return invitations.get(key(playerName, land.getId()));
    }

    public Invitation remove(String playerName, Land land) {
        if (playerName == null || land == null) {
            return null;
        }
        return invitations.remove(key(playerName, land.getId()));
    }

    public void removeForLand(String landId) {
        String suffix = "|" + Text.key(landId);
        java.util.Iterator<String> iterator = invitations.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().endsWith(suffix)) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        invitations.clear();
    }

    private String key(String playerName, String landId) {
        return Text.key(playerName) + "|" + Text.key(landId);
    }

    public static final class Invitation {
        private final String playerKey;
        private final String playerName;
        private final String landId;
        private final String landName;
        private final String inviterName;
        private final long createdAt;

        private Invitation(String playerKey, String playerName, String landId,
                           String landName, String inviterName, long createdAt) {
            this.playerKey = playerKey;
            this.playerName = playerName;
            this.landId = landId;
            this.landName = landName;
            this.inviterName = inviterName;
            this.createdAt = createdAt;
        }

        public String getPlayerKey() {
            return playerKey;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getLandId() {
            return landId;
        }

        public String getLandName() {
            return landName;
        }

        public String getInviterName() {
            return inviterName;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }
}
