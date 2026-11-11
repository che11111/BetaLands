package dev.che1s.betalands.model;

public final class PlayerData {
    private final String key;
    private String lastName;
    private String levelId;
    private boolean levelManaged;
    private long levelExpiresAt;
    private long accumulatedSeconds;
    private long sessionStartedAt;

    public PlayerData(String key, String lastName, long accumulatedSeconds) {
        this.key = key;
        this.lastName = lastName;
        this.levelId = "default";
        this.levelManaged = false;
        this.levelExpiresAt = 0L;
        this.accumulatedSeconds = Math.max(0L, accumulatedSeconds);
    }

    public String getKey() {
        return key;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getLevelId() {
        return levelId;
    }

    public void setLevelId(String levelId) {
        this.levelId = levelId == null || levelId.length() == 0 ? "default" : levelId;
    }

    public boolean isLevelManaged() {
        return levelManaged;
    }

    public long getLevelExpiresAt() {
        return levelExpiresAt;
    }

    public boolean isManagedLevelExpired(long now) {
        return levelManaged && levelExpiresAt > 0L && levelExpiresAt <= now;
    }

    public void setManagedLevel(String levelId, long expiresAt) {
        setLevelId(levelId);
        this.levelManaged = true;
        this.levelExpiresAt = Math.max(0L, expiresAt);
    }

    public void clearManagedLevel() {
        this.levelManaged = false;
        this.levelExpiresAt = 0L;
    }

    public long getAccumulatedSeconds() {
        return accumulatedSeconds;
    }

    public synchronized void startSession(long now) {
        if (sessionStartedAt <= 0L) {
            sessionStartedAt = now;
        }
    }

    public synchronized long settle(long now) {
        if (sessionStartedAt > 0L) {
            long elapsed = Math.max(0L, (now - sessionStartedAt) / 1000L);
            accumulatedSeconds += elapsed;
            sessionStartedAt = now;
        }
        return accumulatedSeconds;
    }

    public synchronized long stopSession(long now) {
        settle(now);
        sessionStartedAt = 0L;
        return accumulatedSeconds;
    }

    public synchronized long getTotalSeconds(long now) {
        if (sessionStartedAt <= 0L) {
            return accumulatedSeconds;
        }
        return accumulatedSeconds + Math.max(0L, (now - sessionStartedAt) / 1000L);
    }

    public synchronized boolean isOnlineSession() {
        return sessionStartedAt > 0L;
    }
}
