package de.devtoolbox.core;

import java.util.List;

/**
 * Describes one configuration value a job needs. The UI, JSON files and the CLI are built from this.
 *
 * @param isAdvanced rarely needed setting; the UI may show it in a collapsed "advanced" section (not yet rendered).
 *                   Named {@code isAdvanced} because {@link #advanced()} is the fluent copy method.
 */
public record Param(String key, String label, Type type, String defaultValue, boolean required, String hint,
                    List<Option> options, boolean isAdvanced) {

    public enum Type { TEXT, DIRECTORY, FILE, FLAG, CHOICE, MULTI }

    /** One selectable value of a CHOICE or MULTI param. */
    public record Option(String value, String label) {
    }

    /** A regular (not advanced) param. */
    public Param(String key, String label, Type type, String defaultValue, boolean required, String hint,
                 List<Option> options) {
        this(key, label, type, defaultValue, required, hint, options, false);
    }

    /** This param marked as advanced. */
    public Param advanced() {
        return new Param(key, label, type, defaultValue, required, hint, options, true);
    }

    public static Param text(String key, String label, String defaultValue, boolean required, String hint) {
        return new Param(key, label, Type.TEXT, defaultValue, required, hint, List.of());
    }

    public static Param directory(String key, String label, boolean required, String hint) {
        return new Param(key, label, Type.DIRECTORY, "", required, hint, List.of());
    }

    /** An existing file to read; {@code extensions} (e.g. "pdf") restrict the file chooser. */
    public static Param file(String key, String label, boolean required, String hint, String... extensions) {
        return new Param(key, label, Type.FILE, "", required, hint,
                java.util.Arrays.stream(extensions).map(e -> new Option(e, e.toUpperCase())).toList());
    }

    public static Param flag(String key, String label, boolean defaultValue, String hint) {
        return new Param(key, label, Type.FLAG, String.valueOf(defaultValue), false, hint, List.of());
    }

    /** Exactly one of the options. */
    public static Param choice(String key, String label, String defaultValue, String hint, Option... options) {
        return new Param(key, label, Type.CHOICE, defaultValue, true, hint, List.of(options));
    }

    /** Any subset of the options; the value is a comma-separated list of option values. */
    public static Param multi(String key, String label, String defaultValue, String hint, Option... options) {
        return new Param(key, label, Type.MULTI, defaultValue, true, hint, List.of(options));
    }
}
