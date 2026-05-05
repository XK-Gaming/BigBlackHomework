package du_an_lon;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        var view = getClass().getResource("View1.fxml");
        if (view == null) {
            throw new IOException("Cannot find View1.fxml");
        }

        Parent root = FXMLLoader.load(view);
        stage.setTitle("Hệ thống đấu giá trực tuyến");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
