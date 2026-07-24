package com.example.fams.assets;

import com.example.fams.lifecycle.AssetLifecycleService;
import com.example.fams.settings.AdminSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private CloudinaryUploadService cloudinaryUploadService;
    @Mock
    private AssetTagGenerationService assetTagGenerationService;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private AssetLifecycleService assetLifecycleService;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(
                assetRepository,
                cloudinaryUploadService,
                assetTagGenerationService,
                adminSettingsService,
                assetLifecycleService
        );
    }

    @Test
    void updateImageReplacesExistingImageAndDeletesPreviousUpload() {
        Asset asset = new Asset();
        asset.setAssetCode("AST-2026-00001");
        asset.setImageUrl("https://old.example/image.png");
        asset.setImagePublicId("fams/assets/old-image");

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "new-image.png",
                "image/png",
                "new image".getBytes()
        );

        when(cloudinaryUploadService.isConfigured()).thenReturn(true);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(cloudinaryUploadService.upload(image))
                .thenReturn(Optional.of(new AssetImageUpload("https://cdn.example/new-image.png", "fams/assets/new-image")));
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Asset updated = assetService.updateImage(1L, image);

        assertEquals("https://cdn.example/new-image.png", updated.getImageUrl());
        assertEquals("fams/assets/new-image", updated.getImagePublicId());
        verify(cloudinaryUploadService).delete("fams/assets/old-image");
    }

    @Test
    void removeImageClearsStoredImageData() {
        Asset asset = new Asset();
        asset.setAssetCode("AST-2026-00001");
        asset.setImageUrl("https://old.example/image.png");
        asset.setImagePublicId("fams/assets/old-image");

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Asset updated = assetService.removeImage(1L);

        assertNull(updated.getImageUrl());
        assertNull(updated.getImagePublicId());
        verify(cloudinaryUploadService).delete("fams/assets/old-image");
    }

    @Test
    void removeImageRejectsAssetsWithoutAnImage() {
        Asset asset = new Asset();
        asset.setAssetCode("AST-2026-00001");

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> assetService.removeImage(1L));
        assertEquals("This asset does not have an image to remove.", ex.getMessage());

        verify(assetRepository, never()).save(any(Asset.class));
        verify(cloudinaryUploadService, never()).delete(anyString());
    }

    @Test
    void removeImageIsBlockedWhenImagesAreRequired() {
        Asset asset = new Asset();
        asset.setAssetCode("AST-2026-00001");
        asset.setImageUrl("https://old.example/image.png");
        asset.setImagePublicId("fams/assets/old-image");

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(adminSettingsService.getParameterValue("asset.require.image", "false")).thenReturn("true");

        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> assetService.removeImage(1L)
        );

        assertEquals("Asset image is required by the current system settings.", ex.getMessage());
        verify(assetRepository, never()).save(any(Asset.class));
        verify(cloudinaryUploadService, never()).delete(anyString());
    }
}
