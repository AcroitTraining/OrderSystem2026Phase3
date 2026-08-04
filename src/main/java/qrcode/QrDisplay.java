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

        int bufferSize = ((EPD_WIDTH % 8 == 0) ? (EPD_WIDTH / 8) : (EPD_WIDTH / 8 + 1)) * EPD_HEIGHT;
        byte[] frameBuffer = new byte[bufferSize];
        Arrays.fill(frameBuffer, (byte) 0xFF);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        int qrSize = 100;
        BitMatrix bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, qrSize, qrSize);

        int offsetX = (EPD_WIDTH - qrSize) / 2;
        int offsetY = (EPD_HEIGHT - qrSize) / 2;

        for (int y = 0; y < qrSize; y++) {
            for (int x = 0; x < qrSize; x++) {
                if (bitMatrix.get(x, y)) {
                    drawPixel(frameBuffer, offsetX + x, offsetY + y);
                }
            }
        }

        EpdEmulator.saveBufferAsImage(frameBuffer, EPD_WIDTH, EPD_HEIGHT, outputPath);
    }

    public void runForTable(int tableId, String qrText) throws Exception {
        String fileName = "table_" + tableId + "_qr.png";
        run(qrText, fileName);
    }

    private void drawPixel(byte[] buffer, int x, int y) {
        if (x >= EPD_WIDTH || y >= EPD_HEIGHT) return;
        int byteWidth = (EPD_WIDTH % 8 == 0) ? (EPD_WIDTH / 8) : (EPD_WIDTH / 8 + 1);
        int index = x / 8 + y * byteWidth;
        buffer[index] &= ~(0x80 >> (x % 8));
    }

    // ★ テスト用：引数で卓番とURLを渡して単体確認する
    public static void main(String[] args) {
        try {
            QrDisplay qrDisplay = new QrDisplay();

            if (args.length < 2) {
                System.out.println("使い方: java QrDisplay <tableId> <url>");
                return;
            }

            int tableId = Integer.parseInt(args[0]);
            String fullUrl = args[1];

            qrDisplay.runForTable(tableId, fullUrl);
            System.out.println("✅ 卓番 " + tableId + " 用QR画像を生成しました ➔ table_" + tableId + "_qr.png");
            System.out.println("   └ 埋め込みURL: " + fullUrl);

        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}