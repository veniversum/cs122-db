package edu.caltech.test.nanodb.writeahead;

import edu.caltech.nanodb.expressions.TupleLiteral;
import edu.caltech.nanodb.server.CommandResult;
import org.testng.annotations.Test;

@Test
public class TestSingleDataPageWal extends TransactionTestCase {

    public TestSingleDataPageWal() {
        super("setup_testWal");
    }

    public void testTransactionCommit() throws Throwable {

        TupleLiteral[] expected = {
                new TupleLiteral(1, 2),
                new TupleLiteral(1, 2),
        };

        CommandResult result = tryMultipleCommands(new String[]{
                "BEGIN",
                "CREATE TABLE transac_commit (a int, b int)",
                "INSERT INTO transac_commit VALUES (1, 2)",
                "INSERT INTO transac_commit VALUES (3, 4)",
                "INSERT INTO transac_commit VALUES (997, 999)",
                "INSERT INTO transac_commit VALUES (999, 999)",
                "UPDATE transac_commit SET a = 1, b = 2 WHERE b = 4",
                "DELETE FROM transac_commit WHERE b = 999",
                "COMMIT",
                "SELECT * FROM transac_commit",
        }, true);

        assert checkSizeResults(expected, result);
        assert checkUnorderedResults(expected, result);

    }

    public void testTransactionCommitAfterRestart() throws Throwable {

        TupleLiteral[] expected = {
                new TupleLiteral(1, 2),
                new TupleLiteral(1, 2),
        };

        tryMultipleCommands(new String[]{
                "BEGIN",
                "CREATE TABLE transac_commit_restart (a int, b int)",
                "INSERT INTO transac_commit_restart VALUES (1, 2)",
                "INSERT INTO transac_commit_restart VALUES (3, 4)",
                "INSERT INTO transac_commit_restart VALUES (997, 999)",
                "INSERT INTO transac_commit_restart VALUES (999, 999)",
                "UPDATE transac_commit_restart SET a = 1, b = 2 WHERE b = 4",
                "DELETE FROM transac_commit_restart WHERE b = 999",
                "COMMIT",
        }, false);

        restartServer();

        CommandResult result = tryMultipleCommands(new String[]{
                "SELECT * FROM transac_commit_restart",
        }, true);

        assert checkSizeResults(expected, result);
        assert checkUnorderedResults(expected, result);

    }

    public void testTransactionRollback() throws Throwable {

        TupleLiteral[] expected = {
                new TupleLiteral(1, 0),
                new TupleLiteral(2, 0),
        };

        restartServer();

        CommandResult result = tryMultipleCommands(new String[]{
                "BEGIN",
                "INSERT INTO test_wal_t1 VALUES (99, 99)",
                "INSERT INTO test_wal_t1 VALUES (99, 99)",
                "UPDATE test_wal_t1 SET a = 100 WHERE NOT b = 0",
                "DELETE FROM test_wal_t1 WHERE b = 0",
                "ROLLBACK",
                "SELECT * FROM test_wal_t1",
        }, true);

        assert checkSizeResults(expected, result);
        assert checkUnorderedResults(expected, result);

    }

    public void testTwoTransactionsCommitCommit() throws Throwable {

        TupleLiteral[] expected = {
                new TupleLiteral(1, 2),
                new TupleLiteral(1, 2),
        };

        CommandResult result = tryMultipleCommands(new String[]{
                "BEGIN",
                "CREATE TABLE transac_commit_commit (a int, b int)",
                "INSERT INTO transac_commit_commit VALUES (1, 2)",
                "INSERT INTO transac_commit_commit VALUES (3, 4)",
                "COMMIT",
                "BEGIN",
                "INSERT INTO transac_commit_commit VALUES (997, 999)",
                "INSERT INTO transac_commit_commit VALUES (999, 999)",
                "UPDATE transac_commit_commit SET a = 1, b = 2 WHERE b = 4",
                "DELETE FROM transac_commit_commit WHERE b = 999",
                "COMMIT",
                "SELECT * FROM transac_commit_commit",
        }, true);

        assert checkSizeResults(expected, result);
        assert checkUnorderedResults(expected, result);

    }

    public void testTwoTransactionsCommitAbort() throws Throwable {

        TupleLiteral[] expected = {
                new TupleLiteral(1, 2),
                new TupleLiteral(3, 4),
                new TupleLiteral(997, 999),
        };

        CommandResult result = tryMultipleCommands(new String[]{
                "BEGIN",
                "CREATE TABLE transac_commit_abort (a int, b int)",
                "INSERT INTO transac_commit_abort VALUES (1, 2)",
                "INSERT INTO transac_commit_abort VALUES (3, 4)",
                "INSERT INTO transac_commit_abort VALUES (997, 999)",
                "COMMIT",
                "BEGIN",
                "INSERT INTO transac_commit_abort VALUES (999, 999)",
                "UPDATE transac_commit_abort SET a = 1, b = 2 WHERE b = 4",
                "DELETE FROM transac_commit_abort WHERE b = 999",
                "ROLLBACK",
                "SELECT * FROM transac_commit_abort",
        }, true);

        assert checkSizeResults(expected, result);
        assert checkUnorderedResults(expected, result);

    }

    public void testTwoTransactionsAbortAbort() throws Throwable {

        TupleLiteral[] expected = {};

        CommandResult result = tryMultipleCommands(new String[]{
                "CREATE TABLE transac_abort_abort (a int, b int)",
                "BEGIN",
                "INSERT INTO transac_abort_abort VALUES (1, 2)",
                "INSERT INTO transac_abort_abort VALUES (3, 4)",
                "INSERT INTO transac_abort_abort VALUES (997, 999)",
                "ROLLBACK",
                "BEGIN",
                "INSERT INTO transac_abort_abort VALUES (999, 999)",
                "UPDATE transac_abort_abort SET a = 1, b = 2 WHERE b = 4",
                "DELETE FROM transac_abort_abort WHERE b = 999",
                "ROLLBACK",
                "SELECT * FROM transac_abort_abort",
        }, true);

        assert checkSizeResults(expected, result);
        assert checkUnorderedResults(expected, result);

    }

}
