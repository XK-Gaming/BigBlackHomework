# Clients module (Client-side) — Tài liệu tổng quan

## Mục tiêu module

Module `Clients` là ứng dụng JavaFX phía người dùng cuối, dùng để:

- đăng nhập / đăng ký
- xem danh sách sản phẩm
- xem chi tiết sản phẩm và tham gia đấu giá (bid)
- người bán đăng sản phẩm mới
- cập nhật thông tin tài khoản (đổi tên/sđt/mật khẩu) và logout

## Cấu trúc package

### `controller`

Chứa các JavaFX Controller và tiện ích điều hướng màn hình.

- `Main`: JavaFX `Application` chính, đọc cấu hình từ `client.properties`, connect server, load `View1.fxml`.
- `Launcher`: `main()` entry để launch JavaFX.
- `SceneHelper`: hàm tiện ích chuyển màn bằng cách load FXML trong `/controller/`.
- `ControllerLogin`: màn đăng nhập; gửi `LOGIN`, nhận `LOGIN_RESULT`.
- `ControllerRegister`: màn đăng ký; gửi `REGISTER`, nhận `REGISTER_RESULT`.
- `ControllerBidder`: màn người đấu giá xem danh sách; gửi `SELECT_ITEMS`, nhận `SELECT_ITEMS_RESULT`.
- `ItemCardController`: controller của `AssetCard.fxml`, bind dữ liệu `Item` lên card và cập nhật trạng thái theo thời gian.
- `ControllerAuction`: màn phiên đấu giá; gửi `GET_AUCTION`, `SET_AUCTION`, `BID`; nhận `GET_AUCTION_RESULT`, `BID_UPDATE`, `BID_RESULT`.
- `ControllerSeller`: màn người bán tạo item; (tuỳ chọn) upload ảnh, gửi `CREATE_ITEM`, nhận `CREATE_ITEM_RESULT`.
- `ControllerSetInf`: màn cài đặt tài khoản; gửi `UPDATE_USER`, `CHANGE_PASSWORD`, `LOGOUT`; nhận `UPDATE_USER_RESULT`, `CHANGE_PASSWORD_RESULT`.

### `network`

Tầng giao tiếp TCP socket và dispatch message.

- `AuctionClient`: singleton quản lý socket và object stream; gửi/nhận `DataPacket`; chạy luồng nền lắng nghe server.
- `DataPacket`: gói tin `{command, payload}` dùng trong object stream.
- `ServerListener`: interface callback cho controller/service nhận response.
- `ClientNetworkExecutor`: thread pool nhỏ cho các tác vụ I/O ngắn để tránh block UI.

### `model`

Model phía client (một phần dùng cho UI, một phần là object nhận từ server).

- `model.User`: `User`, `Bidder`, `Seller`, `Admin`, `UserRole`, `UserSession` (lưu user đang đăng nhập).
- `model.Items`: `Item` + các subclass (`Electronics`, `Vehicle`, `Art`), `ItemFactory`, `ItemSession` (lưu item đang chọn), `ItemType`.
- `model.auction`: `Auction`, `AuctionStatus`, `BidTransaction`, `AuctionEngine` (đếm thời gian OPEN/RUNNING/FINISHED), `AuctionService` (request/response kiểu synchronous).
- `model.observer`: `AuctionObserver` (observer pattern, chủ yếu phục vụ phía model).
- `model.exception`: `AuctionException`.
- `model.Entity`: `Entity` (base class cho model có `id/createdAt`).

## Tài nguyên (resources)

- `src/main/resources/controller/*.fxml`: UI layout JavaFX.
- `src/main/resources/client.properties`: cấu hình IP/port server.

