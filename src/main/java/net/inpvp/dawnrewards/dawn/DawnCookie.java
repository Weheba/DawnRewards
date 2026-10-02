package net.inpvp.dawnrewards.dawn;

import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

@NullMarked
public record DawnCookie(byte version, String brand, String ref) {

    public static DawnCookie parse(byte[] bytes) throws IOException {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return new DawnCookie(stream.readByte(), stream.readUTF(), stream.readUTF());
        }
    }
}
