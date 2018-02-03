package edu.caltech.test.nanodb.costing;

import edu.caltech.nanodb.expressions.*;
import edu.caltech.nanodb.queryeval.ColumnStats;
import edu.caltech.nanodb.queryeval.SelectivityEstimator;
import edu.caltech.nanodb.queryeval.TableStats;
import edu.caltech.nanodb.relations.TableInfo;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * This test checks that analyze() logic of the current TupleFile
 * implementation.
 */
@Test
public class TestTableAnalysis extends CostingTestCase {

    private static final String[] tableNames = new String[]{
            "test_inval_t1",
            "test_inval_t2",
            "test_inval_t3",
            "test_analyze_t4"
    };

    public TestTableAnalysis() {
        super("setup_testCostingOperator", tableNames);
    }

    @Override
    public void testTablesAreReady() throws Throwable {
        super.testTablesAreReady();
    }

    /**
     * This test checks if the number of data pages, tuples and columns is
     * calculated correctly during analysis.
     */
    public void testAnalyzeMiscValues() {

        TableStats t1Stats = tableInfoMap.get(tableNames[0]).getStats();
        assert t1Stats.numTuples == 4;
        assert t1Stats.numDataPages == 1;
        assert t1Stats.getAllColumnStats().size() == 1;

        TableStats t2Stats = tableInfoMap.get(tableNames[1]).getStats();
        assert t2Stats.numTuples == 4;
        assert t2Stats.numDataPages == 1;
        assert t2Stats.getAllColumnStats().size() == 1;

        TableStats t4Stats = tableInfoMap.get(tableNames[3]).getStats();
        assert t4Stats.numTuples == 12;
        assert t4Stats.numDataPages == 1;
        assert t4Stats.getAllColumnStats().size() == 2;

    }

    /**
     * Checks that min and max values for relevant columns were calcualted
     * correctly
     */
    public void testAnalyzeMinMaxValues() {

        TableStats t1Stats = tableInfoMap.get(tableNames[0]).getStats();
        ColumnStats t1Column = t1Stats.getAllColumnStats().get(0);
        assert t1Column.hasDifferentMinMaxValues();
        assert (int) t1Column.getMinValue() == 1;
        assert (int) t1Column.getMaxValue() == 4;

        TableStats t2Stats = tableInfoMap.get(tableNames[1]).getStats();
        ColumnStats t2Column = t2Stats.getAllColumnStats().get(0);
        assert !t2Column.hasMinMaxValues();
        assert t2Column.getMinValue() == null;
        assert t2Column.getMaxValue() == null;

        TableStats t3Stats = tableInfoMap.get(tableNames[2]).getStats();
        ColumnStats t3Column = t3Stats.getAllColumnStats().get(0);
        assert !t3Column.hasMinMaxValues();
        assert t3Column.getMinValue() == null;
        assert t3Column.getMaxValue() == null;

        TableStats t4Stats = tableInfoMap.get(tableNames[3]).getStats();
        ColumnStats t4Column1 = t4Stats.getAllColumnStats().get(0);
        ColumnStats t4Column2 = t4Stats.getAllColumnStats().get(1);
        assert !t4Column1.hasMinMaxValues();
        assert t4Column1.getMinValue() == null;
        assert t4Column1.getMaxValue() == null;
        assert t4Column2.hasMinMaxValues();
        assert (int) t4Column2.getMinValue() == 1;
        assert (int) t4Column2.getMaxValue() == 12;

    }

    /**
     * This test checks number of unique/null values in a table is calculated
     * correctly during analysis.
     */
    public void testAnalyzeColumnValCount() {

        TableStats t2Stats = tableInfoMap.get(tableNames[1]).getStats();
        ColumnStats t2Column = t2Stats.getAllColumnStats().get(0);
        assert t2Column.getNumUniqueValues() == 4;
        assert t2Column.getNumNullValues() == 0;

        TableStats t3Stats = tableInfoMap.get(tableNames[2]).getStats();
        ColumnStats t3Column = t3Stats.getAllColumnStats().get(0);
        assert t3Column.getNumUniqueValues() == 3;
        assert t3Column.getNumNullValues() == 1;

        TableStats t4Stats = tableInfoMap.get(tableNames[3]).getStats();
        ColumnStats t4Column = t4Stats.getAllColumnStats().get(0);
        assert t4Column.getNumUniqueValues() == 4;
        assert t4Column.getNumNullValues() == 3;

    }

}
