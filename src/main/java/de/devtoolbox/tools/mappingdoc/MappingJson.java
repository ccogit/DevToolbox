package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.tools.codecollector.TargetModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The target model JSON of an entity – the source of the mapping documentation (the Confluence table is generated
 * from it). Format:
 * <pre>
 * { "entityTypeName" : "NachlassZuschlag",
 *   "typeAttributeDocumentationList" : [ { "attributeName" : "DiffStatus", "mapping" : "…", "schwebeMapping" : "…",
 *       "specification" : [ "…" ], "status" : [ "…" ] }, … ] }
 * </pre>
 *
 * @param text the file content as read
 */
public record MappingJson(Path file, String entity, String text, List<String> attributes) {

    private static final Pattern ENTITY_NAME = Pattern.compile(
            "\"(?i:entity_?type_?name|entit(?:ae|ä)t(?:s?_?name)?|entity_?name)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final String VALUE = "(\\[\\s*(?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*,?\\s*)*]|\"(?:[^\"\\\\]|\\\\.)*\")";

    /** The JSON of the entity in the target model folder, or null if none matches. */
    public static MappingJson find(Path folder, List<String> terms) throws IOException {
        TargetModel.Result r = TargetModel.find(folder, terms);
        if (r.file() == null) {
            return null;
        }
        return new MappingJson(r.file(), r.entity(), Files.readString(r.file()), r.attributes());
    }

    /**
     * The text with {@code key} (e.g. "specification") set to {@code value} for every attribute of this entity – an
     * array stays an array with one element, a string stays a string. Other entities in the same file are untouched.
     */
    public String with(String key, String value) {
        int[] span = span();
        String part = text.substring(span[0], span[1]);
        Matcher m = Pattern.compile("(\"" + Pattern.quote(key) + "\"\\s*:\\s*)" + VALUE).matcher(part);
        StringBuilder sb = new StringBuilder();
        String quoted = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        while (m.find()) {
            String replacement = m.group(2).startsWith("[") ? "[ " + quoted + " ]" : quoted;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + replacement));
        }
        m.appendTail(sb);
        return text.substring(0, span[0]) + sb + text.substring(span[1]);
    }

    /** {@link #with} for several keys in turn. */
    public MappingJson withAll(java.util.Map<String, String> values) {
        MappingJson json = this;
        for (var e : values.entrySet()) {
            json = new MappingJson(file, entity, json.with(e.getKey(), e.getValue()), attributes);
        }
        return json;
    }

    /** From the entity's name key to the next entity's name key (or the end of the file). */
    private int[] span() {
        List<int[]> names = new ArrayList<>();
        Matcher m = ENTITY_NAME.matcher(text);
        int start = -1;
        int end = text.length();
        while (m.find()) {
            names.add(new int[]{m.start(), m.end()});
            if (start >= 0 && end == text.length()) {
                end = m.start();
            }
            if (start < 0 && m.group(1).strip().equals(entity)) {
                start = m.start();
            }
        }
        return names.size() <= 1 || start < 0 ? new int[]{0, text.length()} : new int[]{start, end};
    }
}
