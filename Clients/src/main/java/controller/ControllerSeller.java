package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.Item;
import model.Items.ItemFactory;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import network.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

public class ControllerSeller implements ServerListener {
    private User user = UserSession.getLoggedInUser();

    private File selectedFile = null;
    private String uploadedImageUrl = null;
    private Cloudinary cloudinary;

    private final AuctionClient client = AuctionClient.getInstance();
    private final String[] categoryList = new String[]{"Mỹ thuật", "Điện tử", "Phương tiện giao thông"};
    private final Map<String, HBox> categoryPaneMap = new HashMap<>();

    @FXML private Button j_ApplyItem;
    @FXML private Label error_Label;
    @FXML private ComboBox<String> j_ItemType;
    @FXML private Label j_LabelName;
    @FXML private TextField j_StartingPrice;
    @FXML private TextField j_MinBid;
    @FXML private TextArea j_description;
    @FXML private TextField j_name;
    @FXML private DatePicker j_DateEnd;
    @FXML private DatePicker j_DateStart;
    @FXML private TextField j_TimeEnd;
    @FXML private TextField j_TimeStart;
    @FXML private ImageView MyImgView;

    @FXML private javafx.scene.shape.Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    @FXML private HBox j_paneArt;
    @FXML private HBox j_paneElectronics;
    @FXML private HBox j_paneVehicle;

    @FXML private TextField j_year;
    @FXML private TextField j_manufacturer;
    @FXML private TextField j_model;
    @FXML private TextField j_artist;
    @FXML private TextField j_brand;

    @FXML private Label j_textSoDu;

    public void initialize() {
        client.addListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        initCloudinary();

        // Ánh xạ phục vụ việc ẩn/hiện động
        categoryPaneMap.put("Mỹ thuật", j_paneArt);
        categoryPaneMap.put("Điện tử", j_paneElectronics);
        categoryPaneMap.put("Phương tiện giao thông", j_paneVehicle);

        j_ItemType.getItems().setAll(categoryList);
        j_ItemType.setValue(categoryList[0]);

        // Đồng bộ hiển thị Form mở rộng ngay từ đầu
        handle_Info(null);
        if (user != null) {
            j_LabelName.setText(user.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(user.getBalance()) + " VNĐ");
        }

        // Chặn ngày hợp lệ
        j_DateStart.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });

        j_DateEnd.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate minDate = (j_DateStart.getValue() != null) ? j_DateStart.getValue() : LocalDate.now();
                setDisable(empty || date.isBefore(minDate));
            }
        });

        j_DateStart.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && j_DateEnd.getValue() != null && j_DateEnd.getValue().isBefore(newVal)) {
                j_DateEnd.setValue(newVal);
            }
        });
    }

    private void initCloudinary() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            Properties prop = new Properties();
            if (input != null) {
                prop.load(input);
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", prop.getProperty("cloudinary.cloud_name"),
                        "api_key", prop.getProperty("cloudinary.api_key"),
                        "api_secret", prop.getProperty("cloudinary.api_secret")
                ));
            } else {
                System.err.println("Warning: client.properties not found. Using hardcoded credentials temporary.");
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", "dpkehgjjp", "api_key", "168924452148875", "api_secret", "JUjgUwOOFx0UegeGWa3VySf-wW4"
                ));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Instant createInstant(DatePicker j_date, TextField j_time) throws DateTimeParseException {
        LocalDate date = j_date.getValue();
        String timeStr = j_time.getText().trim();

        if (timeStr.matches("^\\d{1,2}:\\d{2}$")) {
            timeStr += ":00";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime time = LocalTime.parse(timeStr, formatter);

        return LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    private double parseMoneyField(String value) throws NumberFormatException {
        if (value == null || value.trim().isEmpty()) throw new NumberFormatException();
        return Double.parseDouble(value.replace(",", "").replace(" ", "").trim());
    }

    @FXML
    void handle_Items() {
        if (j_name.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Tên sản phẩm (*)");
            j_name.requestFocus();
            return;
        }

        if (j_DateStart.getValue() == null || j_TimeStart.getText().trim().isEmpty()) {
            showError("Vui lòng chọn đầy đủ Ngày và Giờ bắt đầu (*)");
            if (j_DateStart.getValue() == null) j_DateStart.requestFocus();
            else j_TimeStart.requestFocus();
            return;
        }

        if (j_DateEnd.getValue() == null || j_TimeEnd.getText().trim().isEmpty()) {
            showError("Vui lòng chọn đầy đủ Ngày và Giờ kết thúc (*)");
            if (j_DateEnd.getValue() == null) j_DateEnd.requestFocus();
            else j_TimeEnd.requestFocus();
            return;
        }

        if (j_StartingPrice.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Giá khởi điểm sản phẩm (*)");
            j_StartingPrice.requestFocus();
            return;
        }
        if (j_MinBid.getText().trim().isEmpty()) {
            showError("Vui lòng nhập mức đấu tối thiểu MinBid (*)");
            j_MinBid.requestFocus();
            return;
        }

        j_ApplyItem.setDisable(true);
        error_Label.setTextFill(Color.BLACK);
        error_Label.setText("Đang xử lý dữ liệu và tải ảnh lên...");
        error_Label.setVisible(true);

        ClientNetworkExecutor.execute(() -> {
            try {
                if (selectedFile != null) {
                    uploadedImageUrl = uploadToCloudinary(selectedFile);
                    if (uploadedImageUrl == null) {
                        throw new Exception("Tải hình ảnh lên Cloudinary thất bại!");
                    }
                }

                Instant start, end;
                try {
                    start = createInstant(j_DateStart, j_TimeStart);
                    end = createInstant(j_DateEnd, j_TimeEnd);

                    if (start.isBefore(Instant.now())) {
                        throw new IllegalArgumentException("Thời gian bắt đầu không được nhỏ hơn thời gian hiện tại!");
                    }
                    if (end.isBefore(start)) {
                        throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu!");
                    }
                } catch (DateTimeParseException e) {
                    showErrorInUIThread("Định dạng giờ không hợp lệ (HH:mm:ss)!");
                    return;
                } catch (IllegalArgumentException e) {
                    showErrorInUIThread(e.getMessage());
                    return;
                }

                double startingPrice;
                double minBid;
                try {
                    startingPrice = parseMoneyField(j_StartingPrice.getText());
                    if (startingPrice <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    showErrorInUIThread("Giá khởi điểm phải là số dương hợp lệ!");
                    return;
                }

                try {
                    minBid = parseMoneyField(j_MinBid.getText());
                    if (minBid <= 0) throw new NumberFormatException();
                    if (minBid > startingPrice * 0.2) {
                        showErrorInUIThread("MinBid không được vượt quá 20% giá khởi điểm!");
                        return;
                    }
                } catch (NumberFormatException e) {
                    showErrorInUIThread("MinBid phải là số dương hợp lệ!");
                    return;
                }

                String itemType = j_ItemType.getValue();
                Map<String, String> extraFields = new HashMap<>();
                extraFields.put("brand", j_brand.getText());
                extraFields.put("model", j_model.getText());
                extraFields.put("manufacturer", j_manufacturer.getText());
                extraFields.put("year", j_year.getText());
                extraFields.put("artist", j_artist.getText());

                Item item = ItemFactory.createItem(
                        itemType, j_name.getText(), j_description.getText(), startingPrice, minBid,
                        start, end, user != null ? user.getUsername() : "Unknown",
                        extraFields, uploadedImageUrl
                );

                client.sendCommand(Command.CREATE_ITEM, item);

            } catch (Exception e) {
                e.printStackTrace();
                showErrorInUIThread("Có lỗi xảy ra trong quá trình đăng sản phẩm!");
            }
        });
    }

    public String uploadToCloudinary(File localFile) {
        try {
            Map uploadResult = cloudinary.uploader().upload(localFile, ObjectUtils.emptyMap());
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @FXML
    void handle_SelectImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File file = fileChooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (file != null) {
            this.selectedFile = file;
            MyImgView.setImage(new Image(file.toURI().toString()));
        }
    }

    @FXML
    void handle_Info(ActionEvent event) {
        String selectedCategory = j_ItemType.getValue();
        categoryPaneMap.forEach((category, pane) -> {
            if (pane != null) {
                pane.setVisible(category.equals(selectedCategory));
            }
        });
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        try {
            if (user != null) {
                client.sendCommand(Command.LOGOUT, user.getUsername());
            }
        } catch (IOException e) {
            System.err.println("Lỗi kết nối khi gửi yêu cầu Đăng xuất: " + e.getMessage());
        }
    }

    @FXML
    void On_ProductList(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/ProductListView.fxml");
    }

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/AccountInfoView.fxml");
    }

    private void showError(String message) {
        error_Label.setTextFill(Color.RED);
        error_Label.setText(message);
        error_Label.setVisible(true);
    }

    private void showErrorInUIThread(String message) {
        Platform.runLater(() -> {
            showError(message);
            j_ApplyItem.setDisable(false);
        });
    }

    @Override
    public void onServerResponse(DataPacket response) {
        if (Command.CREATE_ITEM_RESULT.equals(response.command())) {
            boolean isSuccess = (boolean) response.payload();
            Platform.runLater(() -> {
                j_ApplyItem.setDisable(false);
                if (isSuccess) {
                    error_Label.setTextFill(Color.GREEN);
                    error_Label.setText("Đăng bán sản phẩm thành công!");
                } else {
                    showError("Đăng bán sản phẩm thất bại từ phía máy chủ.");
                }
            });
        }
        if (Command.NOTIFICATION.equals(response.command())) {
            Platform.runLater(() -> {
                ControllerNotificationSeller.handleIncomingToastNotificationSeller(response.payload(), j_textSoDu);
            });
        }
        if (Command.SET_ALLOW_RESULT.equals(response.command())) {
            Map<String, Object> responsePayload = (Map<String, Object>) response.payload();
            boolean isAllow = responsePayload.get("allow") != null && responsePayload.get("allow").toString().equals("true");
            String itemName = "";
            Object auctionObj = responsePayload.get("auction");
            if (auctionObj instanceof Auction) {
                Auction auction = (Auction) auctionObj;
                if (auction.getItem() != null) {
                    itemName = " \"" + auction.getItem().getName() + "\"";
                }
            }

            String finalItemName = itemName;
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Thông báo");
                alert.setHeaderText(null);
                if (!isAllow) {
                    alert.setContentText("Phiên đấu giá sản phẩm" + finalItemName + " của bạn đã bị tạm dừng!");
                    j_ApplyItem.setDisable(true);
                } else {
                    alert.setContentText("Phiên đấu giá sản phẩm" + finalItemName + " của bạn đã được phê duyệt!");
                    j_ApplyItem.setDisable(false);
                }
                alert.showAndWait();
            });
        }
        if (Command.DELETE_ITEM_RESULT.equals(response.command())) {
            Map<String, Object> responsePayload = (Map<String, Object>) response.payload();
            boolean success = (boolean) responsePayload.get("success");
            String itemName = (String) responsePayload.get("itemName");
            if (success) {
                String displayName = (itemName != null) ? " \"" + itemName + "\"" : "";
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thông báo");
                    alert.setHeaderText(null);
                    alert.setContentText("Sản phẩm" + displayName + " của bạn đã bị xóa!");
                    alert.showAndWait();
                });
            }
        }
        if (Command.FORCE_LOGOUT.equals(response.command())) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Tài khoản bị xóa");
                alert.setHeaderText(null);
                alert.setContentText("Tài khoản của bạn đã bị Admin xóa. Ứng dụng sẽ tự đóng.");
                alert.showAndWait();
                System.exit(0);
            });
        }
        if (Command.LOGOUT_RESULT.equals(response.command())) {
            Platform.runLater(() -> {
                client.removeListener(this);
                AuctionClient.getInstance().closeConnection();
                UserSession.cleanUserSession();
                SceneHelper.changeScene((Node) j_LabelName, "/fxml/LoginView.fxml");
            });
        }
        if (Command.NOTIFICATION_BIDDER_PAY.equals(response.command())){
            Platform.runLater(() -> {
                ControllerNotificationSeller.handleSuccessToastNotificationSeller(response.payload(), j_textSoDu, user);
            });
        }
    }
}