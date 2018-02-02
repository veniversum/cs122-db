package edu.caltech.test.nanodb.costing;

import edu.caltech.nanodb.expressions.*;
import edu.caltech.nanodb.queryeval.SelectivityEstimator;
import edu.caltech.nanodb.relations.TableInfo;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * This test class exercises the functionality of
 * costing the {@link edu.caltech.nanodb.expressions.InValuesOperator} in the
 * {@link edu.caltech.nanodb.queryeval.SelectivityEstimator} class.
 */
@Test
public class TestInValuesOperatorCosting extends CostingTestCase {

    private static final String[] tableNames = new String[]{
            "test_inval_t1",
            "test_inval_t2",
            "test_inval_t3"
    };

    public TestInValuesOperatorCosting() {
        super("setup_testInValuesOperator", tableNames);
    }

    @Override
    public void testTablesAreReady() throws Throwable {
        super.testTablesAreReady();
    }

    /**
     * Tests that selectivity is calculated correctly when the column which
     * InValuesOperator is used on contains uniformly distributed values
     **/
    public void testInValuesUniformLiteral() {
        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T1", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo tableInfo = tableInfoMap.get(tableNames[0]);

        Object[] values1 = {1, 5};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values1)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.25f);

        Object[] values2 = {5, 6, 7, 8};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values2)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0f);

        Object[] values3 = {1, 2, 3, 4};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values3)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 1f);

    }

    /**
     * Tests that selectivity is calculated correctly when the column which
     * InValuesOperator is used on contains varchar's (without any nulls)
     **/
    public void testInValuesVarcharNoNull() {

        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T2", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo tableInfo = tableInfoMap.get(tableNames[1]);

        Object[] values1 = {'t', 'n'};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values1)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.5f);

        Object[] values2 = {'a'};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values2)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.25f);

        Object[] values3 = {'t', 'n', 'a', 'c', 'd'};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values3)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 1f);

    }

    /**
     * Tests that selectivity is calculated correctly when the column which
     * InValuesOperator is used on contains varchar's (with some nulls)
     **/
    public void testInValuesVarcharWothNull() {

        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T3", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo tableInfo = tableInfoMap.get(tableNames[2]);

        Object[] values1 = {'t', 'n'};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values1)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.5f);

        Object[] values2 = {'a'};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values2)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.25f);

        Object[] values3 = {'t', 'n', 'a', 'c', 'd'};
        selectivity = SelectivityEstimator
                .estimateInValOperSelectiviy(
                        new InValuesOperator(colVal, prepareLitExprs(values3)),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.75f);

    }

    /**
     * Prepares an array list of literal expressions.
     */
    private ArrayList<Expression> prepareLitExprs(Object[] values) {
        ArrayList<Expression> literalExpressions = new ArrayList<>();
        for (Object val : values) {
            literalExpressions.add(new LiteralValue(val));
        }
        return literalExpressions;
    }

}
