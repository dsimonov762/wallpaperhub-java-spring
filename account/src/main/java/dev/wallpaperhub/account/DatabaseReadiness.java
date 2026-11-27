package dev.wallpaperhub.account;

import dev.wallpaperhub.platform.management.ReadinessCheck;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** {@code /health/ready} is DOWN while PostgreSQL cannot be reached (the pool waits at most 2 s for a connection, see application.yml). */
@Component
class DatabaseReadiness implements ReadinessCheck {

    private final DataSource dataSource;

    DatabaseReadiness(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public boolean ready() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }
}
