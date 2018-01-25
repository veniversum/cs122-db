package edu.caltech.test.nanodb.sql;

import edu.caltech.nanodb.expressions.TupleLiteral;
import edu.caltech.nanodb.server.CommandResult;
import org.testng.annotations.Test;

/**
 * This class tests the LIMIT and OFFSET operators, and check that they
 * work both alone and with each other.
 * <p>
 * Tests for multiple edge cases and not-so-well defined behaviors too.
 */
@Test
public class TestLimitOffset extends SqlTestCase {
    public TestLimitOffset() {
        super("setup_testLimitOffset");
    }

    /**
     * Tests the OFFSET clause by itself.
     *
     * @throws Exception propagate exceptions
     */
    @Test
    public void testOffsetOnly() throws Exception {
        CommandResult result;

        // Check that offset 0 is a NO-OP.
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 OFFSET 0", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral("1"),
                new TupleLiteral("2"),
                new TupleLiteral("3"),
                new TupleLiteral("4"),
                new TupleLiteral("5"),
                new TupleLiteral("6"),
                new TupleLiteral("7"),
        };
        assert checkSizeResults(expected1, result);
        assert checkOrderedResults(expected1, result);

        // Check that offset skips the correct number of rows
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 OFFSET 3", true);
        TupleLiteral[] expected2 = {
                new TupleLiteral("4"),
                new TupleLiteral("5"),
                new TupleLiteral("6"),
                new TupleLiteral("7"),
        };
        assert checkSizeResults(expected2, result);
        assert checkOrderedResults(expected2, result);

        // Check that offset >= number of expected rows return no rows
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 OFFSET 10", true);
        TupleLiteral[] expected3 = {};
        assert checkSizeResults(expected3, result);
        assert checkOrderedResults(expected3, result);
    }

    /**
     * Tests the LIMIT clause by itself.
     *
     * @throws Exception propagate exceptions
     */
    @Test
    public void testLimitOnly() throws Exception {
        CommandResult result;

        // Check that limit restricts the number of output rows
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 LIMIT 3", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral("1"),
                new TupleLiteral("2"),
                new TupleLiteral("3"),
        };
        assert checkSizeResults(expected1, result);
        assert checkOrderedResults(expected1, result);

        // Check that limit = 0 return no rows
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 LIMIT 0", true);
        TupleLiteral[] expected2 = {};
        assert checkSizeResults(expected2, result);
        assert checkOrderedResults(expected2, result);

        // Check that limit >= number of expected rows return all rows
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 LIMIT 10", true);
        TupleLiteral[] expected3 = {
                new TupleLiteral("1"),
                new TupleLiteral("2"),
                new TupleLiteral("3"),
                new TupleLiteral("4"),
                new TupleLiteral("5"),
                new TupleLiteral("6"),
                new TupleLiteral("7"),
        };
        assert checkSizeResults(expected3, result);
        assert checkOrderedResults(expected3, result);
    }

    /**
     * Tests the both LIMIT and OFFSET clause in the same statement.
     *
     * @throws Exception propagate exceptions
     */
    @Test
    public void testLimitAndOffset() throws Exception {
        CommandResult result;

        // Check that offset skips the correct number of rows and
        // limit restricts the number of output rows.
        result = server.doCommand(
                "SELECT a FROM test_lo_t1 LIMIT 3 OFFSET 3", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral("4"),
                new TupleLiteral("5"),
                new TupleLiteral("6"),
        };
        assert checkSizeResults(expected1, result);
        assert checkOrderedResults(expected1, result);
    }

    /**
     * Checks that test table is initialized properly.
     *
     * @throws Throwable propagate exceptions
     */
    @Test
    public void testLimitOffsetTablesNotEmpty() throws Throwable {
        testTableNotEmpty("test_lo_t1");
    }
}
