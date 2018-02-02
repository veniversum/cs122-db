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

    private enum Tables {
        T1("test_inval_t1");

        private final String tableName;

        Tables(String rep) {
            tableName = rep;
        }

        public String toString() {
            return tableName;
        }
    }

    public TestInValuesOperatorCosting() {
        super("setup_testInValuesOperator",
                (String[]) Arrays.stream(Tables.values())
                        .map(Tables::toString)
                        .toArray());
    }

    public void testUniformLiteralInValues() throws Throwable {
        float selectivity;

        ColumnName colName = new ColumnName("TEST_INVAL_T1", "A");
        ColumnValue colVal = new ColumnValue(colName);
        TableInfo t1Info = tableInfoMap.get(Tables.T1.toString());

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
