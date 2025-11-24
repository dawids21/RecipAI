package xyz.stasiak.recipai.recipes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Entity
@Table(name = "recipe_permission")
@Getter
@Setter
@ToString
@NoArgsConstructor
class RecipePermission {

    @EmbeddedId
    private RecipePermissionId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RecipePermission recipePermission = (RecipePermission) o;
        return Objects.equals(id, recipePermission.id) && role == recipePermission.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role);
    }
}
