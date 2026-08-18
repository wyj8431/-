package com.example.lowcode.template.application;

import java.time.Instant;
import java.util.Optional;

public interface TemplateCoverRepository {
    Optional<PublicCover> findPublicReferencedCover(long coverAssetId, Instant now);

    record PublicCover(long id, String objectKey, String mimeType) {
    }
}
