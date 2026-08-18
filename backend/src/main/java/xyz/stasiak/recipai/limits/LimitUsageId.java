package xyz.stasiak.recipai.limits;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
record LimitUsageId(String resource, String subject) implements Serializable {
}
