import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Writes the StockBuddy icon as PNG files (Android, Linux) and an .ico file (Windows). Usage: MakeIcons <outDir> */
public class MakeIcons {
    public static void main(String[] a) throws Exception {
        Path out = Path.of(a[0]);
        String[][] android = {{"mdpi", "48"}, {"hdpi", "72"}, {"xhdpi", "96"}, {"xxhdpi", "144"}, {"xxxhdpi", "192"}};
        for (String[] d : android) {
            Path dir = out.resolve("android/drawable-" + d[0]);
            Files.createDirectories(dir);
            ImageIO.write(stockbuddy.App.drawIcon(Integer.parseInt(d[1])), "png", dir.resolve("icon.png").toFile());
        }
        Files.createDirectories(out.resolve("android/drawable"));
        ImageIO.write(stockbuddy.App.drawIcon(96), "png", out.resolve("android/drawable/icon.png").toFile());
        ImageIO.write(stockbuddy.App.drawIcon(256), "png", out.resolve("stockbuddy.png").toFile());
        // Windows .ico containing PNG images
        int[] sizes = {16, 32, 48, 256};
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(stockbuddy.App.drawIcon(sizes[i]), "png", b);
            pngs[i] = b.toByteArray();
        }
        int total = 6 + 16 * sizes.length;
        for (byte[] p : pngs) total += p.length;
        ByteBuffer bb = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            bb.put((byte) (sizes[i] >= 256 ? 0 : sizes[i])).put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
            bb.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            bb.putInt(pngs[i].length).putInt(offset);
            offset += pngs[i].length;
        }
        for (byte[] p : pngs) bb.put(p);
        Files.write(out.resolve("stockbuddy.ico"), bb.array());
    }
}
