package service;

import dao.DAOAuction_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.Items.Item;
import model.Items.ItemType;
import model.User.Bidder;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.exception.BidRejectedException;
import model.exception.NotFoundException;
import model.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ## JUnit: test UserService xu ly nghiep vu truoc khi ghi DB hoac tra ket qua ve handler.
 */
class UserServiceTest {

    /**
     * ## Test dang nhap hop le: DAO tra user dung password thi service tra user cho handler.
     */
    @Test
    void loginReturnsUserWhenCredentialsMatch() throws Exception {
        User user = new Bidder("bidder1", "secret", "Bidder One", "bidder@example.com");
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = user;
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertSame(user, service.loginAndGetUser("bidder1", "secret"));
    }

    /**
     * ## Test dang nhap sai: service nem UnauthorizedException truoc khi tra ve client.
     */
    @Test
    void loginThrowsUnauthorizedWhenDaoReturnsNoUser() throws Exception {
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(null), new FakeAuctionDao(null));

        assertThrows(UnauthorizedException.class, () -> service.loginAndGetUser("bidder1", "wrong"));
    }

    /**
     * ## Test item khong ton tai: processBid dung o buoc select item va nem NotFoundException.
     */
    @Test
    void processBidThrowsNotFoundWhenItemDoesNotExist() throws Exception {
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(null), new FakeAuctionDao(null));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.processBid("99", "bidder1", 120));

        assertEquals("item", exception.getResource());
    }

    /**
     * ## Test seller tu dat gia: service tu choi voi reason SELLER_BID truoc khi load auction.
     */
    @Test
    void processBidRejectsSellerBiddingOwnItem() throws Exception {
        Item item = item("seller", 100);
        FakeAuctionDao auctionDao = new FakeAuctionDao(null);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "seller", 120));

        assertEquals(BidRejectedException.Reason.SELLER_BID, exception.getReason());
        assertFalse(auctionDao.selectCalled);
    }

    /**
     * ## Test dat bid thap hon gia hien tai: service tra reason PRICE_TOO_LOW truoc khi luu DB.
     */
    @Test
    void processBidRejectsPriceLowerThanCurrentPrice() throws Exception {
        Item item = item("seller", 100);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), new FakeAuctionDao(null));

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 100));

        assertEquals(BidRejectedException.Reason.PRICE_TOO_LOW, exception.getReason());
    }

    /**
     * ## Test auction khong ton tai: item co nhung auction null thi service nem NotFoundException resource auction.
     */
    @Test
    void processBidThrowsNotFoundWhenAuctionDoesNotExist() throws Exception {
        Item item = item("seller", 100);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), new FakeAuctionDao(null));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.processBid("1", "bidder1", 120));

        assertEquals("auction", exception.getResource());
    }

    /**
     * ## Test auction da dong/chua chay: service tu choi reason NOT_RUNNING truoc khi tao transaction DB.
     */
    @Test
    void processBidRejectsAuctionThatIsNotRunning() throws Exception {
        Item item = item("seller", 100);
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        auction.setStatus(AuctionStatus.FINISHED);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), new FakeAuctionDao(auction));

        BidRejectedException exception = assertThrows(BidRejectedException.class,
                () -> service.processBid("1", "bidder1", 120));

        assertEquals(BidRejectedException.Reason.NOT_RUNNING, exception.getReason());
    }

    /**
     * ## Test anti-sniping: bid hop le trong 60 giay cuoi thi keo dai gio ket thuc them 90 giay.
     */

    /**
     * ## Test anti-sniping: bid ngoai 60 giay cuoi thi khong doi thoi gian ket thuc.
     */
    @Test
    void processBidDoesNotExtendAuctionEndTimeOutsideLastMinute() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Instant originalEnd = now.plusSeconds(61);
        Item item = item("seller", 100, now.minusSeconds(3600), originalEnd);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        service.processBid("1", "bidder1", 150);

        assertEquals(originalEnd, item.getAuctionEndTime());
        assertEquals(1, itemDao.updateCalls);
        assertEquals(1, auctionDao.updateCalls);
    }

    /**
     * ## Test anti-sniping: hai bid lien tiep cung luc khong lam cong don 2 lan neu bid thu hai khong con nam trong 60 giay cuoi.
     */
    @Test
    void processBidDoesNotDoubleExtendForImmediateConsecutiveBids() throws Exception {
        Instant now = Instant.parse("2026-05-18T10:00:00Z");
        Instant originalEnd = now.plusSeconds(30);
        Instant firstExtendedEnd = originalEnd.plusSeconds(UserService.ANTI_SNIPING_EXTENSION_SECONDS);
        Item item = item("seller", 100, now.minusSeconds(3600), originalEnd);
        FakeItemDao itemDao = new FakeItemDao(item);
        itemDao.updateResult = 1;
        FakeAuctionDao auctionDao = new FakeAuctionDao(runningAuction(item));
        auctionDao.updateResult = 1;
        FakeUserDao userDao = new FakeUserDao();
        userDao.addUser(new Bidder("bidder1", "secret", "Bidder One", "bidder1@example.com", 1_000));
        userDao.addUser(new Bidder("bidder2", "secret", "Bidder Two", "bidder2@example.com", 1_000));
        UserService service = serviceWith(
                userDao,
                itemDao,
                auctionDao,
                1,
                Clock.fixed(now, ZoneOffset.UTC));

        service.processBid("1", "bidder1", 150);
        service.processBid("1", "bidder2", 200);

        assertEquals(firstExtendedEnd, item.getAuctionEndTime());
        assertEquals(200, item.getCurrentHighestPrice(), 0.001);
        assertEquals(2, itemDao.updateCalls);
        assertEquals(2, auctionDao.updateCalls);
    }

    /**
     * ## Test lay auction theo itemId: service nap item truoc roi moi lay auction tu DAO.
     */
    @Test
    void getAuctionByItemIdLoadsItemThenAuction() throws Exception {
        Item item = item("seller", 100);
        Auction auction = new Auction("auction-1", item, "seller", Instant.now());
        FakeAuctionDao auctionDao = new FakeAuctionDao(auction);
        UserService service = serviceWith(new FakeUserDao(), new FakeItemDao(item), auctionDao);

        assertSame(auction, service.getAuctionByItemId("1"));
        assertSame(item, auctionDao.selectedItem);
    }

    /**
     * ## Test update user: field khong hop le thi service tra false va khong goi DAO update.
     */
    @Test
    void updateUserRejectsUnsupportedField() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Old Name", "old@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertFalse(service.updateUser("bidder1", "unknown", "value"));
        assertFalse(userDao.updated);
    }

    /**
     * ## Test doi mat khau: sai mat khau cu thi service tra false va khong luu DAO.
     */
    @Test
    void changePasswordRejectsWrongOldPassword() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Bidder", "bidder@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null), 0);

        assertFalse(service.changePassword("bidder1", "wrong", "new"));
        assertFalse(userDao.updated);
    }

    /**
     * ## Test doi mat khau: dung mat khau cu thi SQL update thanh cong va service tra true.
     */
    @Test
    void changePasswordReturnsTrueWhenSqlUpdateAffectsRow() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Bidder", "bidder@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null), 1);

        assertTrue(service.changePassword("bidder1", "old", "new"));
        assertFalse(userDao.updated);
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao) throws Exception {
        return serviceWith(userDao, itemDao, auctionDao, 1);
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao,
                                    int updateCount) {
        return serviceWith(userDao, itemDao, auctionDao, updateCount, Clock.systemUTC());
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao,
                                    int updateCount, Clock clock) {
        return new UserService(userDao, itemDao, auctionDao, () -> connectionWithUpdateCount(updateCount), clock);
    }

    private Item item(String sellerId, double currentPrice) {
        Instant now = Instant.now();
        return item(sellerId, currentPrice, now.minusSeconds(60), now.plusSeconds(60));
    }

    private Item item(String sellerId, double currentPrice, Instant start, Instant end) {
        Item item = new Item(
                "Item",
                "Description",
                currentPrice,
                start,
                end,
                sellerId,
                ItemType.ART,
                "image.png");
        item.setDatabaseId(1);
        item.setCurrentHighestPrice(currentPrice);
        return item;
    }

    private Auction runningAuction(Item item) {
        return new StaticStatusAuction("auction-1", item, item.getSellerId(), Instant.now(), AuctionStatus.RUNNING);
    }

    private Connection connectionWithUpdateCount(int updateCount) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName())) {
                return preparedStatementWithUpdateCount(updateCount);
            }
            return defaultValue(method);
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler);
    }

    private PreparedStatement preparedStatementWithUpdateCount(int updateCount) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                return updateCount;
            }
            return defaultValue(method);
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                handler);
    }

    private Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0d;
        }
        if (returnType == Float.TYPE) {
            return 0f;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }
        return null;
    }

    private static final class StaticStatusAuction extends Auction {
        private AuctionStatus status;

        private StaticStatusAuction(String id, Item item, String sellerId, Instant createdAt, AuctionStatus status) {
            super(id, item, sellerId, createdAt);
            this.status = status;
        }

        @Override
        public AuctionStatus getStatus() {
            return status;
        }

        @Override
        public void setStatus(AuctionStatus status) {
            this.status = status;
        }

    }

    /**
     * ## Test fake DAO user: mo phong login, update profile va change password.
     */
    private static final class FakeUserDao extends DAOUser {
        private final Map<String, User> users = new HashMap<>();
        private User user;
        private boolean updated;

        private void addUser(User user) {
            users.put(user.getUsername(), user);
            if (this.user == null) {
                this.user = user;
            }
        }

        @Override
        public User selectByUsername(String username, String password) {
            User knownUser = users.get(username);
            if (knownUser != null && knownUser.getPassword().equals(password)) {
                return knownUser;
            }
            if (user != null && user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return user;
            }
            return null;
        }

        @Override
        public User selectByUsernameOnly(String username) {
            User knownUser = users.get(username);
            if (knownUser != null) {
                return knownUser;
            }
            return user != null && user.getUsername().equals(username) ? user : null;
        }

        @Override
        public int UpdateBalance(String username, double newBalance) {
            User knownUser = selectByUsernameOnly(username);
            if (knownUser != null) {
                knownUser.setBalance(newBalance);
                return 1;
            }
            return 0;
        }

        @Override
        public void Update(User user) {
            this.updated = true;
            this.user = user;
        }
    }

    /**
     * ## Test fake DAO item: tra item cho processBid/getAuctionByItemId va chan ghi DB that.
     */
    private static final class FakeItemDao extends DAOItems {
        private final Item item;
        private int updateResult = -1;
        private int updateCalls;
        private Item updatedItem;

        private FakeItemDao(Item item) {
            this.item = item;
        }

        @Override
        public Item selectById(String itemId) {
            return item;
        }

        @Override
        public Item selectById(Connection con, String itemId) {
            return item;
        }

        @Override
        public int Update(Connection con, Item item) throws SQLException {
            if (updateResult < 0) {
                throw new AssertionError("Unit test khong duoc ghi DB that.");
            }
            this.updatedItem = item;
            this.updateCalls++;
            return updateResult;
        }
    }

    /**
     * ## Test fake DAO auction: tra auction cho processBid va ghi nhan item duoc service truyen vao.
     */
    private static final class FakeAuctionDao extends DAOAuction_Items {
        private final Auction auction;
        private boolean selectCalled;
        private Item selectedItem;
        private int updateResult = -1;
        private int updateCalls;
        private Auction updatedAuction;

        private FakeAuctionDao(Auction auction) {
            this.auction = auction;
        }

        @Override
        public Auction selectByItemId(Item item) {
            this.selectCalled = true;
            this.selectedItem = item;
            return auction;
        }

        @Override
        public Auction selectByItemId(Connection con, Item item) {
            this.selectCalled = true;
            this.selectedItem = item;
            return auction;
        }

        @Override
        public int Update(Connection con, Auction auction, int itemId, String bidderId, Double price) {
            if (updateResult < 0) {
                throw new AssertionError("Unit test khong duoc ghi DB that.");
            }
            this.updatedAuction = auction;
            this.updateCalls++;
            return updateResult;
        }
    }
}
