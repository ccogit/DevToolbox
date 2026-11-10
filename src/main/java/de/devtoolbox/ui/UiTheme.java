package de.devtoolbox.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;

import java.util.Map;

/** Dark FlatLaf look with a green focus ring, as in the job selection dialog of the existing tooling. */
final class UiTheme {

    private UiTheme() {
    }

    static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "Component.focusColor", "#3c9a3c",
                "Component.focusedBorderColor", "#4caf50",
                "Component.focusWidth", "2",
                "TitledBorder.titleColor", "#9a9a9a"));
        FlatMacDarkLaf.setup();
    }
}
