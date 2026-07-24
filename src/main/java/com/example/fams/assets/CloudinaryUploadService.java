package com.example.fams.assets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CloudinaryUploadService {

    private static final Pattern SECURE_URL_PATTERN = Pattern.compile("\"secure_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("\"public_id\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryUploadService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.folder:fams/assets}") String folder) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = folder;
    }

    public Optional<AssetImageUpload> upload(MultipartFile image) {
        if (image == null || image.isEmpty() || !isConfigured()) {
            return Optional.empty();
        }

        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            Map<String, String> signedParams = new LinkedHashMap<>();
            signedParams.put("folder", folder);
            signedParams.put("timestamp", String.valueOf(timestamp));

            Map<String, String> formFields = new LinkedHashMap<>(signedParams);
            formFields.put("api_key", apiKey);
            formFields.put("signature", sign(signedParams));

            String boundary = "----FAMS-" + UUID.randomUUID();
            byte[] body = multipartBody(boundary, formFields, image);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cloudinary upload failed with status " + response.statusCode());
            }

            String secureUrl = extract(response.body(), SECURE_URL_PATTERN);
            String publicId = extract(response.body(), PUBLIC_ID_PATTERN);
            return Optional.of(new AssetImageUpload(secureUrl, publicId));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to upload asset image", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Asset image upload was interrupted", e);
        }
    }

    boolean isConfigured() {
        return hasText(cloudName) && hasText(apiKey) && hasText(apiSecret);
    }

    public void delete(String publicId) {
        if (!hasText(publicId) || !isConfigured()) {
            return;
        }

        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            Map<String, String> signedParams = new LinkedHashMap<>();
            signedParams.put("invalidate", "true");
            signedParams.put("public_id", publicId);
            signedParams.put("timestamp", String.valueOf(timestamp));

            String body = "api_key=" + encode(apiKey)
                    + "&invalidate=true"
                    + "&public_id=" + encode(publicId)
                    + "&signature=" + encode(sign(signedParams))
                    + "&timestamp=" + timestamp;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cloudinary delete failed with status " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to delete asset image", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Asset image delete was interrupted", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sign(Map<String, String> params) {
        StringBuilder source = new StringBuilder();
        new TreeMap<>(params).forEach((key, value) -> {
            if (!source.isEmpty()) {
                source.append('&');
            }
            source.append(key).append('=').append(value);
        });
        source.append(apiSecret);
        return sha1Hex(source.toString());
    }

    private String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    private byte[] multipartBody(String boundary, Map<String, String> formFields, MultipartFile file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : formFields.entrySet()) {
            write(output, "--" + boundary + "\r\n");
            write(output, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n");
            write(output, field.getValue() + "\r\n");
        }

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("asset-image");
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        write(output, "Content-Type: " + contentType + "\r\n\r\n");
        output.write(file.getBytes());
        write(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String extract(String json, Pattern pattern) {
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\/", "/");
        }
        return "";
    }
}
