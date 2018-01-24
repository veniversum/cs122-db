package edu.caltech.test.nanodb.sql;


import edu.caltech.nanodb.expressions.TupleLiteral;
import edu.caltech.nanodb.server.CommandResult;
import org.testng.annotations.Test;


/**
 * This class performs some basic tests with NATURAL joins and joins with the
 * USING clause, to ensure that both joins and projections are done properly.
 * These tests aren't exhaustive; they serve as a smoke-test to verify the
 * basic behaviors.
 */
@Test
public class TestSimpleJoins extends SqlTestCase {
    public TestSimpleJoins() {
        super("setup_testSimpleJoins");
    }

    /**
     * This test checks that at the test tables are properly initialized.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinsTablesIntegrity() throws Throwable {
        testTableNotEmpty("test_sj_t1");
        testTableNotEmpty("test_sj_t2");
        testTableEmpty("test_sj_t3");
        testTableEmpty("test_sj_t4");
    }

    /**
     * This test checks that at least one value was successfully inserted into
     * each of the test tables.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinsNeitherEmpty() throws Throwable {
        CommandResult result;

        // Join on 2 tables with implicit table column name
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 JOIN test_sj_t2 t2 ON a = b;", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral(1, 1),
        };
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T1.A", "T2.B");

        // Join on 2 tables with explicit table column name
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 JOIN test_sj_t2 t2 ON t1.a = t2.b;", true);
        TupleLiteral[] expected2 = {
                new TupleLiteral(1, 1),
        };
        assert checkSizeResults(expected2, result);
        assert checkUnorderedResults(expected2, result);
        checkResultSchema(result, "T1.A", "T2.B");

        // Left outer join on 2 tables
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 LEFT JOIN test_sj_t2 t2 ON t1.a = t2.b;", true);
        TupleLiteral[] expected3 = {
                new TupleLiteral(1, 1),
                new TupleLiteral(3, null),
        };
        assert checkSizeResults(expected3, result);
        assert checkUnorderedResults(expected3, result);
        checkResultSchema(result, "T1.A", "T2.B");
    }

    /**
     * Ensure that referencing old table name in joins throws an exception
     *
     * @throws Exception expected exception ExecutionException
     */
    @Test(expectedExceptions = edu.caltech.nanodb.commands.ExecutionException.class)
    public void testSimpleJoinRenameTable() throws Exception {
        CommandResult result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 JOIN test_sj_t2 t2 ON test_sb_t1.a = test_sj_t2.b;", true);
        checkSizeResults(new TupleLiteral[]{}, result);
    }
}
