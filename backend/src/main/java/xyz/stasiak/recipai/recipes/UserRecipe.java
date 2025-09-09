package xyz.stasiak.recipai.recipes;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Entity
@Table(name = "user_recipes")
@Getter
@Setter
@ToString
@NoArgsConstructor
class UserRecipe {

    @EmbeddedId
    private UserRecipeId id;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UserRecipe userRecipe = (UserRecipe) o;
        return Objects.equals(id, userRecipe.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}