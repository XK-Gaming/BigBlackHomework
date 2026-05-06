# Luồng hoạt động hệ thống (Client ↔ Server)

## 1) Khởi động client

- `controller.Launcher.main()` gọi `Application.launch(Main.class)`.
- `controller.Main.start()`:
  - Đọc `client.properties` để lấy `server.ip` và `server.port` (mặc định `localhost:8080` nếu không đọc được).
  - Gọi `network.AuctionClient.getInstance().connect(ip, port)` để mở socket và tạo `ObjectOutputStream/ObjectInputStream`.
  - Start luồng nền `listenForMessages()` để đọc liên tục `DataPacket` từ server.
  - Load UI ban đầu: `/controller/View1.fxml`.

## 2) Cơ chế giao tiếp dữ liệu

- Client và server trao đổi object dạng `network.DataPacket`:
  - `command`: chuỗi định danh loại message
  - `payload`: dữ liệu đi kèm (Map/model/primitive/…)
- Client gửi:
  - `AuctionClient.sendCommand(command, payload)` -> ghi `DataPacket` lên `ObjectOutputStream`
- Client nhận:
  - luồng nền đọc `DataPacket` -> gọi `currentListener.onServerResponse(packet)`

NOTE quan trọng:

- Kiến trúc hiện tại dùng **1 listener active** tại một thời điểm (`AuctionClient.setListener(listener)`).
  Vì vậy khi đổi màn hình, controller mới phải đăng ký lại listener của mình trong `initialize()`.

## 3) Luồng đăng nhập

- UI: `ControllerLogin.handleRegister()` gửi:
  - `command = LOGIN`
  - `payload = Map{username, password}`
- Server phản hồi:
  - `command = LOGIN_RESULT`
  - `payload = Map{success, message?, user?}`
- Client xử lý:
  - nếu `success=false`: hiển thị lỗi
  - nếu `success=true`: `UserSession.setLoggedInUser(user)` rồi điều hướng theo `UserRole`:
    - BIDDER -> `View3.fxml`
    - SELLER -> `View3.1.fxml`

## 4) Luồng đăng ký

- UI: `ControllerRegister.handleRegister_DangKy()` gửi:
  - `command = REGISTER`
  - `payload = User`
- Server phản hồi:
  - `command = REGISTER_RESULT`
  - `payload = Map{success, message}`
- Client hiển thị thông báo.

## 5) Luồng bidder xem danh sách item

- UI: `ControllerBidder.initialize()` gửi:
  - `command = SELECT_ITEMS`
  - `payload = ""` (chuỗi rỗng)
- Server phản hồi:
  - `command = SELECT_ITEMS_RESULT`
  - `payload = ArrayList<Item>`
- Client:
  - render danh sách bằng `Pagination`
  - click 1 card:
    - `ItemSession.setLoggedInItem(item)`
    - chuyển `View4.fxml` (màn chi tiết/đấu giá)

## 6) Luồng xem phiên đấu giá và đặt giá

### 6.1 Load auction

- UI: `ControllerAuction.initialize()` gửi:
  - `GET_AUCTION` với `payload = itemId`
  - `SET_AUCTION` với `payload = Map{userId, itemId}`
    - mục tiêu: báo server biết client đang theo dõi item nào để push update
- Server phản hồi:
  - `GET_AUCTION_RESULT` với `payload = Auction` (hoặc null nếu không có phiên)
- Client:
  - hiển thị trạng thái
  - chạy `AuctionEngine.watchItem(item, ...)` để cập nhật countdown OPEN/RUNNING/FINISHED theo thời gian

### 6.2 Bid

- UI: `ControllerAuction.On_apply()` gửi:
  - `BID` với `payload = Map{itemId, bidderId, amount}`
- Server phản hồi cho bidder:
  - `BID_RESULT` với `payload = Map{success, message, newPrice?}`
- Server có thể push cho các client đang xem item:
  - `BID_UPDATE` với `payload = Map{itemId, newPrice, bidderId, auction?}`
- Client:
  - cập nhật `item.currentHighestPrice`
  - cập nhật `auction.leadingBidder`
  - refresh label giá + leader

### 6.3 Kết thúc và thanh toán

- `AuctionEngine` chuyển status sang `FINISHED` khi qua `endTime`.
- UI: `ControllerAuction.handleFinishedAuction()`:
  - nếu user là `leadingBidder`: hiển thị chúc mừng và tự chuyển `ViewPaid.fxml`.

## 7) Luồng seller tạo item

- UI: `ControllerSeller.handle_Items()`:
  - (tuỳ chọn) upload ảnh (Cloudinary) để lấy URL
  - tạo `Item` bằng `ItemFactory.createItem(...)`
  - gửi `CREATE_ITEM` với `payload = Item`
- Server phản hồi:
  - `CREATE_ITEM_RESULT` với `payload = boolean`
- Client hiển thị kết quả.

## 8) Luồng cập nhật tài khoản

- UI: `ControllerSetInf` gửi:
  - `UPDATE_USER` với `payload = Map{username, field, value}` (đổi tên/sđt)
  - `CHANGE_PASSWORD` với `payload = Map{username, oldPassword, newPassword}`
  - `LOGOUT` với `payload = username`
- Server phản hồi:
  - `UPDATE_USER_RESULT` / `CHANGE_PASSWORD_RESULT` với `payload = Map{success, message}`
- Client hiển thị message và đồng bộ session (ví dụ update name).

