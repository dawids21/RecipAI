package xyz.stasiak.recipai.limits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "limit_config")
@Getter
@Setter
@ToString
@NoArgsConstructor
class LimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String resource;

    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LimitKind kind;

    @Column(name = "max_value", nullable = false)
    private int maxValue;

    @Enumerated(EnumType.STRING)
    private LimitPeriod period;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LimitConfig that = (LimitConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
