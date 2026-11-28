package dev.wallpaperhub.account;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Tells "PostgreSQL cannot be reached or does not answer in time" from every other failure: the first is a 503 / UNAVAILABLE, the rest
 * a 500 / INTERNAL. Looks at the whole chain of causes: Spring, Hibernate and the driver each wrap what is below them.
 */
public final class DatabaseFailures {

    private DatabaseFailures() {}

    public static boolean isUnavailable(Throwable failure) {
        // A cause chain may loop (a -> b -> a): every throwable is looked at once, compared by identity.
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof CannotCreateTransactionException
                    || t instanceof DataAccessResourceFailureException
                    || t instanceof TransientDataAccessException
                    || t instanceof SQLTransientConnectionException
                    || t instanceof SQLNonTransientConnectionException
                    || t instanceof ConnectException
                    || t instanceof SocketException
                    || t instanceof SocketTimeoutException
                    || isConnectionError(t)) {
                return true;
            }
        }
        return false;
    }

    /** SQLSTATE class 08 is "connection exception": the driver could not connect, or lost the connection (reset, broken pipe, timeout). */
    private static boolean isConnectionError(Throwable t) {
        return t instanceof SQLException sql
                && sql.getSQLState() != null
                && sql.getSQLState().startsWith("08");
    }
}
