# README Server

Tài liệu này giải thích phần server của hệ thống đấu giá. Module `Server` hiện đang chứa cả backend socket server và một số JavaFX controller cũ, vì vậy cần phân biệt rõ hai nhóm:

- Backend socket server: `network`, `service.UserService`, `dao`, `database`, `model`, `utils`.
- JavaFX/UI trong cùng module: các class `Controller*`, `SceneHelper`, `Main`, `Launcher`, `ItemCardController`.

## Kiến trúc Tổng Quan

Luồng backend chính:

```text
Client
  -> gửi DataPacket(command, payload)
  -> AuctionServer nhận socket
  -> ClientHandler đọc DataPacket
  -> RequestHandler tương ứng xử lý command
  -> UserService xử lý nghiệp vụ
  -> DAO đọc/ghi MySQL
  -> Handler gửi DataPacket response về client
```

Với realtime bid:

```text
Client A BID thành công
  -> BidHandler gọi UserService.processBid()
  -> DB cập nhật giá + leading bidder + bidHistory
  -> AuctionServer.broadcastToSpecificAuction(itemId, "BID_UPDATE", payload)
  -> Tất cả client đang xem cùng item nhận cập nhật
```

## Package `network`

`AuctionServer`: entry point của socket server. Class này mở `ServerSocket`, accept client, tạo `ClientHandler`, giữ danh sách `onlineClients`, và broadcast update tới client đang xem cùng auction.

`ClientHandler`: worker cho một client socket. Class này giữ `Socket`, `ObjectInputStream`, `ObjectOutputStream`, user đã login, item đang xem (`viewingItemId`), và bảng `handlers` để route command.

`DataPacket`: gói tin chung giữa client và server. `command` là tên lệnh, `payload` là dữ liệu đi kèm.

`RequestHandler`: interface chung cho mọi handler. Mỗi handler implement `handle(payload, out)`.

`BaseHandler`: class cha của các handler. Cung cấp `gson` và `sendResponse()`.

`LoginHandler`: xử lý `LOGIN`. Nếu login đúng, gắn user vào `ClientHandler` và đưa handler vào `AuctionServer.onlineClients`.

`RegisterHandler`: xử lý `REGISTER`. Chuyển payload thành `User`, gọi `UserService.register()`.

`Creater_ItemHandler`: xử lý `CREATE_ITEM`. Nhận `Item`, gọi service để insert item và tạo auction tương ứng.

`Select_Items`: xử lý `SELECT_ITEMS`. Trả danh sách item trong DB.

`GetAuctionHandler`: xử lý `GET_AUCTION`. Nhận item id và trả về `Auction`.

`SetAuctionHandler`: xử lý `SET_AUCTION`. Dùng để đánh dấu client đang xem item nào; đây là cơ sở để broadcast bid đúng nhóm client.

`BidHandler`: xử lý `BID`. Validate và ghi bid qua `UserService.processBid()`, sau đó broadcast `BID_UPDATE` cho các client đang xem cùng item.

`GetAllAuctionsHandler`: xử lý `GET_ALL_AUCTIONS`. Trả toàn bộ auction.

`UpdateUserHandler`: xử lý `UPDATE_USER`. Hiện service gọi `DAOUser.Update()`, nhưng method DAO này còn là stub nên chưa persist DB.

`ChangePasswordHandler`: xử lý `CHANGE_PASSWORD`. Kiểm tra mật khẩu cũ rồi gọi update user; cũng bị ảnh hưởng bởi `DAOUser.Update()` đang là stub.

`LogoutHandler`: xử lý `LOGOUT`. Hiện `UserService.logout()` đang rỗng nên chưa xóa user khỏi `onlineClients`.

## Package `service`

`UserService`: service backend chính cho socket server. Class này gom logic login, register, tạo item, lấy auction, đặt giá, cập nhật trạng thái auction, cập nhật user và đổi mật khẩu.

`Main`: JavaFX application cũ, load `View1.fxml` và chạy `AuctionEngine`. Không phải entry point socket server khi build jar.

`Launcher`: wrapper gọi `Application.launch(Main.class, args)` cho JavaFX.

`SceneHelper`: helper chuyển scene trong JavaFX.

`ControllerLogin`, `ControllerRegister`, `ControllerAuction`, `ControllerSeller`, `ControllerSetInf`, `ControllerPayment`, `ItemCardController`: các controller UI. Một số controller gọi DAO trực tiếp, không đi qua socket server.

## Package `dao`

`DaoInterface`: interface CRUD chung. Thiết kế hiện tại còn trộn method generic với overload riêng cho `Auction` và `Item`, nên nhiều DAO để stub.

`DAOUser`: thao tác bảng `khach`. Dùng để insert user, kiểm tra username, login, load user theo username, đọc/ghi `status`.

`DAOItems`: thao tác bảng `items`. Dùng để insert item, lấy danh sách item, lấy item theo id, cập nhật `currentHighestBid`.

`DAOAution_Items`: thao tác bảng `auction_items`. Dùng để tạo auction, load auction theo item, update current price, leading bidder, bid history và status.

## Package `database`

`JDBCUtil`: load `server.properties`, tạo JDBC URL và trả về `Connection` mới mỗi lần gọi. Hiện chưa dùng connection pool.

## Package `model.auction`

`Auction`: model phiên đấu giá. Giữ item, seller, status, leading bidder và bid history. Lưu ý `getStatus()` có side effect vì gọi `updateStatusByTime()` và có thể ghi DB.

`AuctionStatus`: enum trạng thái: `OPEN`, `RUNNING`, `FINISHED`, `PAID`, `CANCELED`.

`BidTransaction`: một lần đặt giá, gồm bidder username, amount và bidTime.

`AuctionEngine`: scheduler nền quét các auction định kỳ, load item nếu cần, rồi gọi `auction.getStatus()` để tự chuyển trạng thái theo thời gian.

`AuctionService`: service đấu giá dùng trong nhánh JavaFX UI. Luồng socket server chủ yếu dùng `UserService`.

## Package `model.Items`

`Item`: model sản phẩm đấu giá, gồm id DB, name, description, startingPrice, currentHighestPrice, thời gian đấu giá, sellerId, itemType và img.

`Art`, `Electronics`, `Vehicle`: subclass của `Item`, thêm thuộc tính riêng từng loại và override `getProperties()`.

`ItemType`: enum loại item. Có `fromString()` để map text tiếng Việt sang enum.

`ItemFactory`: tạo đúng subclass `Item` từ loại item và extra field.

`ItemSession`: session tĩnh của JavaFX UI cho item đang chọn. Không phải session socket server.

## Package `model.User`

`User`: model user chung, gồm username, password, name, email/address và role.

`Admin`, `Seller`, `Bidder`: subclass của `User` gắn role cụ thể.

`UserRole`: enum role, có `fromString()` để map text tiếng Việt sang enum.

`UserSession`: session tĩnh của JavaFX UI cho user đang đăng nhập. Không quản lý online socket.

## Package `utils`

`GsonUtils`: tạo `Gson` custom để serialize/deserialize `Instant` và `BidTransaction`. Được `DAOAution_Items` dùng khi lưu/đọc `bidHistory`.

## Luồng Khởi Động Socket Server

1. Chạy `network.AuctionServer.main()`.
2. `AuctionServer` đọc `server.port` từ `server.properties`.
3. `launch()` mở `ServerSocket`.
4. Khi client kết nối, server tạo `ClientHandler`.
5. `ClientHandler` tạo `ObjectOutputStream`, flush, rồi tạo `ObjectInputStream`.
6. `ClientHandler` đăng ký các command handler trong `initHandlers()`.
7. Thread pool chạy `ClientHandler.run()` để đọc request liên tục.

## Luồng LOGIN

1. Client gửi `DataPacket("LOGIN", payload)`.
2. `ClientHandler` route sang `LoginHandler`.
3. `LoginHandler` lấy username/password từ payload.
4. `UserService.loginAndGetUser()` gọi `DAOUser.selectByUsername(username, password)`.
5. Nếu hợp lệ, user được gắn vào `ClientHandler`.
6. `AuctionServer.addOnlineClient(user, handler)` lưu user vào `onlineClients`.
7. Server gửi `LOGIN_RESULT`.

## Luồng Xem Auction

1. Client gửi `SET_AUCTION` với `itemId` và `userId`.
2. `SetAuctionHandler` gọi `clientHandler.setViewingItemId(itemId)`.
3. Handler gọi `UserService.getAuctionByItemId(itemId)`.
4. Service load `Item` bằng `DAOItems.selectById()`.
5. Service load `Auction` bằng `DAOAution_Items.selectByItemId(item)`.
6. Server gửi `SET_AUCTION_RESULT`.

## Luồng Đặt Giá BID

1. Client gửi `BID` với `itemId`, `bidderId`, `amount`.
2. `BidHandler` gọi `UserService.processBid(itemId, bidderId, amount)`.
3. Service load item và kiểm tra `amount > currentHighestPrice`.
4. Service update `items.currentHighestBid`.
5. Service load auction, copy `bidHistory`, thêm `BidTransaction` mới.
6. Service update `auction_items.currentPrice`, `leadingbider`, `bidHistory`.
7. `BidHandler` gửi `BID_RESULT` cho client đặt giá.
8. Nếu thành công, `AuctionServer.broadcastToSpecificAuction()` gửi `BID_UPDATE` cho các client đang xem cùng item.

## Luồng Tự Cập Nhật Trạng Thái Auction

1. `AuctionEngine.startEngine()` tạo task chạy định kỳ.
2. Mỗi tick gọi `UserService.getAllAuctions()`.
3. Engine load `Item` cho auction nếu auction chỉ có `itemId`.
4. Engine gọi `auction.getStatus()`.
5. `Auction.updateStatusByTime()` đổi:
   - trước giờ bắt đầu: `OPEN`
   - sau giờ bắt đầu: `RUNNING`
   - sau giờ kết thúc: `FINISHED`
6. Khi status đổi sang `RUNNING` hoặc `FINISHED`, DAO cập nhật `auction_items.status`.

## Điểm Cần Lưu Ý Khi Bảo Trì

- `DAOUser.Update()` đang là stub, nên update profile và đổi mật khẩu chưa persist DB.
- `UserService.logout()` đang rỗng, nên logout chưa xóa user khỏi `AuctionServer.onlineClients`.
- `DAOUser.Insert()` đang nối chuỗi SQL trực tiếp, có rủi ro SQL injection.
- `Auction.getStatus()` không phải getter thuần vì có thể ghi DB.
- `JDBCUtil.getConnection()` tạo connection mới mỗi lần gọi; chưa có connection pool.
- `Server` module đang trộn backend socket và JavaFX UI, nên khi refactor nên tách rõ hai phần.
