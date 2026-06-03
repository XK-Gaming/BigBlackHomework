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
import model.auction.Auction;
import model.auction.AuctionStatus;
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

// Màn sửa sản phẩm.
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

    private final AuctionClient client = AuctionClient.getInstance();

    @FXML private Label j_LabelName;
    @FXML private Label j_textSoDu;

    @FXML private TextField txtName;
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtTimeStart;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtTimeEnd;
    @FXML private TextField txtStartPrice;
    @FXML private TextField txtMinBid;
    @FXML private TextArea txtDescription;

    @FXML private VBox vboxAppendNote;
    @FXML private Label lblAppendNoteTitle;
    @FXML private TextArea txtAppendNote;

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
    private ConnectionStatusManager statusManager;

    private ActionEvent currentEvent;
    @FXML
    private javafx.scene.shape.Circle connectionStatus;

    @FXML
    private Label connectionText;

    // Khởi tạo màn hình.
    @FXML
    public void initialize() {
        client.addListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();
        initCloudinary();

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
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(currentUser.getBalance()) + " VNĐ");
        }

        setupDatePickerConstraints();
    }
    // Cấu hình Cloudinary.
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
    // Nhận dữ liệu màn trước.
    public void initData(Item item, AuctionStatus status, boolean hasBids) {
        this.currentItem = item;
        this.currentStatus = status;
        this.itemHasBids = hasBids;
        this.uploadedImageUrl = item.getImg();

        txtName.setText(item.getName());
        String jsonString =  item.getDescription();
        com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(jsonString).getAsJsonObject();
        String pureDescription = jsonObject.get("description").getAsString();
        txtDescription.setText(pureDescription);

        txtStartPrice.setText(String.format("%.0f", item.getStartingPrice()));
        txtMinBid.setText(String.format("%.0f", item.getMinBid()));

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
    // Khóa form theo trạng thái.
    private void applyPermissionRules() {
        setCoreFieldsDisabled(false);
        txtDescription.setDisable(false);
        btnUploadImage.setDisable(false);
        btnSave.setDisable(false);
        vboxAppendNote.setVisible(false);
        vboxAppendNote.setManaged(false);

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
            return;
        }

        if (currentStatus == AuctionStatus.FINISHED || currentStatus == AuctionStatus.PAID) {
            setCoreFieldsDisabled(true);
            txtDescription.setDisable(true);
            btnUploadImage.setDisable(true);
            btnSave.setDisable(true);
        }
    }

    private void setCoreFieldsDisabled(boolean disabled) {
        txtName.setDisable(disabled);
        txtStartPrice.setDisable(disabled);
        txtMinBid.setDisable(disabled);
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

    // Xử lý nút giao diện.
    @FXML
    void On_Save(ActionEvent event) {
        if (txtName.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Tên sản phẩm (*)");
            return;
        }
        if (txtStartPrice.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Giá khởi điểm (*)");
            return;
        }

        if (!txtMinBid.isDisabled() && txtMinBid.getText().trim().isEmpty()) {
            showError("Vui long nhap MinBid (*)");
            return;
        }

        String nameText = txtName.getText().trim();
        String descText = txtDescription.getText().trim();
        String startPriceText = txtStartPrice.getText().trim();
        String minBidText = txtMinBid.getText().trim();
        String itemTypeVal = j_ItemType.getValue();
        String appendNoteText = txtAppendNote.getText().trim();

        String brandText = j_brand.getText() == null ? "" : j_brand.getText().trim();
        String modelText = j_model.getText() == null ? "" : j_model.getText().trim();
        String manufacturerText = j_manufacturer.getText() == null ? "" : j_manufacturer.getText().trim();
        String yearText = j_year.getText() == null ? "" : j_year.getText().trim();
        String artistText = j_artist.getText() == null ? "" : j_artist.getText().trim();

        LocalDate startDate = dpStartDate.getValue();
        String timeStartText = txtTimeStart.getText().trim();
        LocalDate endDate = dpEndDate.getValue();
        String timeEndText = txtTimeEnd.getText().trim();

        this.currentEvent = event;
        btnSave.setDisable(true);
        error_Label.setTextFill(Color.BLACK);
        error_Label.setText("Đang xác thực thông tin và lưu dữ liệu...");
        error_Label.setVisible(true);

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
                            double minBid = Double.parseDouble(minBidText);
                            if (minBid <= 0 || minBid > startingPrice * 0.2) {
                                showErrorInUIThread("MinBid phai lon hon 0 va khong vuot qua 20% gia khoi diem!");
                                return;
                            }
                            currentItem.setMinBid(minBid);
                        } catch (NumberFormatException e) {
                            showErrorInUIThread("Gia khoi diem va MinBid phai la so duong hop le!");
                            return;
                        }

                        try {
                            if (startDate == null || endDate == null) {
                                showErrorInUIThread("Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc!");
                                return;
                            }

                            Instant startInstant = createInstantFromData(startDate, timeStartText);
                            Instant endInstant = createInstantFromData(endDate, timeEndText);

                            if (currentItem.getAuctionStartTime() != null && startInstant.isBefore(Instant.now()) && !startInstant.equals(currentItem.getAuctionStartTime())) {
                                showErrorInUIThread("Mốc thời gian bắt đầu mới chỉnh sửa không được nhỏ hơn hiện tại!");
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

                client.sendCommand(Command.EDIT_ITEM, currentItem);

            } catch (IOException e) {
                e.printStackTrace();
                showErrorInUIThread("Không thể kết nối đến máy chủ hệ thống!");
            }
        });
    }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(network.DataPacket response) {

        // Nhận kết quả sửa sản phẩm.
        if (Command.EDIT_ITEM_RESULT.equals(response.command())) {
            boolean isSuccess = (boolean) response.payload();

            Platform.runLater(() -> {
                if (isSuccess) {
                    error_Label.setTextFill(Color.GREEN);
                    error_Label.setText("Cập nhật thông tin sản phẩm thành công!");

                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Cập nhật thành công!", ButtonType.OK);
                    alert.showAndWait();

                    client.removeListener(this);

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

        // Nhận trạng thái phiên realtime.
        if (Command.UPDATE_AUCTION_STATUS.equals(response.command())) {
            if (response.payload() instanceof Map<?, ?> map) {
                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        long targetItemId = parseLongValue(map.get("itemId"));
                        String newStatusStr = map.get("newStatus") != null ? map.get("newStatus").toString() : "";

                        Platform.runLater(() -> {
                            System.out.println("[Seller Realtime] Sản phẩm " + targetItemId + " đã chuyển sang trạng thái: " + newStatusStr);

                            if (currentItem != null && currentItem.getDatabaseId() == targetItemId) {
                                try {
                                    this.currentStatus = AuctionStatus.valueOf(newStatusStr);
                                    this.currentItem.setAuctionStatus(this.currentStatus);

                                    applyPermissionRules();

                                    error_Label.setTextFill(Color.ORANGE);
                                    error_Label.setText("Trạng thái phòng đấu giá vừa được hệ thống cập nhật thành: " + newStatusStr);
                                    error_Label.setVisible(true);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi xử lý đồng bộ trạng thái phía Seller: " + e.getMessage());
                }
            }
        }

        // Nhận bid realtime.
        if (Command.BID_UPDATE.equals(response.command())) {
            handleBidUpdate(response.payload());
        }

        // Nhận thông báo nạp tiền.
        if (Command.NOTIFICATION_NEW_PAY.equals(response.command())) {
            User user = UserSession.getLoggedInUser();
            Map<String, Object> notifData = (Map<String, Object>) response.payload();
            Item item = (Item) notifData.get("item");
            user.setBalance(user.getBalance() + item.getCurrentHighestPrice());
            Platform.runLater(() -> {
                ControllerNotificationSeller.handleSuccessToastNotificationSeller(response.payload(), j_textSoDu, UserSession.getLoggedInUser());
            });
        }

        // Nhận kết quả duyệt/dừng.
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
                Alert alert = new Alert(Alert.AlertType.INFORMATION, isAllow ? "Phiên đấu giá" + finalItemName + " đã được phê duyệt!" : "Phiên đấu giá" + finalItemName + " đã bị tạm dừng!", ButtonType.OK);
                alert.showAndWait();
                if (!isAllow) {
                    On_Back(new ActionEvent(j_textSoDu, null));
                }
            });
        }

        // Nhận kết quả xóa sản phẩm.
        if (Command.DELETE_ITEM_RESULT.equals(response.command())) {
            Map<String, Object> resData = (Map<String, Object>) response.payload();
            boolean success = (boolean) resData.get("success");
            String itemName = (String) resData.get("itemName");
            if (success) {
                String displayName = (itemName != null) ? " \"" + itemName + "\"" : "";
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Sản phẩm" + displayName + " này đã bị xóa!", ButtonType.OK);
                    alert.showAndWait();
                    On_Back(new ActionEvent(j_textSoDu, null));
                });
            }
        }

        // Nhận lệnh đăng xuất cưỡng chế.
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

        // Nhận kết quả đăng xuất.
        if (Command.LOGOUT_RESULT.equals(response.command())) {
            Platform.runLater(() -> {

                client.removeListener(this);
                AuctionClient.getInstance().closeConnection();
                UserSession.cleanUserSession();
                SceneHelper.changeScene((Node) j_LabelName, "/fxml/LoginView.fxml");

            });
        }
    }
    // Nhận bid realtime.
    private void handleBidUpdate(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return;
        }

        try {
            long targetItemId = parseLongValue(map.get("itemId"));
            if (currentItem == null || currentItem.getDatabaseId() != targetItemId) {
                return;
            }

            Platform.runLater(() -> {
                Object itemPayload = map.get("item");
                if (itemPayload instanceof Item updatedItem) {
                    mergeRealtimeItem(updatedItem);
                }

                Object pricePayload = map.get("newPrice");
                if (pricePayload instanceof Number number) {
                    currentItem.setCurrentHighestPrice(number.doubleValue());
                } else if (pricePayload != null) {
                    try {
                        currentItem.setCurrentHighestPrice(Double.parseDouble(String.valueOf(pricePayload)));
                    } catch (NumberFormatException ignored) {
                    }
                }

                itemHasBids = true;
                applyPermissionRules();
                error_Label.setTextFill(Color.ORANGE);
                error_Label.setText("Sáº£n pháº©m vá»«a cÃ³ lÆ°á»£t bid má»›i. Quyá»n chá»‰nh sá»­a Ä‘Ã£ Ä‘Æ°á»£c cáº­p nháº­t.");
                error_Label.setVisible(true);
            });
        } catch (Exception e) {
            System.err.println("Lá»—i xá»­ lÃ½ BID_UPDATE trong EditProduct: " + e.getMessage());
        }
    }
    // Gộp item realtime.
    private void mergeRealtimeItem(Item updatedItem) {
        currentItem.setCurrentHighestPrice(updatedItem.getCurrentHighestPrice());
        currentItem.setAuctionStartTime(updatedItem.getAuctionStartTime());
        currentItem.setAuctionEndTime(updatedItem.getAuctionEndTime());
        if (updatedItem.getAuctionStatus() != null) {
            currentStatus = updatedItem.getAuctionStatus();
            currentItem.setAuctionStatus(currentStatus);
        }
    }
    // Đọc dữ liệu.
    private long parseLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String itemIdStr = String.valueOf(value);
        return itemIdStr.contains(".") ? Double.valueOf(itemIdStr).longValue() : Long.parseLong(itemIdStr);
    }
    // Tạo dữ liệu.
    public Instant createInstantFromData(LocalDate date, String timeStr) throws DateTimeParseException {
        if (timeStr.matches("^\\d{1,2}:\\d{2}$")) {
            timeStr += ":00";
        }
        return LocalDateTime.of(date, LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss")))
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }
    // Upload ảnh.
    public String uploadToCloudinary(File localFile) {
        try {
            Map uploadResult = cloudinary.uploader().upload(localFile, ObjectUtils.emptyMap());
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Xử lý thao tác.
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

    // Xử lý nút giao diện.
    @FXML
    void On_Back(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/ProductListView.fxml");
    }

    // Đăng xuất.
    @FXML
    void On_LogOut(ActionEvent event) {
        client.removeListener(this);
        try {
            client.sendCommand(Command.LOGOUT, UserSession.getLoggedInUser().getUsername());
        } catch (IOException e) {
            System.err.println("Lỗi kết nối khi gửi yêu cầu Đăng xuất: " + e.getMessage());

        }
    }
    // Đọc dữ liệu.
    private void parseAndSetAuctionTime(Instant instant, DatePicker datePicker, TextField timeField) {
        if (instant == null) return;
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        datePicker.setValue(ldt.toLocalDate());
        timeField.setText(ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
    // Cấu hình ban đầu.
    private void setupDatePickerConstraints() {
        dpStartDate.setDayCellFactory(picker -> new DateCell() {
            // Cập nhật sản phẩm.
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        dpEndDate.setDayCellFactory(picker -> new DateCell() {
            // Cập nhật sản phẩm.
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
    // Hiển thị giao diện.
    private void showError(String message) {
        error_Label.setTextFill(Color.RED);
        error_Label.setText(message);
        error_Label.setVisible(true);
    }
    // Hiển thị giao diện.
    private void showErrorInUIThread(String message) {
        Platform.runLater(() -> {
            showError(message);
            btnSave.setDisable(false);
        });
    }

    // Xử lý thao tác.
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
