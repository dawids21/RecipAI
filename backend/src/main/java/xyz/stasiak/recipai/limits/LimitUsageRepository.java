package xyz.stasiak.recipai.limits;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface LimitUsageRepository extends JpaRepository<LimitUsage, LimitUsageId> {

    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}limit_usage (resource, subject, used, period_start)
            SELECT :resource, :subject, 1, :now
             WHERE :max > 0
            ON CONFLICT (resource, subject) DO UPDATE SET
                used         = CASE WHEN limit_usage.period_start <= :cutoff THEN 1    ELSE limit_usage.used + 1     END,
                period_start = CASE WHEN limit_usage.period_start <= :cutoff THEN :now ELSE limit_usage.period_start END
            WHERE limit_usage.period_start <= :cutoff
               OR limit_usage.used < :max
            """, nativeQuery = true)
    int reserve(@Param("resource") String resource, @Param("subject") String subject,
                @Param("now") Instant now, @Param("cutoff") Instant cutoff, @Param("max") int max);
}
