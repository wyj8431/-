package com.example.lowcode.template.application;

import com.example.lowcode.asset.application.StorageGateway;
import com.example.lowcode.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateCoverServiceTest {
    @Test
    void readsPublishedReferencedCoverWithoutExposingItsObjectKey() {
        FakeTemplateCoverRepository repository = new FakeTemplateCoverRepository();
        repository.add(new TemplateCoverRepository.PublicCover(2001L, "platform/cover/2001.png", "image/png"));
        FakeStorageGateway storage = new FakeStorageGateway();
        storage.objects.put("platform/cover/2001.png", new byte[] {1, 2, 3});
        TemplateCoverService service = new TemplateCoverService(repository, storage);

        TemplateCoverService.CoverContent content = service.readPublicContent(2001L);

        assertThat(content.bytes()).containsExactly(1, 2, 3);
        assertThat(content.mimeType()).isEqualTo("image/png");
        assertThat(storage.lastReadKey).isEqualTo("platform/cover/2001.png");
    }

    @Test
    void hidesUnreferencedOrNonPublicCoverAsNotFound() {
        FakeTemplateCoverRepository repository = new FakeTemplateCoverRepository();
        TemplateCoverService service = new TemplateCoverService(repository, new FakeStorageGateway());

        assertThatThrownBy(() -> service.readPublicContent(2002L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("封面");
    }

    private static final class FakeTemplateCoverRepository implements TemplateCoverRepository {
        private final Map<Long, PublicCover> covers = new HashMap<>();

        @Override
        public Optional<PublicCover> findPublicReferencedCover(long coverAssetId, Instant now) {
            return Optional.ofNullable(covers.get(coverAssetId));
        }

        void add(PublicCover cover) {
            covers.put(cover.id(), cover);
        }
    }

    private static final class FakeStorageGateway implements StorageGateway {
        private final Map<String, byte[]> objects = new HashMap<>();
        private String lastReadKey;

        @Override
        public String presignPut(String objectKey, String mimeType, Duration expiresIn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] readBounded(String objectKey, long maxBytes) {
            lastReadKey = objectKey;
            return objects.get(objectKey);
        }

        @Override
        public void promote(String temporaryObjectKey, String finalObjectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }
    }
}
