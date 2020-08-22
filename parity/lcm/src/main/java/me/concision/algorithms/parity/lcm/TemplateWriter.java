package me.concision.algorithms.parity.lcm;

import lombok.NonNull;
import lombok.Synchronized;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplistic templater that writes to an {@link OutputStream}
 *
 * @author Concision
 */
public class TemplateWriter {
    private String template;

    /**
     * Instantiates a new templater
     *
     * @param template template to fulfill
     */
    public TemplateWriter(@NonNull String template) {
        this.template = template;
    }

    /**
     * Seeks out a specific variable and writes any non-variablized content to {@param stream}
     *
     * @param stream {@link OutputStream} to write non-variablized content too
     * @param key    variable key identifier
     * @throws IOException if an underlying i/o exception occurs
     */
    @Synchronized
    public void seek(@NonNull OutputStream stream, @NonNull String key) throws IOException {
        if (this.template == null) throw new IllegalStateException("templater instance is already closed");
        if (!key.matches("^\\w+$"))
            throw new IllegalArgumentException("variable key must be alphanumeric with underscores");

        // searching for '/* KEY */' or '/* KEY */ arbitrary content /* /KEY */'
        Matcher matcher = Pattern.compile(
                String.join(key, "/\\*\\s*", "\\s*\\*/(?:(?:(?!/\\*).)+/\\*\\s*+/", "\\s*+\\*/)?"),
                Pattern.MULTILINE
        ).matcher(this.template);

        if (!matcher.find()) throw new IllegalArgumentException("template variable section not found: " + key);

        stream.write(this.template.substring(0, matcher.start(0)).getBytes(StandardCharsets.ISO_8859_1));
        this.template = this.template.substring(matcher.end(0));
    }

    /**
     * Writes the remaining template to {@param stream}.
     *
     * @param stream {@link OutputStream} to write remaining template to
     * @throws IOException if an underlying i/o exception occurs
     */
    @Synchronized
    public void finish(@NonNull OutputStream stream) throws IOException {
        if (this.template == null) throw new IllegalStateException("templater instance is already closed");
        stream.write(this.template.getBytes(StandardCharsets.ISO_8859_1));
        template = null;
    }
}
