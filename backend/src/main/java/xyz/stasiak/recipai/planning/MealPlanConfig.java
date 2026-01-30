package xyz.stasiak.recipai.planning;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MealPlanProperties.class)
class MealPlanConfig {
}
