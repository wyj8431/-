package com.example.lowcode.template.application;

import com.example.lowcode.asset.application.StorageGateway;
import com.example.lowcode.common.exception.BusinessException;
import com.example.lowcode.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class TemplateCoverService {
    private static final long MAX_COVER_BYTES = 10L * 1024 * 1024;

    private final TemplateCoverRepository templateCoverRepository;
    private final StorageGateway storageGateway;
    private final Clock clock;

    @Autowired
    public TemplateCoverService(TemplateCoverRepository templateCoverRepository, StorageGateway storageGateway) {
        this(templateCoverRepository, storageGateway, Clock.systemUTC());
    }

    public TemplateCoverService(
        TemplateCoverRepository templateCoverRepository,
        StorageGateway storageGateway,
        Clock clock
    ) {
        this.templateCoverRepository = templateCoverRepository;
        this.storageGateway = storageGateway;
        this.clock = clock;
    }

    public CoverContent readPublicContent(long coverAssetId) {
        if (coverAssetId < 1) {
            throw notFound();
        }
        TemplateCoverRepository.PublicCover cover = templateCoverRepository
            .findPublicReferencedCover(coverAssetId, clock.instant())
            .orElseThrow(this::notFound);
        return new CoverContent(storageGateway.readBounded(cover.objectKey(), MAX_COVER_BYTES), cover.mimeType());
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "模板封面不存在或未发布");
    }

    public record CoverContent(byte[] bytes, String mimeType) {
        public CoverContent {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
