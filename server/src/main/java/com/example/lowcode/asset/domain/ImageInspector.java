package com.example.lowcode.asset.domain;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class ImageInspector {
    private static final int MAX_DIMENSION = 20_000;

    public ImageInfo inspect(byte[] imageBytes) {
        ImageFormat magicFormat = detectMagic(imageBytes);
        if (magicFormat == null) {
            throw new InvalidImageException("图片魔数无效");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) {
                throw new InvalidImageException("无法读取图片内容");
            }
            ImageReader reader = findReader(input, magicFormat);
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new InvalidImageException("图片尺寸无效");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidImageException("图片解码失败");
                }
                return new ImageInfo(magicFormat.mimeType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidImageException("图片解码失败", exception);
        }
    }

    private ImageReader findReader(ImageInputStream input, ImageFormat magicFormat) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            if (magicFormat.matches(reader.getFormatName())) {
                return reader;
            }
            reader.dispose();
        }
        if (magicFormat == ImageFormat.WEBP) {
            throw new UnsupportedWebpException();
        }
        throw new InvalidImageException("当前运行环境无法解码图片格式");
    }

    private ImageFormat detectMagic(byte[] bytes) {
        if (bytes.length >= 3
            && (bytes[0] & 0xff) == 0xff
            && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) {
            return ImageFormat.JPEG;
        }
        if (bytes.length >= 8
            && (bytes[0] & 0xff) == 0x89
            && bytes[1] == 'P'
            && bytes[2] == 'N'
            && bytes[3] == 'G'
            && bytes[4] == 0x0d
            && bytes[5] == 0x0a
            && bytes[6] == 0x1a
            && bytes[7] == 0x0a) {
            return ImageFormat.PNG;
        }
        if (bytes.length >= 12
            && bytes[0] == 'R'
            && bytes[1] == 'I'
            && bytes[2] == 'F'
            && bytes[3] == 'F'
            && bytes[8] == 'W'
            && bytes[9] == 'E'
            && bytes[10] == 'B'
            && bytes[11] == 'P') {
            return ImageFormat.WEBP;
        }
        return null;
    }

    public record ImageInfo(String mimeType, int width, int height) {
    }

    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }

        public InvalidImageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class UnsupportedWebpException extends InvalidImageException {
        public UnsupportedWebpException() {
            super("当前运行环境不支持 WebP 图片校验");
        }
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String mimeType;
        private final String[] readerNames;

        ImageFormat(String mimeType, String... readerNames) {
            this.mimeType = mimeType;
            this.readerNames = readerNames;
        }

        private boolean matches(String readerFormat) {
            String normalized = readerFormat.toLowerCase(Locale.ROOT);
            for (String readerName : readerNames) {
                if (readerName.equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }
}
