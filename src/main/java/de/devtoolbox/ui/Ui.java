package de.devtoolbox.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Small visual building blocks of the layout: cards, badges, icon tiles, links, key caps. */
final class Ui {

    private Ui() {
    }

    /** Rounded panel with a thin border; {@code accent} = green border (the core configuration). */
    static class Card extends JPanel {
        private final boolean accent;
        private final int arc;
        Color fill = UiTheme.CARD;
        /** Height of a differently coloured header strip (0 = none). */
        int headerHeight;

        Card(LayoutManager layout, boolean accent) {
            this(layout, accent, 14);
        }

        Card(LayoutManager layout, boolean accent, int arc) {
            super(layout);
            this.accent = accent;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc);
            g2.setColor(fill);
            g2.fill(shape);
            if (headerHeight > 0) {
                Shape clip = g2.getClip();
                g2.clip(shape);
                g2.setPaint(new GradientPaint(0, 0, UiTheme.CARD_HEADER, getWidth(), 0, new Color(0x121A2A)));
                g2.fillRect(0, 0, getWidth(), headerHeight);
                g2.setColor(UiTheme.BORDER);
                g2.drawLine(0, headerHeight, getWidth(), headerHeight);
                g2.setClip(clip);
            }
            g2.setColor(accent ? UiTheme.ACCENT_BORDER : UiTheme.BORDER);
            g2.setStroke(new BasicStroke(accent ? 1.4f : 1f));
            g2.draw(shape);
            g2.dispose();
        }
    }

    /** Small rounded label, e.g. "4 Pfade konfiguriert" (mono) or "Kern-Konfiguration". */
    static JLabel badge(String text, Color fg, Color bg, Color border, boolean mono) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setForeground(fg);
        label.setFont(mono ? UiTheme.mono(11f) : UiTheme.text(Font.PLAIN, 11.5f));
        label.setBorder(new EmptyBorder(3, 7, 3, 7));
        return label;
    }

    static JLabel greenBadge(String text, boolean mono) {
        return badge(text, UiTheme.ACCENT, UiTheme.ACCENT_TINT, UiTheme.ACCENT_BORDER, mono);
    }

    static JLabel greyBadge(String text) {
        return badge(text, UiTheme.MUTED, new Color(0x1A222D), UiTheme.BORDER_STRONG, true);
    }

    /** Rounded square with a glyph, e.g. the ✓ in front of "Pfade & Dateiquellen". */
    static JComponent iconTile(String glyph, Color color, int size) {
        JLabel label = new JLabel(glyph, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 38));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setForeground(color);
        label.setFont(UiTheme.text(Font.BOLD, size * 0.48f));
        Dimension d = new Dimension(size, size);
        label.setPreferredSize(d);
        label.setMinimumSize(d);
        label.setMaximumSize(d);
        return label;
    }

    /** The logo: dark green rounded square with green border, a green cube carrying "‹›". */
    static JComponent logo(int size) {
        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float s = size;
                g2.setPaint(new GradientPaint(0, 0, new Color(0x1E5A32), s, s, new Color(0x0A1612)));
                g2.fill(new RoundRectangle2D.Float(1, 1, s - 2, s - 2, s * 0.32f, s * 0.32f));
                g2.setColor(new Color(0x3DBE5A));
                g2.setStroke(new BasicStroke(Math.max(1.2f, s / 22f)));
                g2.draw(new RoundRectangle2D.Float(1, 1, s - 2, s - 2, s * 0.32f, s * 0.32f));
                // isometric cube: top, left and right face
                float cx = s / 2, cy = s / 2, r = s * 0.24f;
                Polygon top = poly(cx, cy - r, cx + r, cy - r / 2, cx, cy, cx - r, cy - r / 2);
                Polygon left = poly(cx - r, cy - r / 2, cx, cy, cx, cy + r, cx - r, cy + r / 2);
                Polygon right = poly(cx + r, cy - r / 2, cx, cy, cx, cy + r, cx + r, cy + r / 2);
                g2.setColor(new Color(0x5FD873));
                g2.fill(top);
                g2.setColor(new Color(0x3DBE5A));
                g2.fill(left);
                g2.setColor(new Color(0x2C9A46));
                g2.fill(right);
                g2.setColor(Color.WHITE);
                g2.setFont(UiTheme.text(Font.BOLD, s * 0.2f));
                FontMetrics fm = g2.getFontMetrics();
                String code = "<>";
                g2.drawString(code, cx - fm.stringWidth(code) / 2f, cy + r * 0.55f);
                g2.dispose();
            }

            private Polygon poly(float... xy) {
                Polygon p = new Polygon();
                for (int i = 0; i < xy.length; i += 2) {
                    p.addPoint(Math.round(xy[i]), Math.round(xy[i + 1]));
                }
                return p;
            }
        };
        Dimension d = new Dimension(size, size);
        logo.setPreferredSize(d);
        logo.setMinimumSize(d);
        logo.setMaximumSize(d);
        return logo;
    }

    /** Upper-case section title with a coloured glyph, e.g. "FACHLICHE ZIELENTITÄT". */
    static JComponent sectionTitle(String glyph, Color color, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        JLabel icon = new JLabel(glyph);
        icon.setForeground(color);
        icon.setFont(UiTheme.text(Font.BOLD, 13f));
        JLabel title = new JLabel(text.toUpperCase(java.util.Locale.GERMAN));
        title.setForeground(UiTheme.TEXT);
        title.setFont(UiTheme.text(Font.BOLD, 12.5f));
        row.add(icon);
        row.add(title);
        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setOpaque(false);
        box.add(row, BorderLayout.CENTER);
        JSeparator line = new JSeparator();
        line.setForeground(UiTheme.BORDER);
        box.add(line, BorderLayout.SOUTH);
        box.setBorder(new EmptyBorder(0, 0, 10, 0));
        return box;
    }

    /** Green text link with hand cursor. */
    static JLabel link(String text, Runnable action) {
        JLabel link = new JLabel(text);
        link.setForeground(UiTheme.ACCENT);
        link.setFont(UiTheme.text(Font.PLAIN, 12.5f));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                link.setForeground(UiTheme.TEXT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                link.setForeground(UiTheme.ACCENT);
            }
        });
        return link;
    }

    /** Key cap, e.g. "Strg". */
    static JLabel key(String text) {
        JLabel k = badge(text, UiTheme.TEXT, new Color(0x0F151D), UiTheme.BORDER_STRONG, true);
        k.setBorder(new EmptyBorder(4, 8, 4, 8));
        return k;
    }

    static JLabel muted(String text, float size, boolean mono) {
        JLabel l = new JLabel(text);
        l.setForeground(UiTheme.MUTED);
        l.setFont(mono ? UiTheme.mono(size) : UiTheme.text(Font.PLAIN, size));
        return l;
    }

    static JLabel strong(String text, float size) {
        JLabel l = new JLabel(text);
        l.setForeground(UiTheme.TEXT);
        l.setFont(UiTheme.text(Font.BOLD, size));
        return l;
    }

    static JPanel transparent(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** Green filled primary button (like "Job starten"). */
    static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "background: " + hex(UiTheme.ACCENT_DARK)
                + "; foreground: #ffffff; hoverBackground: " + hex(UiTheme.ACCENT) + "; pressedBackground: "
                + hex(UiTheme.ACCENT_DARK.darker()) + "; borderWidth: 0; focusWidth: 0; arc: 12");
        b.setFont(UiTheme.text(Font.BOLD, 14f));
        b.setPreferredSize(new Dimension(Math.max(120, b.getPreferredSize().width + 20), 40));
        return b;
    }

    /** Secondary button of the same height as {@link #primaryButton}. */
    static JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "arc: 12");
        b.setFont(UiTheme.text(Font.PLAIN, 13.5f));
        b.setPreferredSize(new Dimension(b.getPreferredSize().width + 16, 40));
        return b;
    }

    /** Smaller button used inside cards. */
    static JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("FlatLaf.style", "arc: 10");
        b.setFont(UiTheme.text(Font.PLAIN, 12.5f));
        b.setPreferredSize(new Dimension(b.getPreferredSize().width + 8, 34));
        return b;
    }

    static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    /** Page header: icon tile, bold title, muted subtitle; {@code east} (buttons) on the right, may be null. */
    static JComponent pageHeader(String glyph, String title, String subtitle, JComponent east) {
        JPanel head = transparent(new BorderLayout(14, 0));
        head.add(iconTile(glyph, UiTheme.TEXT, 44), BorderLayout.WEST);
        JPanel text = transparent(new GridLayout(2, 1, 0, 2));
        text.add(strong(title, 22f));
        JLabel sub = muted(subtitle, 13.5f, false);
        text.add(sub);
        head.add(text, BorderLayout.CENTER);
        if (east != null) {
            JPanel box = transparent(new GridBagLayout());
            box.add(east);
            head.add(box, BorderLayout.EAST);
        }
        return head;
    }

    /** Card with a header (glyph, bold title, muted subtitle, optional action on the right) above {@code body}. */
    static Card card(String glyph, String title, String subtitle, JComponent action, JComponent body) {
        Card card = new Card(new BorderLayout(0, 8), false);
        card.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel head = transparent(new BorderLayout(12, 0));
        JLabel icon = new JLabel(glyph);
        icon.setFont(UiTheme.text(Font.PLAIN, 20f));
        icon.setForeground(UiTheme.MUTED);
        head.add(icon, BorderLayout.WEST);
        JPanel text = transparent(new GridLayout(2, 1, 0, 1));
        text.add(strong(title, 15f));
        text.add(muted(subtitle, 12f, false));
        head.add(text, BorderLayout.CENTER);
        if (action != null) {
            JPanel box = transparent(new GridBagLayout());
            box.add(action);
            head.add(box, BorderLayout.EAST);
        }
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
}
