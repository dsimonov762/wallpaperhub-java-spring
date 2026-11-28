package dev.wallpaperhub.account;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Tells "PostgreSQL cannot be reached or does not answer in time" from every other failure: the first is a 503 / UNAVAILABLE, the rest a 500 / INTERNAL. */
public final class DatabaseFailures {

    private DatabaseFailures() {}

    public static boolean isUnavailable(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof CannotCreateTransactionException
                    || t instanceof DataAccessResourceFailureException
                    || t instanceof TransientDataAccessException
                    || t instanceof SQLTransientConnectionException
                    || t instanceof SQLNonTransientConnectionException
                    || t instanceof ConnectException
                    || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
