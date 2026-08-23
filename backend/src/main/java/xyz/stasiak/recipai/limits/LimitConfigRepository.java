package xyz.stasiak.recipai.limits;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface LimitConfigRepository extends JpaRepository<LimitConfig, UUID> {

    @Query("""
            SELECT c FROM LimitConfig c
             WHERE c.resource = :resource AND (c.subject = :subject OR c.subject IS NULL)
             ORDER BY c.subject NULLS LAST
             LIMIT 1
            """)
    Optional<LimitConfig> resolve(@Param("resource") String resource, @Param("subject") String subject);

    @Query("""
            SELECT c FROM LimitConfig c
             WHERE c.subject = :subject OR c.subject IS NULL
             ORDER BY c.resource, c.subject NULLS LAST
            """)
    List<LimitConfig> findResolutionCandidates(@Param("subject") String subject);

    default List<LimitConfig> resolveAll(String subject) {
        Map<String, LimitConfig> byResource = new LinkedHashMap<>();
        for (LimitConfig config : findResolutionCandidates(subject)) {
            byResource.putIfAbsent(config.getResource(), config);
        }
        return List.copyOf(byResource.values());
    }
}
