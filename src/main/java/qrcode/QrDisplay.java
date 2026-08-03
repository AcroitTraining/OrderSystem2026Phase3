package qrcode;

import java.util.Arrays;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class QrDisplay {

    private static final int EPD_WIDTH = 122;
    private static final int EPD_HEIGHT = 250;

    public void run(String qrText, String outputPath) throws Exception {

        if (qrText == null || qrText.trim().isEmpty()) {
            throw new IllegalArgumentException("注文開始URLが null または空です。");
        }
        if (!qrText.startsWith("http://") && !qrText.startsWith("https://")) {
            throw new IllegalArgumentException("URLの形式が正しくありません: " + qrText);
        }
        if (qrText.length() > 200) {
            throw new IllegalArgumentException("注文開始URLの文字列が長すぎます。");
        }

        int bufferSize = ((EPD_WIDTH % 8 == 0) ? (EPD_WIDTH / 8) : (EPD_WIDTH / 8 + 1)) * EPD_HEIGHT;
        byte[] frameBuffer = new byte[bufferSize];
        Arrays.fill(frameBuffer, (byte) 0xFF);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, 120, 120);

        int offsetX = 1;
        int offsetY = 60;

        for (int y = 0; y < 120; y++) {
            for (int x = 0; x < 120; x++) {
                if (bitMatrix.get(x, y)) {
                    drawPixel(frameBuffer, offsetX + x, offsetY + y);
                }
            }
        }

        EpdEmulator.saveBufferAsImage(frameBuffer, EPD_WIDTH, EPD_HEIGHT, outputPath);
    }

    private void drawPixel(byte[] buffer, int x, int y) {
        if (x >= EPD_WIDTH || y >= EPD_HEIGHT) return;
        int byteWidth = (EPD_WIDTH % 8 == 0) ? (EPD_WIDTH / 8) : (EPD_WIDTH / 8 + 1);
        int index = x / 8 + y * byteWidth;
        buffer[index] &= ~(0x80 >> (x % 8));
    }

    public static void main(String[] args) {
        try {
            // ★ 最新の Wi-Fi IP (172.19.72.36) を設定
            String testUrl = "http://172.19.72.36:8080/OrderSystem2026Phase1/OrderStartServlet?tt=fa44f9a6-741f-11f1-b5ec-6845f12866be-4090a26b";
            
            QrDisplay qrDisplay = new QrDisplay();
            qrDisplay.run(testUrl, "qr_test.png");
            
            System.out.println("★ IP: 172.19.72.36 でQRコードの生成に成功しました！ -> qr_test.png");
            System.out.println("埋め込みURL: " + testUrl);
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}