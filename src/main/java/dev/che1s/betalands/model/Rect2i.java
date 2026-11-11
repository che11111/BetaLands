package dev.che1s.betalands.model;

public final class Rect2i {
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    public Rect2i(int x1, int z1, int x2, int z2) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Rect2i other) {
        return other != null
                && minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public long area() {
        return (long) (maxX - minX + 1) * (long) (maxZ - minZ + 1);
    }

    public double centerX() {
        return (minX + maxX + 1.0D) / 2.0D;
    }

    public double centerZ() {
        return (minZ + maxZ + 1.0D) / 2.0D;
    }

    public String serialize() {
        return minX + "," + maxX + "," + minZ + "," + maxZ;
    }

    public static Rect2i parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid area: " + value);
        }
        return new Rect2i(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[3])
        );
    }
}
