package de.devtoolbox.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Dark "engineering suite" look: near-black background, cards with thin borders, green accent, Inter for text and
 * JetBrains Mono for paths, badges and key caps.
 */
final class UiTheme {

    static Color BACKGROUND = new Color(0x0B0F14);
    static Color HEADER = new Color(0x0E131A);
    static Color CARD = new Color(0x111821);
    static Color CARD_HEADER = new Color(0x15213A);
    static Color INPUT = new Color(0x0D131B);
    static Color BORDER = new Color(0x222C38);
    static Color BORDER_STRONG = new Color(0x2E3A48);
    static Color ACCENT = new Color(0x34C77B);
    static Color ACCENT_DARK = new Color(0x2E9E68);
    static Color ACCENT_BORDER = new Color(0x2F7A57);
    static Color ACCENT_TINT = new Color(0x163327);
    static Color TEXT = new Color(0xE6EDF3);
    static Color MUTED = new Color(0x8B96A5);
    static Color WARNING = new Color(0xE3A33B);
    static Color VIOLET = new Color(0xA78BFA);
    static Color CYAN = new Color(0x38BDF8);

    private UiTheme() {
    }

    /** Layout B: navy with blue accent (sidebar layout). */
    static boolean variantB;

    /** @param b true for layout B (navy, blue), false for layout A (near black, green) */
    static void install(boolean b) {
        variantB = b;
        if (b) {
            BACKGROUND = new Color(0x161C2E);
            HEADER = new Color(0x111628);
            CARD = new Color(0x1C2336);
            CARD_HEADER = new Color(0x1C2336);
            INPUT = new Color(0x151B2C);
            BORDER = new Color(0x2A3350);
            BORDER_STRONG = new Color(0x3A4568);
            ACCENT = new Color(0x4F7DF3);
            ACCENT_DARK = new Color(0x3566E8);
            ACCENT_BORDER = new Color(0x4F7DF3);
            ACCENT_TINT = new Color(0x27355E);
            TEXT = new Color(0xE8ECF5);
            MUTED = new Color(0x9AA3B8);
        } else {
            BACKGROUND = new Color(0x0B0F14);
            HEADER = new Color(0x0E131A);
            CARD = new Color(0x111821);
            CARD_HEADER = new Color(0x15213A);
            INPUT = new Color(0x0D131B);
            BORDER = new Color(0x222C38);
            BORDER_STRONG = new Color(0x2E3A48);
            ACCENT = new Color(0x34C77B);
            ACCENT_DARK = new Color(0x2E9E68);
            ACCENT_BORDER = new Color(0x2F7A57);
            ACCENT_TINT = new Color(0x163327);
            TEXT = new Color(0xE6EDF3);
            MUTED = new Color(0x8B96A5);
        }
        install();
    }

    private static void install() {
        try {
            FlatInterFont.install();
            FlatJetBrainsMonoFont.install();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
        } catch (Throwable e) {
            // fonts are optional – the defaults of the platform are used
        }
        Map<String, String> d = new HashMap<>();
        d.put("@background", hex(BACKGROUND));
        d.put("@foreground", hex(TEXT));
        d.put("@accentColor", hex(ACCENT));
        d.put("Panel.background", hex(BACKGROUND));
        d.put("Component.focusColor", hex(ACCENT_DARK));
        d.put("Component.focusedBorderColor", hex(ACCENT));
        d.put("Component.borderColor", hex(BORDER_STRONG));
        d.put("Component.focusWidth", "1");
        d.put("Component.arc", "10");
        d.put("Button.arc", "10");
        d.put("TextComponent.arc", "10");
        d.put("CheckBox.arc", "0"); // square boxes
        d.put("TextField.background", hex(INPUT));
        d.put("TextComponent.selectionBackground", hex(ACCENT_TINT));
        d.put("TextComponent.selectionForeground", hex(TEXT));
        d.put("TextArea.background", hex(INPUT));
        d.put("ComboBox.background", hex(INPUT));
        d.put("ComboBox.buttonBackground", hex(INPUT));
        d.put("ComboBox.buttonEditableBackground", hex(INPUT));
        d.put("ComboBox.popupBackground", hex(CARD));
        d.put("List.selectionBackground", hex(ACCENT_TINT));
        d.put("ComboBox.selectionBackground", hex(ACCENT_TINT));
        d.put("Button.background", hex(CARD));
        d.put("Button.borderColor", hex(BORDER_STRONG));
        d.put("Button.hoverBackground", "#18212c");
        d.put("Button.default.background", hex(ACCENT_DARK));
        d.put("Button.default.foreground", "#ffffff");
        d.put("Button.default.hoverBackground", hex(ACCENT));
        d.put("Button.default.borderColor", hex(ACCENT_DARK));
        d.put("CheckBox.icon.selectedBackground", hex(ACCENT_DARK));
        d.put("CheckBox.icon.selectedBorderColor", hex(ACCENT_DARK));
        d.put("CheckBox.icon.checkmarkColor", "#ffffff");
        d.put("CheckBox.icon.background", hex(INPUT));
        d.put("CheckBox.icon.borderColor", hex(BORDER_STRONG));
        d.put("SplitPane.background", hex(BACKGROUND));
        d.put("SplitPaneDivider.style", "plain");
        d.put("SplitPaneDivider.gripColor", hex(BORDER_STRONG));
        d.put("ScrollBar.track", hex(BACKGROUND));
        d.put("ScrollBar.thumb", hex(BORDER_STRONG));
        d.put("ScrollBar.width", "10");
        d.put("ScrollPane.background", hex(BACKGROUND));
        d.put("PopupMenu.background", hex(CARD));
        d.put("MenuItem.selectionBackground", hex(ACCENT_TINT));
        d.put("MenuBar.background", hex(HEADER));
        d.put("TitlePane.background", hex(HEADER));
        d.put("TitlePane.inactiveBackground", hex(HEADER));
        d.put("TitlePane.embeddedForeground", hex(MUTED));
        d.put("TitlePane.unifiedBackground", "false");
        d.put("ToolTip.background", hex(CARD));
        d.put("ToolTip.border", "1,1,1,1," + hex(BORDER_STRONG));
        d.put("OptionPane.background", hex(CARD));
        d.put("TitledBorder.titleColor", hex(MUTED));
        FlatLaf.setGlobalExtraDefaults(d);
        FlatDarkLaf.setup();
        // custom title bar: the header (logo, project, status) sits in it
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
    }

    static Font mono(float size) {
        Font f = UIManager.getFont("monospaced.font");
        return (f != null ? f : new Font(Font.MONOSPACED, Font.PLAIN, 12)).deriveFont(Font.PLAIN, size);
    }

    static Font text(int style, float size) {
        Font f = UIManager.getFont("Label.font");
        return (f != null ? f : new JLabel().getFont()).deriveFont(style, size);
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }
}
