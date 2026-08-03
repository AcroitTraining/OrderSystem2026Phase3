package qrcode;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;

import javax.imageio.ImageIO;

public class EpdEmulator {

    public static void saveBufferAsImage(byte[] frameBuffer, int width, int height, String outputPath)
            throws Exception {

        if (outputPath == null || outputPath.trim().isEmpty()) {
            throw new IllegalArgumentException("出力パスが無効です。");
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        int byteWidth = (width % 8 == 0) ? (width / 8) : (width / 8 + 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x / 8 + y * byteWidth;
                int bit = (frameBuffer[index] >> (7 - (x % 8))) & 0x01;
                int rgb = (bit == 0) ? 0x000000 : 0xFFFFFF;
                image.setRGB(x, y, rgb);
            }
        }

        File outputFile = new File(outputPath);
        boolean written = ImageIO.write(image, "png", outputFile);

        if (!written) {
            // 設計書: FileNotFound等の例外ハンドリング用
            throw new FileNotFoundException("画像の書き込みに失敗しました: " + outputPath);
        }
    }
}