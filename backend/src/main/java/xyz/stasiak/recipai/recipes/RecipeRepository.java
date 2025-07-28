package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface RecipeRepository extends JpaRepository<Recipe, UUID> {
}