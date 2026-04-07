package com.bbbc.client.view;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PriceChartPanel extends JPanel {
    private static final int PADDING = 36;
    private static final Color BACKGROUND = new Color(247, 248, 244);
    private static final Color GRID = new Color(210, 216, 211);
    private static final Color ACCENT = new Color(22, 115, 98);
    private static final Color POINT = new Color(211, 88, 45);
    private static final Color TEXT = new Color(37, 42, 52);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private List<ChartPoint> points = List.of();
    private double minPrice = 0.0d;
    private double maxPrice = 0.0d;

    public PriceChartPanel() {
        setBackground(BACKGROUND);
    }

    public void setAuctionData(double startingPrice, String startsAt, List<Map<String, Object>> bidHistory) {
        List<ChartPoint> newPoints = new ArrayList<>();
        Instant startTime = Instant.parse(startsAt);
        newPoints.add(new ChartPoint(startTime, startingPrice));
        if (bidHistory != null) {
            for (Map<String, Object> bid : bidHistory) {
                Object timestamp = bid.get("timestamp");
                Object amount = bid.get("amount");
                if (timestamp == null || amount == null) {
                    continue;
                }
                newPoints.add(new ChartPoint(
                        Instant.parse(String.valueOf(timestamp)),
                        Double.parseDouble(String.valueOf(amount))
                ));
            }
        }
        newPoints.sort(Comparator.comparing(ChartPoint::time));
        points = List.copyOf(newPoints);
        minPrice = points.stream().mapToDouble(ChartPoint::price).min().orElse(0.0d);
        maxPrice = points.stream().mapToDouble(ChartPoint::price).max().orElse(0.0d);
        if (Double.compare(minPrice, maxPrice) == 0) {
            maxPrice = minPrice + 1.0d;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (points.isEmpty()) {
                g2.setColor(TEXT);
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                g2.drawString("No price data yet", PADDING, PADDING);
                return;
            }

            int width = getWidth() - (PADDING * 2);
            int height = getHeight() - (PADDING * 2);
            int left = PADDING;
            int top = PADDING;
            int right = left + width;
            int bottom = top + height;

            drawGrid(g2, left, top, width, height);
            drawAxisLabels(g2, left, top, right, bottom);
            drawSeries(g2, left, top, width, height);
        } finally {
            g2.dispose();
        }
    }

    private void drawGrid(Graphics2D g2, int left, int top, int width, int height) {
        g2.setColor(GRID);
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 4; i++) {
            int y = top + ((height * i) / 4);
            g2.drawLine(left, y, left + width, y);
        }
        for (int i = 0; i <= 4; i++) {
            int x = left + ((width * i) / 4);
            g2.drawLine(x, top, x, top + height);
        }
    }

    private void drawAxisLabels(Graphics2D g2, int left, int top, int right, int bottom) {
        g2.setColor(TEXT);
        g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g2.drawString("Realtime Price Curve", left, top - 12);

        g2.setFont(getFont().deriveFont(12f));
        g2.drawString(String.format("%.2f", maxPrice), 6, top + 4);
        g2.drawString(String.format("%.2f", minPrice), 6, bottom);

        Instant firstTime = points.getFirst().time();
        Instant lastTime = points.getLast().time();
        g2.drawString(formatTime(firstTime), left, bottom + 18);
        g2.drawString(formatTime(lastTime), Math.max(left, right - 58), bottom + 18);
    }

    private void drawSeries(Graphics2D g2, int left, int top, int width, int height) {
        Instant firstTime = points.getFirst().time();
        Instant lastTime = points.getLast().time();
        long durationMillis = Math.max(1L, lastTime.toEpochMilli() - firstTime.toEpochMilli());

        g2.setColor(ACCENT);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int previousX = -1;
        int previousY = -1;
        for (ChartPoint point : points) {
            int x = left + (int) (((point.time().toEpochMilli() - firstTime.toEpochMilli()) * width) / durationMillis);
            int y = top + (int) (((maxPrice - point.price()) * height) / (maxPrice - minPrice));

            if (previousX >= 0) {
                g2.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }

        g2.setColor(POINT);
        for (ChartPoint point : points) {
            int x = left + (int) (((point.time().toEpochMilli() - firstTime.toEpochMilli()) * width) / durationMillis);
            int y = top + (int) (((maxPrice - point.price()) * height) / (maxPrice - minPrice));
            g2.fillOval(x - 4, y - 4, 8, 8);
        }
    }

    private String formatTime(Instant instant) {
        return TIME_FORMATTER.format(instant);
    }

    private record ChartPoint(Instant time, double price) {
    }
}
