# Hệ thống đấu giá trực tuyến

## 1. Mô tả bài toán và phạm vi hệ thống

Hệ thống đấu giá trực tuyến cho phép người dùng đăng sản phẩm, tham gia đặt giá, theo dõi phiên đấu giá, nạp tiền và thanh toán sản phẩm thắng đấu giá.

Phạm vi hệ thống gồm:

- Client JavaFX cho Admin, Seller và Bidder.
- Server xử lý nghiệp vụ, đồng bộ dữ liệu và đã được đóng gói JAR để chạy trên Microsoft Azure.
- Database MySQL do Server sử dụng, Client không cần cài database riêng.
- Giao tiếp Client/Server qua TCP Socket.

## 2. Công nghệ sử dụng, môi trường chạy và yêu cầu cài đặt

Công nghệ sử dụng:

- Java 21.
- Maven multi-module.
- JavaFX 21.
- TCP Socket và Object Stream.
- MySQL.
- HikariCP.
- Gson/Jackson.
- Cloudinary SDK.
- JUnit 5.
- Microsoft Azure.

Môi trường chạy Client:

- Windows, Linux hoặc macOS.
- JDK 21 trở lên.
- Máy có giao diện đồ họa để mở JavaFX.
- Có kết nối mạng tới Server Azure.
- Có thể dùng Maven Wrapper có sẵn trong dự án, không bắt buộc cài Maven global.

Cấu hình Server mặc định trong `Clients/src/main/resources/client.properties`:

```properties
server.ip=20.188.113.2
server.port=8080
```

Kiểm tra Java:

```bash
java -version
```

## 3. Cấu trúc thư mục và module chính

```text
.
|-- pom.xml                      # Parent Maven POM
|-- mvnw / mvnw.cmd              # Maven Wrapper
|-- Common/                      # Model và network class dùng chung
|-- Server/                      # Server xử lý nghiệp vụ
|   |-- src/main/java/dao/        # Truy cập dữ liệu
|   |-- src/main/java/database/   # Kết nối database
|   |-- src/main/java/network/    # Socket server và handler
|   `-- src/main/java/service/    # Logic user, auction, bid, payment
`-- Clients/                     # Ứng dụng JavaFX Client
    |-- src/main/java/controller/ # Controller giao diện
    |-- src/main/java/network/    # Kết nối tới Server
    |-- src/main/resources/fxml/  # Màn hình FXML
    |-- src/main/resources/css/   # Giao diện CSS
    `-- src/main/resources/       # Cấu hình và tài nguyên
```

## 4. Câu lệnh chạy chương trình

Server đã chạy sẵn trên Microsoft Azure. Người dùng chỉ cần build và chạy Client.

Windows:

```powershell
.\mvnw.cmd -pl Clients -am clean package -DskipTests
java -jar Clients\target\Clients-1.0-SNAPSHOT.jar
```

Linux/macOS:

```bash
chmod +x mvnw
./mvnw -pl Clients -am clean package -DskipTests
java -jar Clients/target/Clients-1.0-SNAPSHOT.jar
```

## 5. Hướng dẫn chạy Server/Client theo thứ tự

1. Đảm bảo Server Azure đang hoạt động với cấu hình:

```properties
server.ip=20.188.113.2
server.port=8080
```

2. Kiểm tra máy chạy Client đã có JDK 21 trở lên:

```bash
java -version
```

3. Build Client theo hệ điều hành đang dùng.

4. Chạy Client bằng file `Clients-1.0-SNAPSHOT.jar`.

5. Đăng ký hoặc đăng nhập tài khoản trên giao diện Client.

## 6. Danh sách chức năng đã hoàn thành

Chức năng chung:

- Đăng ký, đăng nhập và đăng xuất.
- Phân quyền Admin, Seller, Bidder.
- Cập nhật thông tin tài khoản.
- Đổi mật khẩu.
- Theo dõi trạng thái kết nối Client/Server.
- Xử lý tài khoản đăng nhập trùng và force logout.

Chức năng Admin:

- Xem dashboard tổng quan.
- Quản lý người dùng.
- Quản lý sản phẩm.
- Duyệt hoặc dừng phiên đấu giá.
- Xem lịch sử bid.
- Duyệt, từ chối và xóa yêu cầu nạp tiền.

Chức năng Seller:

- Đăng sản phẩm đấu giá.
- Upload ảnh sản phẩm.
- Xem danh sách sản phẩm đã đăng.
- Sửa và xóa sản phẩm.
- Theo dõi trạng thái phiên đấu giá.
- Nhận thông báo khi Bidder thanh toán.

Chức năng Bidder:

- Xem và tìm kiếm sản phẩm đấu giá.
- Xem chi tiết phiên đấu giá.
- Đặt bid thủ công.
- Dùng AutoBid với `maxBidAllow` và `bidGap`.
- Xem lịch sử đấu giá.
- Bid lại nhanh khi bị vượt giá.
- Nạp tiền và xem lịch sử nạp tiền.
- Thanh toán sản phẩm thắng đấu giá.
- Nhận cập nhật realtime về giá, trạng thái phiên và số dư.

Chức năng Server:

- Quản lý nhiều Client kết nối đồng thời.
- Xử lý request theo `Command`.
- Đồng bộ giá và trạng thái đấu giá realtime.
- Tự động cập nhật trạng thái phiên theo thời gian.
- Gia hạn phiên khi có bid sát giờ kết thúc.
- Kiểm tra bid theo giá hiện tại, `MinBid`, trạng thái phiên và số dư.
- Hoàn tiền người bị vượt bid và trừ tiền người đặt bid mới.
- Đồng bộ số dư sau bid, nạp tiền và thanh toán.
- Lưu dữ liệu user, item, auction, bid history và deposit history vào MySQL.
