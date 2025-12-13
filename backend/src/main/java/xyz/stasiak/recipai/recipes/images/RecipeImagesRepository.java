package xyz.stasiak.recipai.recipes.images;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface RecipeImagesRepository extends JpaRepository<RecipeImages, UUID> {
}
