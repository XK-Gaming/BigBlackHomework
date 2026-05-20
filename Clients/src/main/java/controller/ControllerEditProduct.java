package controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.Item;
import model.User.User;
import model.User.UserSession;
import model.auction.AuctionStatus;
import network.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ControllerEditProduct implements ServerListener {

    private File selectedFile = null;
    private String uploadedImageUrl = null;
    private Cloudinary cloudinary;

    private Item currentItem;
    private AuctionStatus currentStatus;
    private boolean itemHasBids;

    private final String[] categoryList = new String[]{"Mỹ thuật", "Điện tử", "Phương tiện giao thông"};
    private final Map<String, HBox> categoryPaneMap = new HashMap<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Lấy instance quản lý socket tương tự như bên ControllerProductList
    private final AuctionClient client = AuctionClient.getInstance();

    @FXML private Label j_LabelName;
    @FXML private Label j_textSoDu;

    @FXML private TextField txtName;
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtTimeStart;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtTimeEnd;
    @FXML private TextField txtStartPrice;
    @FXML private TextArea txtDescription;

    // Khối phục vụ luật Append Note (RUNNING + Has Bids)
    @FXML private VBox vboxAppendNote;
    @FXML private Label lblAppendNoteTitle;
    @FXML private TextArea txtAppendNote;

    // Trường mở rộng loại mặt hàng
    @FXML private ComboBox<String> j_ItemType;
    @FXML private HBox j_paneArt;
    @FXML private HBox j_paneElectronics;
    @FXML private HBox j_paneVehicle;
    @FXML private TextField j_year;
    @FXML private TextField j_manufacturer;
    @FXML private TextField j_model;
    @FXML private TextField j_artist;
    @FXML private TextField j_brand;

    @FXML private ImageView MyImgView;
    @FXML private Button btnUploadImage;
    @FXML private Button btnSave;
    @FXML private Label error_Label;

    // Biến lưu lại Event Action để dùng khi chuyển Scene sau khi nhận phản hồi từ luồng mạng thành công
    private ActionEvent currentEvent;

    @FXML
    public void initialize() {
        // Đăng ký lắng nghe các gói tin từ Server trả về cho View này
        client.setListener(this);

        initCloudinary();

        // Đăng ký ánh xạ các pane mở rộng
        categoryPaneMap.put("Mỹ thuật", j_paneArt);
        categoryPaneMap.put("Điện tử", j_paneElectronics);
        categoryPaneMap.put("Phương tiện giao thông", j_paneVehicle);

        j_ItemType.getItems().setAll(categoryList);

        j_ItemType.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            handle_Info(null);
        });

        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            j_LabelName.setText(currentUser.getName());
            j_textSoDu.setText("0 VNĐ");
        }

        setupDatePickerConstraints();
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
                System.err.println("Warning: Properties not found. Using fallback hardcoded credentials.");
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", "dpkehgjjp", "api_key", "168924452148875", "api_secret", "JUjgUwOOFx0UegeGWa3VySf-wW4"
                ));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Nhận dữ liệu truyền từ danh sách sản phẩm và thiết lập giao diện
     */
    public void initData(Item item, AuctionStatus status, boolean hasBids) {
        this.currentItem = item;
        this.currentStatus = status;
        this.itemHasBids = hasBids;
        this.uploadedImageUrl = item.getImg();

        // Đổ dữ liệu lên các trường
        txtName.setText(item.getName());
        txtDescription.setText(item.getDescription());
        txtStartPrice.setText(String.format("%.0f", item.getStartingPrice()));

        parseAndSetAuctionTime(item.getAuctionStartTime(), dpStartDate, txtTimeStart);
        parseAndSetAuctionTime(item.getAuctionEndTime(), dpEndDate, txtTimeEnd);

        if (item.getImg() != null && !item.getImg().trim().isEmpty()) {
            try {
                MyImgView.setImage(new Image(item.getImg(), true));
            } catch (Exception e) {
                System.err.println("Không thể nạp ảnh cũ từ URL: " + item.getImg());
            }
        }

        if (item.getItemType() != null) {
            String uiCategoryType = convertModelTypeToUiType(item.getItemType().toString());
            j_ItemType.setValue(uiCategoryType);
            handle_Info(null);

            Map<String, String> props = item.getProperties();
            if (props != null) {
                j_brand.setText(props.getOrDefault("brand", ""));
                j_model.setText(props.getOrDefault("model", ""));
                j_manufacturer.setText(props.getOrDefault("manufacturer", ""));
                j_year.setText(props.getOrDefault("year", ""));
                j_artist.setText(props.getOrDefault("artist", ""));
            }
        }

        vboxAppendNote.setVisible(false);
        vboxAppendNote.setManaged(false);

        applyPermissionRules();
    }

    private void applyPermissionRules() {
        if (currentStatus == AuctionStatus.OPEN || currentStatus == AuctionStatus.CANCELLED) {
            return;
        }

        if (currentStatus == AuctionStatus.RUNNING) {
            setCoreFieldsDisabled(true);

            if (!itemHasBids) {
                txtDescription.setDisable(false);
                btnUploadImage.setDisable(false);
            } else {
                txtDescription.setDisable(true);
                btnUploadImage.setDisable(true);

                vboxAppendNote.setVisible(true);
                vboxAppendNote.setManaged(true);
                txtAppendNote.setPromptText("Nhập nội dung thông tin bổ sung đính kèm tại đây...");
            }
        }
    }

    private void setCoreFieldsDisabled(boolean disabled) {
        txtName.setDisable(disabled);
        txtStartPrice.setDisable(disabled);
        j_ItemType.setDisable(disabled);
        dpStartDate.setDisable(disabled);
        txtTimeStart.setDisable(disabled);
        dpEndDate.setDisable(disabled);
        txtTimeEnd.setDisable(disabled);
        j_brand.setDisable(disabled);
        j_model.setDisable(disabled);
        j_manufacturer.setDisable(disabled);
        j_year.setDisable(disabled);
        j_artist.setDisable(disabled);
    }

    @FXML
    void On_Save(ActionEvent event) {
        // --- BƯỚC 1: VALIDATE UI (Chạy trên JavaFX Application Thread) ---
        if (txtName.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Tên sản phẩm (*)");
            return;
        }
        if (txtStartPrice.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Giá khởi điểm (*)");
            return;
        }

        // 🌟 TRÍCH XUẤT TOÀN BỘ DỮ LIỆU UI RA BIẾN SƠ CẤP TRƯỚC KHI VÀO LUỒNG NỀN
        // Điều này giúp tránh NullPointerException và không gây đơ giao diện
        String nameText = txtName.getText().trim();
        String descText = txtDescription.getText().trim();
        String startPriceText = txtStartPrice.getText().trim();
        String itemTypeVal = j_ItemType.getValue();

        String appendNoteText = txtAppendNote.getText().trim();

        String brandText = j_brand.getText() == null ? "" : j_brand.getText().trim();
        String modelText = j_model.getText() == null ? "" : j_model.getText().trim();
        String manufacturerText = j_manufacturer.getText() == null ? "" : j_manufacturer.getText().trim();
        String yearText = j_year.getText() == null ? "" : j_year.getText().trim();
        String artistText = j_artist.getText() == null ? "" : j_artist.getText().trim();

        // Đọc dữ liệu ngày giờ (DatePicker và TextField cũng phải đọc trước)
        LocalDate startDate = dpStartDate.getValue();
        String timeStartText = txtTimeStart.getText().trim();
        LocalDate endDate = dpEndDate.getValue();
        String timeEndText = txtTimeEnd.getText().trim();

        this.currentEvent = event; // Lưu trữ sự kiện chuyển trang
        btnSave.setDisable(true);
        error_Label.setTextFill(Color.BLACK);
        error_Label.setText("Đang xác thực thông tin và lưu dữ liệu...");
        error_Label.setVisible(true);

        // --- BƯỚC 2: XỬ LÝ BACKGROUND VÀ PHÁT LỆNH BẤT ĐỒNG BỘ ---
        ClientNetworkExecutor.execute(() -> {
            try {
                if (selectedFile != null) {
                    String newUrl = uploadToCloudinary(selectedFile);
                    if (newUrl != null) {
                        uploadedImageUrl = newUrl;
                    } else {
                        showErrorInUIThread("Lỗi tải hình ảnh mới lên hệ thống lưu trữ cloud!");
                        return;
                    }
                }

                // --- BƯỚC 3: ĐÓNG GÓI THÔNG TIN (Chỉ dùng biến đã trích xuất, không chạm vào UI nữa) ---
                if (currentStatus == AuctionStatus.RUNNING && itemHasBids) {
                    if (appendNoteText.isEmpty()) {
                        showErrorInUIThread("Vui lòng nhập nội dung thông tin bổ sung!");
                        return;
                    }

                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    String timestamp = dtf.format(LocalDateTime.now());
                    String appendedDescription = currentItem.getDescription() + "\n[* Cập nhật ngày " + timestamp + ": " + appendNoteText + "]";

                    currentItem.setDescription(appendedDescription);
                } else {
                    currentItem.setName(nameText);
                    currentItem.setDescription(descText);
                    currentItem.setImg(uploadedImageUrl);

                    // Thuộc tính mở rộng (Sử dụng an toàn các biến String cục bộ)
                    Map<String, String> extraFields = new HashMap<>();
                    extraFields.put("brand", brandText.isEmpty() ? null : brandText);
                    extraFields.put("model", modelText.isEmpty() ? null : modelText);
                    extraFields.put("manufacturer", manufacturerText.isEmpty() ? null : manufacturerText);
                    extraFields.put("year", yearText.isEmpty() ? null : yearText);
                    extraFields.put("artist", artistText.isEmpty() ? null : artistText);

                    currentItem.setProperties(extraFields);
                    currentItem.setItemType(model.Items.ItemType.fromString(itemTypeVal));

                    if (currentStatus == AuctionStatus.OPEN || currentStatus == AuctionStatus.CANCELLED) {
                        try {
                            double startingPrice = Double.parseDouble(startPriceText);
                            if (startingPrice <= 0) throw new NumberFormatException();
                            currentItem.setStartingPrice(startingPrice);
                        } catch (NumberFormatException e) {
                            showErrorInUIThread("Giá khởi điểm nhập vào phải là số dương hợp lệ!");
                            return;
                        }

                        try {
                            // Tạo Instant từ các biến Date/String an toàn đã bóc tách
                            if (startDate == null || endDate == null) {
                                showErrorInUIThread("Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc!");
                                return;
                            }

                            Instant startInstant = createInstantFromData(startDate, timeStartText);
                            Instant endInstant = createInstantFromData(endDate, timeEndText);

                            if (startInstant.isBefore(Instant.now())) {
                                showErrorInUIThread("Thời gian bắt đầu chỉnh sửa không được nhỏ hơn hiện tại!");
                                return;
                            }
                            if (endInstant.isBefore(startInstant)) {
                                showErrorInUIThread("Thời gian kết thúc phải sau mốc bắt đầu!");
                                return;
                            }

                            currentItem.setAuctionStartTime(startInstant);
                            currentItem.setAuctionEndTime(endInstant);
                        } catch (DateTimeParseException e) {
                            showErrorInUIThread("Định dạng giờ nhập vào không đúng quy chuẩn (HH:mm:ss)!");
                            return;
                        }
                    }
                }

                // Gửi lệnh qua luồng mạng lên Server
                client.sendCommand(Command.EDIT_ITEM, currentItem);

            } catch (IOException e) {
                e.printStackTrace();
                showErrorInUIThread("Không thể kết nối đến máy chủ hệ thống!");
            }
        });
    }

    /**
     * 🌟 ĐỒNG BỘ KIẾN TRÚC: Hàm nhận kết quả phản hồi bất đồng bộ từ EditItemHandler của Server
     */
    @Override
    public void onServerResponse(DataPacket response) {
        if (Command.EDIT_ITEM_RESULT.equals(response.command())) {
            // Ép kiểu payload nhận được (EditItemHandler gửi về gói tin Boolean)
            boolean isSuccess = (boolean) response.payload();

            Platform.runLater(() -> {
                if (isSuccess) {
                    error_Label.setTextFill(Color.GREEN);
                    error_Label.setText("Cập nhật thông tin sản phẩm thành công!");

                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Cập nhật thành công!", ButtonType.OK);
                    alert.showAndWait();

                    // Quay về danh sách sản phẩm bằng Event đã lưu giữ trước đó
                    if (currentEvent != null) {
                        On_Back(currentEvent);
                    }
                } else {
                    error_Label.setTextFill(Color.RED);
                    error_Label.setText("Cập nhật thất bại từ hệ thống hoặc dính lỗi xung đột!");
                    btnSave.setDisable(false);
                }
            });
        }
    }

    public Instant createInstantFromData(LocalDate date, String timeStr) throws DateTimeParseException {
        if (timeStr.matches("^\\d{1,2}:\\d{2}$")) {
            timeStr += ":00";
        }
        return LocalDateTime.of(date, LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss")))
                .atZone(ZoneId.systemDefault())
                .toInstant();
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
    void On_Back(ActionEvent event) {
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/ProductListView.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/LoginView.fxml");
    }

    private void parseAndSetAuctionTime(Instant instant, DatePicker datePicker, TextField timeField) {
        if (instant == null) return;
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        datePicker.setValue(ldt.toLocalDate());
        timeField.setText(ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }


    private void setupDatePickerConstraints() {
        dpStartDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        dpEndDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate minDate = (dpStartDate.getValue() != null) ? dpStartDate.getValue() : LocalDate.now();
                setDisable(empty || date.isBefore(minDate));
            }
        });
    }

    private String convertModelTypeToUiType(String modelType) {
        if (modelType == null) return categoryList[0];
        return switch (modelType) {
            case "ART", "Mỹ thuật" -> "Mỹ thuật";
            case "ELECTRONICS", "Điện tử" -> "Điện tử";
            case "VEHICLE", "Phương tiện giao thông" -> "Phương tiện giao thông";
            default -> categoryList[0];
        };
    }

    private void showError(String message) {
        error_Label.setTextFill(Color.RED);
        error_Label.setText(message);
        error_Label.setVisible(true);
    }

    private void showErrorInUIThread(String message) {
        Platform.runLater(() -> {
            showError(message);
            btnSave.setDisable(false);
        });
    }

    @FXML
    void handle_Info(ActionEvent event) {
        String selectedCategory = j_ItemType.getValue();

        categoryPaneMap.forEach((category, pane) -> {
            if (pane != null) {
                boolean isMatch = category.equals(selectedCategory);
                pane.setVisible(isMatch);
                pane.setManaged(isMatch);
            }
        });

        if (!"Mỹ thuật".equals(selectedCategory)) {
            j_artist.setText("");
        }
        if (!"Điện tử".equals(selectedCategory)) {
            j_brand.setText("");
            j_model.setText("");
        }
        if (!"Phương tiện giao thông".equals(selectedCategory)) {
            j_manufacturer.setText("");
            j_year.setText("");
        }
    }
}