package trang_chu.du_an_lon;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("View1.fxml"));
        stage.setTitle("Hệ thống đấu giá trực tuyến");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
