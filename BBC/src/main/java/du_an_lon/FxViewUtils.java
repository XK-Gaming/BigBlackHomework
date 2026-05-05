package du_an_lon;

import java.io.File;
import java.net.URL;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;

public final class FxViewUtils {
    private FxViewUtils() {
    }

    public static void loadImage(ImageView imageView, String path) {
        if (imageView == null) {
            return;
        }
        imageView.setImage(null);
        if (path == null || path.isBlank()) {
            return;
        }

        File file = new File(path);
        if (file.isFile()) {
            imageView.setImage(new Image(file.toURI().toString()));
            return;
        }

        String resourcePath = path.startsWith("/") ? path : "/" + path;
        URL resourceUrl = FxViewUtils.class.getResource(resourcePath);
        if (resourceUrl != null) {
            imageView.setImage(new Image(resourceUrl.toExternalForm()));
        }
    }

    public static void showError(Label label, String message) {
        showMessage(label, message, Color.FIREBRICK);
    }

    public static void showSuccess(Label label, String message) {
        showMessage(label, message, Color.DARKGREEN);
    }

    private static void showMessage(Label label, String message, Color color) {
        if (label == null) {
            return;
        }
        label.setTextFill(color);
        label.setText(message);
        label.setVisible(true);
    }
}
