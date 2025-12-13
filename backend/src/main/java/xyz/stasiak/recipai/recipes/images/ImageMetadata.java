package xyz.stasiak.recipai.recipes.images;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.UUID;

record ImageMetadata(UUID id, @JsonUnwrapped ContentType contentType) {
}