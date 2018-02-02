package edu.caltech.test.nanodb.costing;

import edu.caltech.nanodb.expressions.*;
import edu.caltech.nanodb.queryeval.SelectivityEstimator;
import edu.caltech.nanodb.queryeval.TableStats;
import edu.caltech.nanodb.relations.TableInfo;
import edu.caltech.nanodb.server.CommandResult;
import edu.caltech.nanodb.storage.StorageManager;
import edu.caltech.nanodb.storage.TableManager;
import edu.caltech.test.nanodb.sql.SqlTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This test class exercises the functionality of
 * costing the {@link edu.caltech.nanodb.expressions.InValuesOperator} in the
 * {@link edu.caltech.nanodb.queryeval.SelectivityEstimator} class.
 */
@Test
public class TestInValuesOperatorCosting extends SqlTestCase {

    private enum TestTable {
        T1("test_inval_t1");

        private final String tableName;

        TestTable(String rep) {
            tableName = rep;
        }

        public String toString() {
            return tableName;
        }
    }

    private static final String[] inValTables = {"test_inval_t1"};
    private static final HashMap<String, TableInfo> tableInfoMap
            = new HashMap<>();

    public TestInValuesOperatorCosting() {
        super("setup_testInValuesOperator");
    }

    @BeforeClass
    @Override
    public void beforeClass() throws Exception {
        super.beforeClass();

        // Analyze all tables to make sure they're ready for the tests.
        // Save all of the TableStats to hashmap that will be accessed in tests.
        StorageManager storageManager = server.getStorageManager();
        TableManager tableManager = storageManager.getTableManager();
        for (TestTable tableEnum : TestTable.values()) {
            String table = tableEnum.toString();
            TableInfo tableInfo = tableManager.openTable(table);
            tableManager.analyzeTable(tableInfo);
            tableInfoMap.put(table, tableInfo);
        }
    }

    /**
     * This test checks that at least one value was successfully inserted into
     * each of the test tables.
     *
     * @throws Exception if any query parsing or execution issues occur.
     */
    public void testTablesAreReady() throws Throwable {
        testTableNotEmpty("test_inval_t1");
    }

    public void testUniformLiteralInValues() throws Throwable {
        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T1", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo t1Info = tableInfoMap.get(TestTable.T1.toString());

        Object[] values1 = {1, 5};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values1)),
                        t1Info.getSchema(),
                        t1Info.getTupleFile().getStats().getAllColumnStats());
        assert selectivity == 0.25f;

        Object[] values2 = {5, 6, 7, 8};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values2)),
                        t1Info.getSchema(),
                        t1Info.getTupleFile().getStats().getAllColumnStats());
        assert selectivity == 0f;

        Object[] values3 = {1, 2, 3, 4};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values3)),
                        t1Info.getSchema(),
                        t1Info.getTupleFile().getStats().getAllColumnStats());
        assert selectivity == 1f;

    }

    private ArrayList<Expression> prepareLitExprs(Object[] values) {
        ArrayList<Expression> literalExpressions = new ArrayList<>();
        for (Object val : values) {
            literalExpressions.add(new LiteralValue(val));
        }
        return literalExpressions;
    }

}
