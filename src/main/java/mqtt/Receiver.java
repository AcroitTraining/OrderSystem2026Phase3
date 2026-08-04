package mqtt;

import qrcode.QrDisplay;

public class Receiver {

    /**
     * URLを受け取り、電子ペーパー（EPD）表示用フォーマット（122x250）のQRコード画像を生成する
     * @param tableId 卓番号
     * @param targetUrl 新しいセッションURL
     */
    public static void generateQRCode(int tableId, String targetUrl) {
        System.out.println("📩 [Receiver] 卓番 " + tableId + " のQRコード画像生成要求を受信しました。");

        try {
            // qrcodeパッケージの QrDisplay（122x250 モノクロ2値描画）を呼び出し
            QrDisplay qrDisplay = new QrDisplay();
            
            // table_X_qr.png のファイル名で電子ペーパー用画像を書き出す
            qrDisplay.runForTable(tableId, targetUrl);

            System.out.println("✅ [電子ペーパー用QR自動生成完了] 卓番 " + tableId + " の新QRコードを更新しました -> table_" + tableId + "_qr.png");
            System.out.println(" └ 埋め込みURL: " + targetUrl);

        } catch (Exception e) {
            System.err.println("❌ 卓番 " + tableId + " のQRコード生成中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}