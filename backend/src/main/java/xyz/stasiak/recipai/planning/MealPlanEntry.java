package xyz.stasiak.recipai.planning;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "meal_plan_entries")
@Getter
@Setter
@ToString
@NoArgsConstructor
class MealPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "placeholder_text")
    private String placeholderText;

    @Column(name = "serving_size")
    private Integer servingSize;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    MealPlanEntry(UUID planId, LocalDate date, UUID recipeId, String placeholderText, Integer servingSize) {
        this.planId = planId;
        this.date = date;
        this.recipeId = recipeId;
        this.placeholderText = placeholderText;
        this.servingSize = servingSize;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MealPlanEntry that = (MealPlanEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
