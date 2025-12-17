package xyz.stasiak.recipai.recipes.images;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import xyz.stasiak.recipai.recipes.images.exception.ImageLimitExceededException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "recipe_images")
@Getter
@Setter
@NoArgsConstructor
class RecipeImages {
    private static final int MAX_IMAGES = 2;

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Images images = Images.empty();

    @Version
    @Column(nullable = false)
    private Long version;

    RecipeImages(UUID id) {
        this.id = id;
    }

    ImageMetadata getFirstImageMetadata() {
        return images.firstImageMetadata();
    }

    RecipeImagesUpdated updateImages(List<UUID> newImages, List<ImageMetadata> newImagesMetadata) {
        if (newImages.size() > MAX_IMAGES) {
            throw new ImageLimitExceededException(String.format("Maximum %d images allowed", MAX_IMAGES));
        }

        List<UUID> currentImages = images.getImageIds();
        Set<UUID> currentSet = new HashSet<>(currentImages);
        Set<UUID> newSet = new HashSet<>(newImages);

        Set<UUID> toDeleteIds = new HashSet<>(currentSet);
        toDeleteIds.removeAll(newSet);
        Set<ImageMetadata> toDelete = images.getImagesMetadata(toDeleteIds);

        Set<UUID> toAddIds = new HashSet<>(newSet);
        toAddIds.removeAll(currentSet);
        Set<ImageMetadata> toAdd = newImagesMetadata.stream()
                .filter(meta -> toAddIds.contains(meta.id()))
                .collect(Collectors.toSet());

        images = images.deleteAll(toDeleteIds);
        images = images.addAll(toAdd);
        if (!images.getImageIds().equals(newImages)) {
            images = images.reorder(newImages);
        }

        return new RecipeImagesUpdated(toAdd, toDelete);
    }
}
