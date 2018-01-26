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
     * TODO: Inner:
     *          - Left empty, right full
     *          - Left full, right empty
     *          - Left empty, right empty
     *          - Some row matches multiple rows in another table
     *          - Some rows match a single row in another table
     *          - Some rows match multiple rows in another table
     */

    /**
     * This test checks that inner, left outer and right outer joins are performed correctly
     * on when two non-empty tables are used.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinsNeitherEmpty() throws Throwable {
        CommandResult result;

        // Inner join on 2 tables with implicit table column name
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 JOIN test_sj_t2 t2 ON a = b;", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral(1, 1),
        };
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T1.A", "T2.B");

        // Inner join on 2 tables with explicit table column name
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

        // Right outer join on 2 tables
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 RIGHT JOIN test_sj_t2 t2 ON t1.a = t2.b;", true);
        TupleLiteral[] expected4 = {
                new TupleLiteral(1, 1),
                new TupleLiteral(null, 5),
        };
        assert checkSizeResults(expected4, result);
        assert checkUnorderedResults(expected4, result);
        checkResultSchema(result, "T1.A", "T2.B");
    }

    /**
     * This test checks that inner, left outer and right outer joins are performed correctly
     * on when the left table is empty.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinLeftEmpty() throws Exception {
        CommandResult result;

        // Inner join with left table empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t3 t3 JOIN test_sj_t2 t2 ON t3.c = t2.b;", true);
        TupleLiteral[] expected1 = {};
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T3.C", "T2.B");

        // Left outer join with left table empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t3 t3 LEFT JOIN test_sj_t2 t2 ON t3.c = t2.b;", true);
        TupleLiteral[] expected2 = {};
        assert checkSizeResults(expected2, result);
        assert checkUnorderedResults(expected2, result);
        checkResultSchema(result, "T3.C", "T2.B");

        // Right outer join with left table empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t3 t3 RIGHT JOIN test_sj_t2 t2 ON t3.c = t2.b;", true);
        TupleLiteral[] expected3 = {
                new TupleLiteral(null, 1),
                new TupleLiteral(null, 5),
        };
        assert checkSizeResults(expected3, result);
        assert checkUnorderedResults(expected3, result);
        checkResultSchema(result, "T3.C", "T2.B");
    }

    /**
     * This test checks that inner, left outer and right outer joins are performed correctly
     * on when the right table is empty.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinRightEmpty() throws Exception {
        CommandResult result;

        // Inner join with right table empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t2 t2 JOIN test_sj_t3 t3 ON t2.b = t3.c;", true);
        TupleLiteral[] expected1 = {};
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T2.B", "T3.C");

        // Left outer join with right table empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t2 t2 LEFT JOIN test_sj_t3 t3 ON t2.b = t3.c;", true);
        TupleLiteral[] expected2 = {
                new TupleLiteral(1, null),
                new TupleLiteral(5, null),
        };
        assert checkSizeResults(expected2, result);
        assert checkUnorderedResults(expected2, result);
        checkResultSchema(result, "T2.B", "T3.C");

        // Right outer join with right table empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t2 t2 RIGHT JOIN test_sj_t3 t3 ON t2.b = t3.c;", true);
        TupleLiteral[] expected3 = {};
        assert checkSizeResults(expected3, result);
        assert checkUnorderedResults(expected3, result);
        checkResultSchema(result, "T2.B", "T3.C");
    }

    /**
     * This test checks that inner, left outer and right outer joins are performed correctly
     * on when both tables are empty.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinBothEmpty() throws Exception {
        CommandResult result;

        // Inner join with both tables empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t4 t4 JOIN test_sj_t3 t3 ON t4.d = t3.c;", true);
        TupleLiteral[] expected1 = {};
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T4.D", "T3.C");

        // Left outer join with both tables empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t4 t4 LEFT JOIN test_sj_t3 t3 ON t4.d = t3.c;", true);
        TupleLiteral[] expected2 = {};
        assert checkSizeResults(expected2, result);
        assert checkUnorderedResults(expected2, result);
        checkResultSchema(result, "T4.D", "T3.C");

        // Right outer join with both tables empty
        result = server.doCommand(
                "SELECT * FROM test_sj_t4 t4 RIGHT JOIN test_sj_t3 t3 ON t4.d = t3.c;", true);
        TupleLiteral[] expected3 = {};
        assert checkSizeResults(expected3, result);
        assert checkUnorderedResults(expected3, result);
        checkResultSchema(result, "T4.D", "T3.C");
    }

    /**
     * This test checks that inner, left outer and right outer joins are performed correctly
     * when some row from the left table matches multiple rows in the right table.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinMatchMultipleInRight() throws Exception {
        CommandResult result;

        // Inner join with multiple matches
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 JOIN test_sj_t5 t5 ON t1.a = t5.e;", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral(3, 3, 10),
                new TupleLiteral(3, 3, 25),
        };
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T1.A", "T5.E", "T5.F");

        // Left outer join with multiple matches
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 LEFT JOIN test_sj_t5 t5 ON t1.a = t5.e;", true);
        TupleLiteral[] expected2 = {
                new TupleLiteral(1, null, null),
                new TupleLiteral(3, 3, 10),
                new TupleLiteral(3, 3, 25),
        };
        assert checkSizeResults(expected2, result);
        assert checkUnorderedResults(expected2, result);
        checkResultSchema(result, "T1.A", "T5.E", "T5.F");

        // Right outer join with multiple matches
        result = server.doCommand(
                "SELECT * FROM test_sj_t1 t1 RIGHT JOIN test_sj_t5 t5 ON t1.a = t5.e;", true);
        TupleLiteral[] expected3 = {
                new TupleLiteral(null, 15, 0),
                new TupleLiteral(3, 3, 10),
                new TupleLiteral(3, 3, 25),
        };
        assert checkSizeResults(expected3, result);
        assert checkUnorderedResults(expected3, result);
        checkResultSchema(result, "T1.A", "T5.E", "T5.F");
    }

    /**
     * This test checks that inner, left outer and right outer joins are performed correctly
     * when some row from the right table matches multiple rows in the left table.
     *
     * @throws Exception if any query parsing or execution issues occur.
     **/
    public void testSimpleJoinMatchMultipleInLeft() throws Exception {
        CommandResult result;

        // Inner join with multiple matches
        result = server.doCommand(
                "SELECT * FROM test_sj_t5 t5 JOIN test_sj_t1 t1 ON t5.e = t1.a;", true);
        TupleLiteral[] expected1 = {
                new TupleLiteral(3, 10, 3),
                new TupleLiteral(3, 25, 3),
        };
        assert checkSizeResults(expected1, result);
        assert checkUnorderedResults(expected1, result);
        checkResultSchema(result, "T5.E", "T5.F", "T1.A");

        // Left outer join with multiple matches
        result = server.doCommand(
                "SELECT * FROM test_sj_t5 t5 LEFT JOIN test_sj_t1 t1 ON t5.e = t1.a;", true);
        TupleLiteral[] expected2 = {
                new TupleLiteral(15, 0, null),
                new TupleLiteral(3, 10, 3),
                new TupleLiteral(3, 25, 3),
        };
        assert checkSizeResults(expected2, result);
        assert checkUnorderedResults(expected2, result);
        checkResultSchema(result, "T5.E", "T5.F", "T1.A");

        // Right outer join with multiple matches
        result = server.doCommand(
                "SELECT * FROM test_sj_t5 t5 RIGHT JOIN test_sj_t1 t1 ON t5.e = t1.a;", true);
        TupleLiteral[] expected3 = {
                new TupleLiteral(null, null, 1),
                new TupleLiteral(3, 10, 3),
                new TupleLiteral(3, 25, 3),
        };
        assert checkSizeResults(expected3, result);
        assert checkUnorderedResults(expected3, result);
        checkResultSchema(result, "T5.E", "T5.F", "T1.A");
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
