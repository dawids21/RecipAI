package xyz.stasiak.recipai;

import java.util.UUID;

public final class TestIdentities {

    private TestIdentities() {
    }

    public static String freshToken() {
        return "u" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String emailOf(String token) {
        return token + "@local.test";
    }
}
