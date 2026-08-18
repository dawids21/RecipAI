package xyz.stasiak.recipai.limits;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
