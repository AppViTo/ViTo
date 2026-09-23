package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.outbound.ImageProcessingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Component
public class ImageProcessingAdapter implements ImageProcessingPort {

    @Override
    public Media.MediaMetadata extractMetadata(byte[] data, String contentType) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BufferedImage image = ImageIO.read(inputStream);
            
            if (image != null) {
                return Media.MediaMetadata.builder()
                        .width(image.getWidth())
                        .height(image.getHeight())
                        .format(contentType.split("/")[1])
                        .build();
            }
        } catch (IOException e) {
            log.error("Failed to extract image metadata: {}", e.getMessage(), e);
        }
        
        return Media.MediaMetadata.builder().build();
    }

    @Override
    public byte[] createThumbnail(byte[] data, int width, int height) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BufferedImage originalImage = ImageIO.read(inputStream);
            
            if (originalImage == null) {
                return data;
            }

            BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = thumbnail.createGraphics();
            
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(originalImage, 0, 0, width, height, null);
            g2d.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", outputStream);
            
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("Failed to create thumbnail: {}", e.getMessage(), e);
            return data;
        }
    }

    @Override
    public byte[] resizeImage(byte[] data, int maxWidth, int maxHeight) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BufferedImage originalImage = ImageIO.read(inputStream);
            
            if (originalImage == null) {
                return data;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            int newWidth = originalWidth;
            int newHeight = originalHeight;
            
            if (originalWidth > maxWidth) {
                newWidth = maxWidth;
                newHeight = (originalHeight * maxWidth) / originalWidth;
            }
            
            if (newHeight > maxHeight) {
                newHeight = maxHeight;
                newWidth = (originalWidth * maxHeight) / originalHeight;
            }

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", outputStream);
            
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("Failed to resize image: {}", e.getMessage(), e);
            return data;
        }
    }

    @Override
    public boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    @Override
    public boolean isVideo(String contentType) {
        return contentType != null && contentType.startsWith("video/");
    }
}
