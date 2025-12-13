package xyz.stasiak.recipai.recipes.images;

import java.util.ArrayList;
import java.util.List;

record Images(List<ImageMetadata> imagesMetadata) {
    static Images empty() {
        return new Images(List.of());
    }

    int size() {
        return imagesMetadata.size();
    }

    Images add(ImageMetadata imageMetadata) {
        if (imagesMetadata.stream().anyMatch(img -> img.id().equals(imageMetadata.id()))) {
            return this;
        }
        List<ImageMetadata> newImages = new ArrayList<>(imagesMetadata);
        newImages.add(imageMetadata);
        return new Images(List.copyOf(newImages));
    }

    ImageMetadata firstImageMetadata() {
        if (imagesMetadata.isEmpty()) {
            return null;
        }
        return imagesMetadata.getFirst();
    }
}
