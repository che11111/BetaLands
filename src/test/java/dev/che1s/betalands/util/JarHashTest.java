package dev.che1s.betalands.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;

public class JarHashTest {
    @Test
    public void calculatesStandardSha256() throws Exception {
        File file = File.createTempFile("betalands-jar-hash", ".dat");
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write("abc".getBytes("UTF-8"));
        } finally {
            output.close();
        }
        try {
            assertEquals(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    JarHash.sha256(file));
        } finally {
            file.delete();
        }
    }
}
