package edu.caltech.test.nanodb.costing;

import edu.caltech.nanodb.expressions.*;
import edu.caltech.nanodb.queryeval.SelectivityEstimator;
import edu.caltech.nanodb.relations.TableInfo;
import javafx.beans.binding.BooleanExpression;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * This test class exercises the functionality of
 * costing the {@link BooleanOperator} selectivity in cases where it can be
 * replaced with {@link InValuesOperator}.
 */
@Test
public class TestOrBooleanSameColSelectivity extends CostingTestCase {

    private static final String[] tableNames = new String[]{
            "test_inval_t1",
            "test_inval_t2",
            "test_inval_t3"
    };

    public TestOrBooleanSameColSelectivity() {
        super("setup_testCostingOperator", tableNames);
    }

    @Override
    public void testTablesAreReady() throws Throwable {
        super.testTablesAreReady();
    }

    /**
     * Tests that selectivity is calculated correctly when the column which
     * InValuesOperator is used on contains uniformly distributed values
     **/
    public void testOrSameColumnUniformLiteral() {
        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T1", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo tableInfo = tableInfoMap.get(tableNames[0]);

        Object[] values1 = {1, 5};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values1),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.25f);

        Object[] values2 = {5, 6, 7, 8};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values2),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0f);

        Object[] values3 = {1, 2, 3, 4};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values3),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 1f);

    }

    /**
     * Tests that selectivity is calculated correctly when the column which
     * InValuesOperator is used on contains varchar's (without any nulls)
     **/
    public void testOrSameColumnVarcharNoNull() {

        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T2", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo tableInfo = tableInfoMap.get(tableNames[1]);

        Object[] values1 = {'t', 'n'};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values1),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.5f);

        Object[] values2 = {'a'};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values2),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.25f);

        Object[] values3 = {'t', 'n', 'a', 'c', 'd'};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values3),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 1f);

    }

    /**
     * Tests that selectivity is calculated correctly when the column which
     * InValuesOperator is used on contains varchar's (with some nulls)
     **/
    public void testOrSameColumnVarcharWithNull() {

        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T3", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo tableInfo = tableInfoMap.get(tableNames[2]);

        Object[] values1 = {'t', 'n'};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values1),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.5f);

        Object[] values2 = {'a'};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values2),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.25f);

        Object[] values3 = {'t', 'n', 'a', 'c', 'd'};
        selectivity = SelectivityEstimator
                .estimateBoolOperSelectivity(
                        constructBooleanOperator(colVal, values3),
                        tableInfo.getSchema(),
                        tableInfo.getTupleFile().getStats().getAllColumnStats());
        assert checkSelectivity(selectivity, 0.75f);

    }

    /**
     * Prepares an array list of literal expressions.
     */
    private BooleanOperator constructBooleanOperator(ColumnValue colVal,
                                                     Object[] values) {
        final ArrayList<Expression> terms = new ArrayList<>();
        for (Object val : values) {
            CompareOperator compOp = new CompareOperator(
                    CompareOperator.Type.EQUALS,
                    colVal, new LiteralValue(val));
            terms.add(compOp);
        }
        return new BooleanOperator(BooleanOperator.Type.OR_EXPR, terms);
    }

}
