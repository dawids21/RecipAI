package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recipes")
@Getter
@Setter
@ToString
@NoArgsConstructor
class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode data;

    @Column(name = "recipes_collection_id")
    private UUID recipesCollectionId;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Recipe recipe = (Recipe) o;
        return Objects.equals(id, recipe.id) && Objects.equals(name, recipe.name) && Objects.equals(data, recipe.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, data);
    }
}