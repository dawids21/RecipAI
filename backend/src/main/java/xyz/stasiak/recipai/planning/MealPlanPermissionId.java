package xyz.stasiak.recipai.planning;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
record MealPlanPermissionId(String email, UUID planId) implements Serializable {
}
