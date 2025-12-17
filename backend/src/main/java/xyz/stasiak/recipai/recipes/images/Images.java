package xyz.stasiak.recipai.recipes.images;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

record Images(List<ImageMetadata> imagesMetadata) {
    static Images empty() {
        return new Images(List.of());
    }

    ImageMetadata firstImageMetadata() {
        if (imagesMetadata.isEmpty()) {
            return null;
        }
        return imagesMetadata.getFirst();
    }

    List<UUID> getImageIds() {
        return imagesMetadata.stream().map(ImageMetadata::id).toList();
    }

    Set<ImageMetadata> getImagesMetadata(Collection<UUID> imageIds) {
        return imagesMetadata.stream()
                .filter(img -> imageIds.contains(img.id()))
                .collect(Collectors.toSet());
    }

    Images addAll(Collection<ImageMetadata> imagesToAdd) {
        List<ImageMetadata> newImages = new ArrayList<>(imagesMetadata);
        newImages.addAll(imagesToAdd);
        return new Images(List.copyOf(newImages));
    }

    Images deleteAll(Collection<UUID> imageIdsToDelete) {
        return new Images(imagesMetadata.stream()
                .filter(img -> !imageIdsToDelete.contains(img.id()))
                .toList());
    }

    Images reorder(List<UUID> orderedIds) {
        Map<UUID, ImageMetadata> metadataMap = imagesMetadata.stream()
                .collect(Collectors.toMap(ImageMetadata::id, Function.identity()));

        List<ImageMetadata> reordered = orderedIds.stream()
                .map(metadataMap::get)
                .filter(Objects::nonNull)
                .toList();

        return new Images(reordered);
    }
}
