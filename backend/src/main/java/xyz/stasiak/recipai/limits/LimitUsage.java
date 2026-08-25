package xyz.stasiak.recipai.limits;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "limit_usage")
@Getter
@Setter
@ToString
@NoArgsConstructor
class LimitUsage {

    @EmbeddedId
    private LimitUsageId id;

    @Column(nullable = false)
    private int used;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    LimitBalance toBalance(Long resetsInSeconds) {
        return new LimitBalance(used, periodStart, resetsInSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LimitUsage that = (LimitUsage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
