package de.devtoolbox.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTest {

    @Test
    void documentChangesNotifyOnInsertAndRemove() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = new JTextField();
            AtomicInteger edits = new AtomicInteger();
            Ui.onDocumentChange(field, edits::incrementAndGet);

            field.setText("abc");
            assertEquals(1, edits.get());
            field.setText("");
            assertEquals(2, edits.get());
        });
    }

    @Test
    void disclosureExpandsAndCollapsesAndRequestsLayout() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel content = new JPanel();
            AtomicInteger refreshes = new AtomicInteger();
            JLabel toggle = Ui.disclosure("Details", content, refreshes::incrementAndGet);
            assertFalse(content.isVisible());
            assertEquals("Details  ⌄", toggle.getText());

            click(toggle);
            assertTrue(content.isVisible());
            assertEquals("Details  ⌃", toggle.getText());

            click(toggle);
            assertFalse(content.isVisible());
            assertEquals("Details  ⌄", toggle.getText());
            assertEquals(2, refreshes.get());
        });
    }

    private static void click(JLabel link) {
        MouseEvent event = new MouseEvent(link, MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false);
        for (MouseListener listener : link.getMouseListeners()) {
            listener.mouseClicked(event);
        }
    }
}
