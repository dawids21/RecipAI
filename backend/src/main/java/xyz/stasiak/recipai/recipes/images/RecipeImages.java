package xyz.stasiak.recipai.recipes.images;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import xyz.stasiak.recipai.recipes.images.exception.ImageLimitExceededException;

import java.util.UUID;

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

    void addImage(ImageMetadata imageMetadata) {
        if (images.size() >= MAX_IMAGES) {
            throw new ImageLimitExceededException(String.format("Maximum %d imagesMetadata allowed", MAX_IMAGES));
        }
        images = images.add(imageMetadata);
    }

    ImageMetadata getFirstImageMetadata() {
        return images.firstImageMetadata();
    }
}
