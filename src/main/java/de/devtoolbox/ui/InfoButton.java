package de.devtoolbox.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Small symbol that keeps long explanations off the screen: ⓘ, or ⚠ when something needs attention. The full text
 * appears as tooltip and, on click, in a dialog.
 */
final class InfoButton extends JButton {

    private String title;
    private String html = "";

    InfoButton(String title) {
        super("ⓘ");
        this.title = title;
        putClientProperty("JButton.buttonType", "toolBarButton");
        setFont(getFont().deriveFont(getFont().getSize2D() + 3f));
        setFocusable(false);
        setMargin(new Insets(0, 4, 0, 4));
        addActionListener(e -> JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                new JLabel("<html><div style='width:420px'>" + html + "</div></html>"), this.title,
                JOptionPane.INFORMATION_MESSAGE));
    }

    /** @param html text (may contain simple HTML); {@code warning} shows ⚠ instead of ⓘ */
    void setInfo(String title, String html, boolean warning) {
        this.title = title;
        this.html = html;
        setText(warning ? "⚠" : "ⓘ");
        setForeground(warning ? new Color(0xE0A030) : UIManager.getColor("Label.foreground"));
        setToolTipText("<html><div style='width:420px'><b>" + title + "</b><br>" + html + "</div></html>");
    }
}
