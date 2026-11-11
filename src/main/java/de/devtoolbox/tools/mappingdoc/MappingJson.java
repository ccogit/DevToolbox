package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.tools.codecollector.TargetModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    /** The JSON of the entity in the target model folder, or null if none matches. */
    public static MappingJson find(Path folder, List<String> terms) throws IOException {
        TargetModel.Result r = TargetModel.find(folder, terms);
        if (r.file() == null) {
            return null;
        }
        return new MappingJson(r.file(), r.entity(), Files.readString(r.file()), r.attributes());
    }

    /** Prefill a property on attribute objects of this entity only, preserving unrelated JSON. */
    public String with(String key, String value) {
        return MappingJsonPrefill.replace(text, entity, attributes, key, value);
    }

    /** {@link #with} for several keys in turn. */
    public MappingJson withAll(java.util.Map<String, String> values) {
        MappingJson json = this;
        for (var e : values.entrySet()) {
            json = new MappingJson(file, entity, json.with(e.getKey(), e.getValue()), attributes);
        }
        return json;
    }

}
