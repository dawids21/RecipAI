package xyz.stasiak.recipai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class RecomputeMigration {

    private RecomputeMigration() {
    }

    public static void run(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO recipai");
                try {
                    ScriptUtils.executeSqlScript(connection,
                            new ClassPathResource("db/migration/R__recompute_limit_usage.sql"));
                } finally {
                    statement.execute("RESET search_path");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to run the recompute migration", e);
        }
    }
}
