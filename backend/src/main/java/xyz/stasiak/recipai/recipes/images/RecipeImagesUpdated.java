package xyz.stasiak.recipai.recipes.images;

import java.util.Set;

record RecipeImagesUpdated(
        Set<ImageMetadata> toAdd,
        Set<ImageMetadata> toDelete
) {
}
