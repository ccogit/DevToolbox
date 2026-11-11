import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Captures the visible Swing window in an Xvfb display for visual review. */
public class UiScreenshot {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: java UiScreenshot.java <output.png>");
        }
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        var image = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice())
                .createScreenCapture(screen);
        if (!ImageIO.write(image, "png", Path.of(args[0]).toFile())) {
            throw new IllegalStateException("PNG writer unavailable");
        }
        System.out.println("Screenshot: " + args[0] + " (" + screen.width + "x" + screen.height + ")");
    }
}
