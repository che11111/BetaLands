package dev.che1s.betalands.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class JarHash {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JarHash() {
    }

    public static String sha256(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Plugin JAR is not a readable file.");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        }
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        } finally {
            if (input != null) {
                input.close();
            }
        }
        byte[] bytes = digest.digest();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = HEX[value >>> 4];
            result[index * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(result);
    }
}
