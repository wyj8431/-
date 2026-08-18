package com.example.lowcode.template.infrastructure;

import com.example.lowcode.template.application.TemplateCoverRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class MyBatisTemplateCoverRepository implements TemplateCoverRepository {
    private final TemplateCoverAssetMapper templateCoverAssetMapper;

    public MyBatisTemplateCoverRepository(TemplateCoverAssetMapper templateCoverAssetMapper) {
        this.templateCoverAssetMapper = templateCoverAssetMapper;
    }

    @Override
    public Optional<PublicCover> findPublicReferencedCover(long coverAssetId, Instant now) {
        return Optional.ofNullable(templateCoverAssetMapper.findPublicReferencedCover(
            coverAssetId,
            Timestamp.from(now)
        )).map(row -> new PublicCover(row.getId(), row.getObjectKey(), row.getMimeType()));
    }
}
