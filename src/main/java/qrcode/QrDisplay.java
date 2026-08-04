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

        // 白（0xFF）で初期化
        int bufferSize = ((EPD_WIDTH % 8 == 0) ? (EPD_WIDTH / 8) : (EPD_WIDTH / 8 + 1)) * EPD_HEIGHT;
        byte[] frameBuffer = new byte[bufferSize];
        Arrays.fill(frameBuffer, (byte) 0xFF);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        // 読み取り用の余白を設けるため 100x100
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

    // ★ ここを実行すればDBドライバなしで卓1〜4の正解QR画像が一気に完成します！
    public static void main(String[] args) {
        try {
            QrDisplay qrDisplay = new QrDisplay();
            
            // 最新のIPアドレス
            String baseUrl = "http://172.19.72.12:8080/OrderSystem2026Phase1/OrderStartServlet?tt=";

            // 先ほどのDB画像で確認できた、各卓の最新（未closed）トークン
            String[] latestTokens = {
                "2891ebcb57a67d9a", // 卓1用 (session_id: 163)
                "233ed69d31184669", // 卓2用 (session_id: 159)
                "103926ce8e410c78", // 卓3用 (session_id: 160)
                "fd2a4fcd7162b293"  // 卓4用 (session_id: 162)
            };

            System.out.println("🔄 最新IP [172.19.72.36] とDB最新トークンで全卓のQRコードを一括作成します...\n");

            for (int i = 0; i < 4; i++) {
                int tableId = i + 1;
                String fullUrl = baseUrl + latestTokens[i];

                qrDisplay.runForTable(tableId, fullUrl);
                System.out.println(" ✅ 卓番 " + tableId + " 用QR画像を生成しました ➔ table_" + tableId + "_qr.png");
                System.out.println("   └ 埋め込みURL: " + fullUrl);
            }

            System.out.println("\n✨ 全4卓分のQRコード画像生成が成功しました！");

        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}