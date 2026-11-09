package xyz.stasiak.recipai.planning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningFacade {

    private final MealPlanService mealPlanService;

    public void deleteAllByEmail(String email) {
        log.debug("Deleting all meal plans owned by user {}", email);
        mealPlanService.deleteAllByEmail(email);
    }
}
