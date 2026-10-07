import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 개발용: 더미 인증 사진(물병 그림 + 닉네임·날짜)을 만든다. dev-verification.sh 가 부른다.
 *   java db/DevVerificationPhotos.java {업로드 폴더} < "경로\t닉네임\t날짜" 줄들
 */
public class DevVerificationPhotos {

    private static final Color[][] TINTS = {
            {new Color(0xDCEFF7), new Color(0x9CCFE4)},
            {new Color(0xF4E6D6), new Color(0xD9B48F)},
            {new Color(0xE3F1E6), new Color(0xA5D3AE)},
            {new Color(0xEDE5F5), new Color(0xBBA6D6)},
    };

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int count = 0;
        for (String line; (line = in.readLine()) != null; ) {
            String[] cols = line.split("\t");
            if (cols.length < 3 || !cols[0].startsWith("verification/")) {
                continue;
            }
            Path target = root.resolve(cols[0]);
            Files.createDirectories(target.getParent());
            ImageIO.write(draw(cols[1], cols[2], Math.abs(cols[0].hashCode())), "jpg", target.toFile());
            count++;
        }
        System.out.println("더미 인증 사진 " + count + "장을 만들었어요: " + root);
    }

    private static BufferedImage draw(String nickname, String date, int seed) {
        int size = 800;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color[] tint = TINTS[seed % TINTS.length];
        g.setPaint(new GradientPaint(0, 0, tint[0], size, size, tint[1]));
        g.fillRect(0, 0, size, size);

        // 물병
        int x = 300 + seed % 60 - 30;
        g.setColor(new Color(255, 255, 255, 170));
        g.fill(new RoundRectangle2D.Double(x, 190, 200, 420, 60, 60));
        g.fill(new RoundRectangle2D.Double(x + 65, 140, 70, 60, 16, 16));
        g.setColor(new Color(70, 150, 200, 190));
        g.fill(new RoundRectangle2D.Double(x + 12, 330 + seed % 80, 176, 268 - seed % 80, 44, 44));
        g.setColor(new Color(60, 60, 60, 120));
        g.setStroke(new BasicStroke(4));
        g.draw(new RoundRectangle2D.Double(x, 190, 200, 420, 60, 60));

        g.setColor(new Color(0x2A2925));
        g.setFont(new Font("Malgun Gothic", Font.BOLD, 44));
        g.drawString(nickname, 48, 710);
        g.setFont(new Font("Malgun Gothic", Font.PLAIN, 30));
        g.setColor(new Color(0x6B4A35));
        g.drawString(date + "  물 2L 인증", 48, 756);
        g.dispose();
        return image;
    }
}
