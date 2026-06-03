package dao;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import model.DepositTransaction;
import model.auction.BidTransaction;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;

// Cấu hình JSON model.
public class GsonUtils {
    // Tạo dữ liệu.
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
                .registerTypeAdapter(BidTransaction.class, new BidTransactionTypeAdapter())
                .registerTypeAdapter(DepositTransaction.class, new DepositTransactionTypeAdapter())
                .create();
    }

    public static class InstantTypeAdapter extends TypeAdapter<Instant> {

        // Ghi dữ liệu.
        @Override
        public void write(JsonWriter out, Instant value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.getEpochSecond());
            }
        }

        // Đọc dữ liệu.
        @Override
        public Instant read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.ofEpochSecond(in.nextLong());
        }
    }

    public static class LocalDateTimeTypeAdapter extends TypeAdapter<LocalDateTime> {

        // Ghi dữ liệu.
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toEpochSecond(ZoneOffset.UTC));
            }
        }

        // Đọc dữ liệu.
        @Override
        public LocalDateTime read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.ofEpochSecond(in.nextLong(), 0, ZoneOffset.UTC);
        }
    }

    public static class BidTransactionTypeAdapter extends TypeAdapter<BidTransaction> {

        // Ghi dữ liệu.
        @Override
        public void write(JsonWriter out, BidTransaction value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();
            out.name("id").value(value.getId());
            out.name("Usernamebidder").value(value.getBidder());
            out.name("amount").value(value.getAmount());
            out.name("bidTime").value(value.getBidTime().getEpochSecond());
            out.endObject();
        }

        // Đọc dữ liệu.
        @Override
        public BidTransaction read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            String id = null;
            String bidder = null;
            double amount = 0;
            long epochSeconds = 0;

            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "id":
                        id = in.nextString();
                        break;
                    case "Usernamebidder":
                        bidder = in.nextString();
                        break;
                    case "amount":
                        amount = in.nextDouble();
                        break;
                    case "bidTime":
                        epochSeconds = in.nextLong();
                        break;
                    default:
                        in.skipValue();
                }
            }
            in.endObject();

            return new BidTransaction(id, bidder, amount, Instant.ofEpochSecond(epochSeconds));
        }
    }

    public static class DepositTransactionTypeAdapter extends TypeAdapter<DepositTransaction> {

        // Ghi dữ liệu.
        @Override
        public void write(JsonWriter out, DepositTransaction value) throws java.io.IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("id").value(value.getId());
            out.name("username").value(value.getUsername());
            out.name("amount").value(value.getAmount());

            out.name("timestamp").value(value.getTimestamp().getEpochSecond());

            out.name("status").value(value.getStatus());
            out.endObject();
        }

        // Đọc dữ liệu.
        @Override
        public DepositTransaction read(JsonReader in) throws java.io.IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            DepositTransaction dt = new DepositTransaction();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "id": dt.setId(in.nextString()); break;
                    case "username": dt.setUsername(in.nextString()); break;
                    case "amount": dt.setAmount(in.nextDouble()); break;

                    case "timestamp": dt.setTimestamp(Instant.ofEpochSecond(in.nextLong())); break;

                    case "status": dt.setStatus(in.nextString()); break;
                    default: in.skipValue(); break;
                }
            }
            in.endObject();
            return dt;
        }
    }
}
