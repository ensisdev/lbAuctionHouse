package fr.maxlego08.zauctionhouse.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ColorExtractor {

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HttpClient httpClient;
    private final Logger logger;
    private final Map<String, String> colorCache = new ConcurrentHashMap<>();
    private final String defaultColor;
    private final File cacheFile;

    public ColorExtractor(File dataFolder, Logger logger, String defaultColor) {
        this.logger = logger;
        this.defaultColor = defaultColor;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        File cacheDir = new File(dataFolder, ".cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        this.cacheFile = new File(cacheDir, "material-colors.json");
        loadCache();
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(cacheFile)) {
            Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) {
                colorCache.putAll(loaded);
                this.logger.info("Loaded " + colorCache.size() + " cached material colors from material-colors.json");
            }
        } catch (Exception e) {
            this.logger.warning("Failed to load color cache: " + e.getMessage());
        }
    }

    private void saveCache() {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            GSON.toJson(colorCache, writer);
        } catch (Exception e) {
            this.logger.warning("Failed to save color cache: " + e.getMessage());
        }
    }

    /**
     * Gets the cached color for a material, or extracts it from the image URL if not cached.
     *
     * @param material the material name (e.g., "DIAMOND_SWORD")
     * @param imageUrl the URL of the image to extract the color from
     * @return the hex color (e.g., "#FF5733")
     */
    public String getColorForMaterial(String material, String imageUrl) {
        if (material == null || material.isEmpty()) {
            return defaultColor;
        }

        String cached = colorCache.get(material);
        if (cached != null) {
            return cached;
        }

        String color = extractColorFromUrl(imageUrl);

        colorCache.put(material, color);
        saveCache();

        return color;
    }

    private String extractColorFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return defaultColor;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(imageUrl)).timeout(Duration.ofSeconds(10)).GET().build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                return defaultColor;
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                return defaultColor;
            }

            return calculateDominantColor(image);

        } catch (Exception e) {
            logger.warning("Failed to extract dominant color from " + imageUrl + ": " + e.getMessage());
            return defaultColor;
        }
    }

    /**
     * Calculates the dominant color of an image by sampling every 50 pixels in both the x and y directions.
     * The dominant color is determined by the color with the highest frequency in the sampled colors.
     * If no colors are found, the default color is returned.
     *
     * @param image the image to calculate the dominant color for
     * @return the hex color (e.g., "#FF5733")
     */
    private String calculateDominantColor(BufferedImage image) {
        Map<Integer, Integer> colorCount = new HashMap<>();

        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, Math.min(width, height) / 50);

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;

                if (alpha < 128) continue;

                int quantizedRgb = quantizeColor(rgb);
                colorCount.merge(quantizedRgb, 1, Integer::sum);
            }
        }

        if (colorCount.isEmpty()) {
            return defaultColor;
        }

        int dominantColor = colorCount.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFFFFF);

        int r = (dominantColor >> 16) & 0xFF;
        int g = (dominantColor >> 8) & 0xFF;
        int b = dominantColor & 0xFF;

        return String.format("#%02X%02X%02X", r, g, b);
    }

    /**
     * Quantizes a color to a palette of 256 colors.
     * This method reduces the precision of the color by rounding it to the nearest multiple of 8.
     * The resulting color is a packed integer with the format {@code 0xRRGGBB}.
     *
     * @param rgb the color to quantize
     * @return the quantized color
     */
    private int quantizeColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = (r / 8) * 8;
        g = (g / 8) * 8;
        b = (b / 8) * 8;

        return (r << 16) | (g << 8) | b;
    }
}
