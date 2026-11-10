package de.devtoolbox.tools.codecollector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads text files as UTF-8, falls back to Windows-1252 (older projects), skips binaries. */
public final class SourceReader {

    private static final Charset FALLBACK = Charset.forName("windows-1252");

    private SourceReader() {
    }

    /** @return normalized content ({@code \n} line endings, no BOM) or {@code null} if unreadable/binary */
    public static String read(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            for (int i = 0; i < Math.min(bytes.length, 8000); i++) {
                if (bytes[i] == 0) {
                    return null;
                }
            }
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                text = new String(bytes, FALLBACK);
            }
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return text.replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException e) {
            return null;
        }
    }
}
