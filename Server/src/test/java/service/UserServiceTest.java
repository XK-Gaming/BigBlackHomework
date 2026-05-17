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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

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
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertFalse(service.changePassword("bidder1", "wrong", "new"));
        assertFalse(userDao.updated);
    }

    /**
     * ## Test doi mat khau: dung mat khau cu thi service cap nhat password va goi DAO update.
     */
    @Test
    void changePasswordUpdatesPasswordWhenOldPasswordMatches() throws Exception {
        FakeUserDao userDao = new FakeUserDao();
        userDao.user = new Bidder("bidder1", "old", "Bidder", "bidder@example.com");
        UserService service = serviceWith(userDao, new FakeItemDao(null), new FakeAuctionDao(null));

        assertTrue(service.changePassword("bidder1", "old", "new"));
        assertEquals("new", userDao.user.getPassword());
        assertTrue(userDao.updated);
    }

    private UserService serviceWith(FakeUserDao userDao, FakeItemDao itemDao, FakeAuctionDao auctionDao) throws Exception {
        UserService service = new UserService();
        setField(service, "userDAO", userDao);
        setField(service, "itemDAO", itemDao);
        setField(service, "auctionDAO", auctionDao);
        return service;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = UserService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Item item(String sellerId, double currentPrice) {
        Item item = new Item(
                "Item",
                "Description",
                currentPrice,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                sellerId,
                ItemType.ART,
                "image.png");
        item.setDatabaseId(1);
        item.setCurrentHighestPrice(currentPrice);
        return item;
    }

    /**
     * ## Test fake DAO user: mo phong login, update profile va change password.
     */
    private static final class FakeUserDao extends DAOUser {
        private User user;
        private boolean updated;

        @Override
        public User selectByUsername(String username, String password) {
            if (user != null && user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return user;
            }
            return null;
        }

        @Override
        public User selectByUsernameOnly(String username) {
            return user != null && user.getUsername().equals(username) ? user : null;
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

        private FakeItemDao(Item item) {
            this.item = item;
        }

        @Override
        public Item selectById(String itemId) {
            return item;
        }

        @Override
        public int Update(Connection con, Item item) throws SQLException {
            throw new AssertionError("Unit test khong duoc ghi DB that.");
        }
    }

    /**
     * ## Test fake DAO auction: tra auction cho processBid va ghi nhan item duoc service truyen vao.
     */
    private static final class FakeAuctionDao extends DAOAuction_Items {
        private final Auction auction;
        private boolean selectCalled;
        private Item selectedItem;

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
        public int Update(Connection con, Auction auction, int itemId, String bidderId, Double price) {
            throw new AssertionError("Unit test khong duoc ghi DB that.");
        }
    }
}
