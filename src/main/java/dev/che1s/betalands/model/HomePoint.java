package dev.che1s.betalands.model;

public final class HomePoint {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public HomePoint(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public String serialize() {
        return x + "," + y + "," + z + "," + yaw + "," + pitch;
    }

    public static HomePoint parse(String value) {
        if (value == null || value.length() == 0 || "-".equals(value)) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid home point: " + value);
        }
        return new HomePoint(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4])
        );
    }
}
