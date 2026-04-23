package du_an_lon;

import dao.DAOAution_Items;
import dao.DAOItems;
import dao.DAOUser;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.*;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class ControllerSeller {
    public static File file = null;
    private static String fileName;

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
        // Mục đích (Node) event.getSource() là để lấy Node hiện tại đó
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "View1.fxml");
    }

    @FXML
    private Button LogOut;

    @FXML
    private Button j_ApplyItem;

    @FXML
    private Label error_Label;

    @FXML
    private ComboBox<String> j_ItemType;
    private String[] list = new String[]{"Mỹ thuật", "Điện tử", "Phương tiện giao thông"};

    public void initialize() {
        j_ItemType.getItems().setAll(list);

        // Nếu muốn khi mở app lên nó chọn sẵn một cái (không bị trống)
        j_ItemType.setValue(list[0]);
        User p1 = UserSession.getLoggedInUser();
        j_LabelName.setText(p1.getName());

    }


    @FXML
    private Label j_LabelName;


    @FXML
    private TextField j_StartingPrice;

    @FXML
    private TextArea j_description;

    @FXML
    private TextField j_name;
    @FXML
    private DatePicker j_DateEnd; // Định dạng YYYY/mm/dd

    @FXML
    private DatePicker j_DateStart;

    @FXML
    private TextField j_TimeEnd; //Định dạng HH:mm:ss

    @FXML
    private TextField j_TimeStart;

    public Instant createInstant(DatePicker j_date, TextField j_time) {
        // 1. Lấy LocalDate từ DatePicker
        LocalDate date = j_date.getValue();
        // 2. Lấy String từ TextField (ví dụ: "15:30:45")
        String timeStr = j_time.getText();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime time = LocalTime.parse(timeStr, formatter);

        // 4. Kết hợp ngày + giờ + múi giờ hệ thống để tạo Instant
        // .atZone(ZoneId.systemDefault()) sẽ tự cộng/trừ chênh lệch múi giờ để ra chuẩn UTC
        Instant instant = LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return instant;
    }

    @FXML
    private ImageView j_image;

    public void handle_Items() {
        User p1 = UserSession.getLoggedInUser();
        try {
            Instant start = createInstant(j_DateStart, j_TimeStart);
            Instant end = createInstant(j_DateEnd, j_TimeEnd);
            if (j_ItemType.getValue().equals("Điện tử")) {
                Electronics item = new Electronics(j_name.getText(), j_description.getText(),
                        Double.parseDouble(j_StartingPrice.getText()), start, end, p1.getUsername(),
                        j_brand.getText(), j_model.getText(), fileName);
                DAOItems.getInstance().Insert(item);
                DAOAution_Items.getInstance().Insert(item);
            }
            if (j_ItemType.getValue().equals("Phương tiện giao thông")) {
                Vehicle item = new Vehicle(j_name.getText(), j_description.getText(),
                        Double.parseDouble(j_StartingPrice.getText()),
                        start, end, p1.getUsername(), j_manufacturer.getText(), j_year.getText(), fileName);
                DAOItems.getInstance().Insert(item);
                DAOAution_Items.getInstance().Insert(item);
            }
            if (j_ItemType.getValue().equals("Mỹ thuật")) {
                Art item = new Art(j_name.getText(), j_description.getText(),
                        Double.parseDouble(j_StartingPrice.getText()),
                        start, end, p1.getUsername(), j_artist.getText(), fileName);
                DAOItems.getInstance().Insert(item);
                DAOAution_Items.getInstance().Insert(item);
            }
            error_Label.setText("Đăng bán sản phẩm thành công");
            error_Label.setVisible(true);
        } catch (Exception e) {
            error_Label.setTextFill(Color.RED);
            error_Label.setText("Điền thông tin bắt buộc!");
            error_Label.setVisible(true);
        }

    }

    @FXML
    private ImageView MyImgView;

    @FXML
    private Button j_img;

    @FXML
    void handle_SelectImage(ActionEvent event) throws IOException {
        FileChooser fileChooser = new FileChooser();

        // Chỉ hiện các định dạng ảnh
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif")
        );

        // Mở cửa sổ chọn file
        file = fileChooser.showOpenDialog(null);

        if (file != null) {
            // 1. Hiển thị ảnh lên giao diện
            Image image = new Image(file.toURI().toString());
            MyImgView.setImage(image);

            // 2. Lấy tên file để lưu vào Database (Path)
            // Bạn nên lưu tên file duy nhất để tránh trùng (ví dụ thêm timestamp)
            fileName = System.currentTimeMillis() + "_" + file.getName();
            // 3. Thực hiện copy file từ máy khách vào thư mục của Server/Project
            File dest = new File("src/main/java/imgs/" + fileName);
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }


    }

    void reset_pane() {
        j_paneArt.setVisible(false);
        j_paneElectronics.setVisible(false);
        j_paneVehicle.setVisible(false);
    }

    @FXML
    void handle_Info(ActionEvent event) {
        String result = j_ItemType.getValue();
        if (result.equals("Mỹ thuật")) {
            reset_pane();
            j_paneArt.setVisible(true);
        }
        if (result.equals("Điện tử")) {
            reset_pane();
            j_paneElectronics.setVisible(true);
        }
        if (result.equals("Phương tiện giao thông")) {
            reset_pane();
            j_paneVehicle.setVisible(true);
        }
    }

    @FXML
    private TextField j_year;

    @FXML
    private AnchorPane j_paneArt;

    @FXML
    private AnchorPane j_paneElectronics;

    @FXML
    private AnchorPane j_paneVehicle;

    @FXML
    private TextField j_manufacturer;

    @FXML
    private TextField j_model;

    @FXML
    private TextField j_artist;

    @FXML
    private TextField j_brand;


}




