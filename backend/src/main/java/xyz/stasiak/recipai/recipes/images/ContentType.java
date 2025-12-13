package xyz.stasiak.recipai.recipes.images;

import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;

import java.util.Objects;

record ContentType(String type) {
    public ContentType {
        type = Objects.requireNonNullElse(type, "");
    }

    static ContentType JPEG() {
        return new ContentType(MediaType.IMAGE_JPEG_VALUE);
    }

    String toExtension() {
        if (type.equals(MediaType.IMAGE_JPEG_VALUE)) {
            return "jpg";
        } else if (type.equals(MediaType.IMAGE_PNG_VALUE)) {
            return "png";
        } else if (type.isBlank()) {
            return "";
        } else {
            throw new IllegalArgumentException("Unsupported content type: " + type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ContentType that = (ContentType) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }

    @Override
    @NonNull
    public String toString() {
        return type;
    }
}
