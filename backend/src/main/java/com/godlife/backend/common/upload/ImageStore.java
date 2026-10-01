package com.godlife.backend.common.upload;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 채팅 사진·인증 사진·일기 사진·프로필 사진을 서버 디스크에 저장한다. 받은 파일을 그대로 두지 않고 다시 그려서 JPEG 로 저장하므로
 * - 진짜 사진(JPG/PNG)인지 확인되고
 * - 위치(GPS)·기기 정보 같은 촬영 정보(EXIF)가 지워지고
 * - 긴 쪽이 1600px(프로필 사진은 512px)를 넘으면 줄어든다.
 * 픽셀 수는 열기 전에 머리 부분만 읽어 확인한다. (작은 파일이 거대한 그림으로 풀려 서버 메모리를 채우는 공격 방지)
 */
@Slf4j
@Component
public class ImageStore {

    private static final Set<String> FORMATS = Set.of("jpeg", "png");
    private static final int MAX_SIDE = 1600;
    /** 프로필 사진은 작게만 쓰인다 */
    private static final int PROFILE_MAX_SIDE = 512;
    private static final int MAX_SOURCE_SIDE = 8000;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;

    private final Path root;

    public ImageStore(@Value("${app.upload.dir:uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** 챌린지 채팅 사진을 저장하고 파일 키("chat/{challengeId}/{uuid}.jpg")를 돌려준다. */
    public String storeChatImage(Long challengeId, MultipartFile file) {
        return store("chat/" + challengeId, file);
    }

    /** 챌린지 인증 사진을 저장하고 파일 키("verification/{challengeId}/{uuid}.jpg")를 돌려준다. */
    public String storeVerificationImage(Long challengeId, MultipartFile file) {
        return store("verification/" + challengeId, file);
    }

    /** 일기 사진을 저장하고 파일 키("diary/{userId}/{uuid}.jpg")를 돌려준다. */
    public String storeDiaryImage(Long userId, MultipartFile file) {
        return store("diary/" + userId, file);
    }

    /** 프로필 사진을 저장하고 파일 키("profile/{userId}/{uuid}.jpg")를 돌려준다. */
    public String storeProfileImage(Long userId, MultipartFile file) {
        return store("profile/" + userId, file, PROFILE_MAX_SIDE);
    }

    private String store(String folder, MultipartFile file) {
        return store(folder, file, MAX_SIDE);
    }

    private String store(String folder, MultipartFile file, int maxSide) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        BufferedImage source = read(file);
        BufferedImage resized = toRgbWithin(source, maxSide);

        String key = folder + "/" + UUID.randomUUID() + ".jpg";
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            writeJpeg(resized, target);
        } catch (IOException e) {
            throw new IllegalStateException("사진을 저장하지 못했습니다.", e);
        }
        return key;
    }

    /** 저장한 파일 경로. 키는 서버가 만든 값이지만, 저장 폴더 밖을 가리키면 거절한다. */
    public Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return path;
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("사진 파일을 지우지 못했습니다: {}", key, e);
        }
    }

    /** 챌린지를 지울 때 그 챌린지의 채팅·인증 사진 폴더를 통째로 지운다. */
    public void deleteChallenge(Long challengeId) {
        try {
            FileSystemUtils.deleteRecursively(resolve("chat/" + challengeId));
            FileSystemUtils.deleteRecursively(resolve("verification/" + challengeId));
        } catch (IOException e) {
            log.warn("챌린지 {} 사진 폴더를 지우지 못했습니다.", challengeId, e);
        }
    }

    private static BufferedImage read(MultipartFile file) {
        try (InputStream in = file.getInputStream(); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE);
                }
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new BusinessException(ErrorCode.IMAGE_TOO_BIG);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }

    /** 긴 쪽을 maxSide 이하로 줄이고, 투명한 부분(PNG)은 흰 바탕으로 채운 RGB 그림 */
    private static BufferedImage toRgbWithin(BufferedImage source, int maxSide) {
        double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        // ImageOutputStream 을 닫아도 아래 파일 스트림은 닫히지 않으므로 둘 다 닫는다
        try (OutputStream file = Files.newOutputStream(target);
             ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
