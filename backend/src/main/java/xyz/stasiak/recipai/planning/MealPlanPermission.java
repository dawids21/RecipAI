package xyz.stasiak.recipai.planning;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;

@Entity
@Table(name = "meal_plan_permissions")
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
class MealPlanPermission {

    @EmbeddedId
    private MealPlanPermissionId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    boolean hasOwnerRights() {
        return role == UserRole.OWNER;
    }

    boolean hasEditorRights() {
        return hasOwnerRights() || role == UserRole.EDITOR;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MealPlanPermission that = (MealPlanPermission) o;
        return Objects.equals(id, that.id) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role);
    }
}
