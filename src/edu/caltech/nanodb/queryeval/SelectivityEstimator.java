package edu.caltech.nanodb.queryeval;


import edu.caltech.nanodb.expressions.*;
import edu.caltech.nanodb.relations.ColumnInfo;
import edu.caltech.nanodb.relations.ColumnType;
import edu.caltech.nanodb.relations.SQLDataType;
import edu.caltech.nanodb.relations.Schema;
import org.apache.log4j.Logger;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * This utility class is used to estimate the selectivity of predicates that
 * appear on Select and Theta-Join plan-nodes.
 */
public class SelectivityEstimator {

    /** A logging object for reporting anything interesting that happens. **/
    private static Logger logger = Logger.getLogger(SelectivityEstimator.class);


    /**
     * This collection specifies the data-types that support comparison
     * selectivity estimates (not including equals or not-equals).  It must be
     * possible to use the {@link #computeRatio} on the data-type so that an
     * estimate can be made about where a value fits within the minimum and
     * maximum values for the column.
     * <p>
     * Note that we can compute selectivity for equals and not-equals simply
     * from the number of distinct values in the column.
     */
    private static HashSet<SQLDataType> SUPPORTED_TYPES_COMPARE_ESTIMATES;


    static {
        // Initialize the set of types that support comparison selectivity
        // estimates.  In time, types like dates, times, NUMERIC, etc. could be
        // added as well.

        SUPPORTED_TYPES_COMPARE_ESTIMATES = new HashSet<SQLDataType>();

        SUPPORTED_TYPES_COMPARE_ESTIMATES.add(SQLDataType.INTEGER);
        SUPPORTED_TYPES_COMPARE_ESTIMATES.add(SQLDataType.BIGINT);
        SUPPORTED_TYPES_COMPARE_ESTIMATES.add(SQLDataType.SMALLINT);
        SUPPORTED_TYPES_COMPARE_ESTIMATES.add(SQLDataType.TINYINT);
        SUPPORTED_TYPES_COMPARE_ESTIMATES.add(SQLDataType.FLOAT);
        SUPPORTED_TYPES_COMPARE_ESTIMATES.add(SQLDataType.DOUBLE);
    }


    /**
     * This constant specifies the default selectivity assumed when a select
     * predicate is too complicated to compute more accurate estimates.  We are
     * assuming that generally people are going to do things that limit the
     * results produced.
     */
    public static final float DEFAULT_SELECTIVITY = 0.25f;


    /** This class should not be instantiated. */
    private SelectivityEstimator() {
        throw new IllegalArgumentException("This class should not be instantiated.");
    }


    /**
     * Returns true if the database supports selectivity estimates for
     * comparisons (other than equals and not-equals) on the specified SQL data
     * type.  SQL types that support these selectivity estimates will include
     * minimum and maximum values in their column-statistics.
     *
     * @param type the SQL data type being considered
     *
     * @return true if the database supports selectivity estimates for the type
     */
    public static boolean typeSupportsCompareEstimates(SQLDataType type) {
        return SUPPORTED_TYPES_COMPARE_ESTIMATES.contains(type);
    }


    /**
     * This function computes the selectivity of a selection predicate, using
     * table statistics and other estimates to make an educated guess.  The
     * result is between 0.0 and 1.0, with 1.0 meaning that all rows will be
     * selected by the predicate.
     *
     * @param expr the expression whose selectivity we are estimating
     *
     * @param exprSchema a schema describing the environment that the expression
     *        will be evaluated within
     *
     * @param stats statistics that may be helpful in estimating the selectivity
     *
     * @return the estimated selectivity as a float
     */
    public static float estimateSelectivity(Expression expr, Schema exprSchema,
                                            ArrayList<ColumnStats> stats) {
        float selectivity = DEFAULT_SELECTIVITY;

        if (expr == null) return 1;

        if (expr instanceof InValuesOperator) {
            InValuesOperator inVal = (InValuesOperator) expr;
            selectivity = estimateInValOperSelectiviy(inVal, exprSchema, stats);
        } else if (expr instanceof BooleanOperator) {
            // A Boolean AND, OR, or NOT operation.
            BooleanOperator bool = (BooleanOperator) expr;
            selectivity = estimateBoolOperSelectivity(bool, exprSchema, stats);
        } else if (expr instanceof CompareOperator) {
            // This is a simple comparison between expressions.
            CompareOperator comp = (CompareOperator) expr;
            selectivity = estimateCompareSelectivity(comp, exprSchema, stats);
        }

        return selectivity;
    }

    /**
     * Estimates selectivity for the `column IN (values)` operator. Where
     * possible, the count of distinct literal values is used to estimate
     * selectivity. For non-literal values, we assume that each such value is
     * unique.
     *
     * @param inVal the IN (values) expression
     *
     * @param exprSchema a schema specifying the environment that the expression
     *        will be evaluated within
     *
     * @param stats a collection of column-statistics to use in making
     *        selectivity estimates
     *
     * @return a selectivity estimate in the range [0, 1].
     */
    public static float estimateInValOperSelectiviy(InValuesOperator inVal,
                                                    Schema exprSchema, ArrayList<ColumnStats> stats) {

        float selectivity = DEFAULT_SELECTIVITY;

        // Check thta the value on the left is a column name
        Expression lhsExpression = inVal.getLeftExpression();
        if (!(lhsExpression instanceof ColumnValue)) return selectivity;
        ColumnValue colVal = (ColumnValue) lhsExpression;

        List<Expression> valueExpressions = inVal.getValues();
        int colIndex = exprSchema.getColumnIndex(colVal.getColumnName());
        ColumnStats colStats = stats.get(colIndex);

        long relevantVals = 0;
        long totalValsInCol = colStats.getNumUniqueValues();
        if (colStats.getNumNullValues() > 0) totalValsInCol += 1;
        boolean containsNull = false;

        List<Object> literalValues = new ArrayList<>();
        for (Expression valueExpr : valueExpressions) {
            if (valueExpr instanceof LiteralValue) {
                Object litVal = valueExpr.evaluate();

                if (litVal == null) {
                    if (!containsNull) {
                        containsNull = true;
                    }
                    continue;
                }

                literalValues.add(litVal);
            } else {
                relevantVals++;
            }
        }

        if (colStats.hasDifferentMinMaxValues()) {
            Comparable max = (Comparable) colStats.getMaxValue();
            Comparable min = (Comparable) colStats.getMinValue();
            relevantVals += literalValues.stream()
                    .filter(val -> max.compareTo(val) > -1
                            && min.compareTo(val) < 1)
                    .count();
        } else {
            relevantVals += literalValues.stream().distinct().count();
        }

        selectivity =
                Math.min(relevantVals, colStats.getNumUniqueValues()) /
                        (float) totalValsInCol;

        return selectivity;
    }

    /**
     * This function computes a selectivity estimate for a general Boolean
     * expression that may be comprised of one or more components.  The method
     * treats components as independent, estimating the selectivity of each one
     * separately, and then combines the results based on whether the Boolean
     * operation is an <tt>AND</tt>, an <tt>OR</tt>, or a <tt>NOT</tt>
     * operation.  As one might expect, this method delegates to
     * {@link #estimateSelectivity} to compute the selectivity of individual
     * terms.
     *
     * @param bool the compound Boolean expression
     *
     * @param exprSchema a schema specifying the environment that the expression
     *        will be evaluated within
     *
     * @param stats a collection of column-statistics to use in making
     *        selectivity estimates
     *
     * @return a selectivity estimate in the range [0, 1].
     */
    public static float estimateBoolOperSelectivity(BooleanOperator bool,
                                                    Schema exprSchema, ArrayList<ColumnStats> stats) {

        float selectivity = 1.0f;

        /* WARNING
        We assume strong (naive) independence conditions between boolean terms.
        Estimates may be wildly off if the terms are strongly correlated.
        */
        switch (bool.getType()) {
            case AND_EXPR:
                selectivity = (float) IntStream.range(0, bool.getNumTerms())
                        .mapToObj(bool::getTerm)
                        .mapToDouble(e -> estimateSelectivity(e, exprSchema, stats))
                        .reduce(1, (a, b) -> a * b);
                break;

            case OR_EXPR:

                List<Expression> terms = new ArrayList<>();
                for (int i = 0; i < bool.getNumTerms(); i++)
                    terms.add(bool.getTerm(i));


                // Check if we can treat this OR as `col IN (values)`
                AbstractMap.SimpleEntry<Expression, ArrayList<Expression>> inValuePair
                        = attemptConvertOrToInValues(terms);

                if (inValuePair != null) {
                    InValuesOperator inValOp = new InValuesOperator
                            (inValuePair.getKey(), inValuePair.getValue());
                    selectivity = estimateInValOperSelectiviy(inValOp,
                            exprSchema, stats);
                } else {
                    selectivity = 1f - (float) terms.stream()
                            .mapToDouble(e -> 1f - estimateSelectivity(e, exprSchema, stats))
                            .reduce(1, (a, b) -> a * b);
                }
                break;

            case NOT_EXPR:
                selectivity = 1f - estimateSelectivity(bool.getTerm(0), exprSchema, stats);
                break;

            default:
                // Shouldn't have any other Boolean expression types.
                assert false : "Unexpected Boolean operator type:  " + bool.getType();
        }

        logger.debug("Estimated selectivity of Boolean operator \"" + bool +
                "\" as " + selectivity);

        // Clip selectivity to [0, 1]
        return Float.min(1, selectivity);
    }


    private static AbstractMap.SimpleEntry<Expression, ArrayList<Expression>>
    attemptConvertOrToInValues(List<Expression> terms) {
        ArrayList<Expression> inValuesExprs = new ArrayList<>();
        String columnName = null;
        Expression colExpr = null;

        for (Expression term : terms) {
            if (!(term instanceof CompareOperator)) return null;
            CompareOperator comp = (CompareOperator) term;
            comp.normalize();

            Expression left = comp.getLeftExpression();
            Expression right = comp.getRightExpression();

            if (comp.getType() != CompareOperator.Type.EQUALS
                    || !(left instanceof ColumnValue)
                    || right instanceof ColumnValue)
                return null;

            String currColumn = ((ColumnValue) left)
                    .getColumnName().getColumnName();

            if (columnName == null) {
                columnName = currColumn;
                colExpr = left;
            } else if (!columnName.equals(currColumn)) return null;

            inValuesExprs.add(right);
        }

        return new AbstractMap.SimpleEntry<>(colExpr, inValuesExprs);
    }


    /**
     * This function computes a selectivity estimate for a general comparison
     * operation.  The method examines the types of the arguments in the
     * comparison and determines if it will be possible to make a reasonable
     * guess as to the comparison's selectivity; if not then a default
     * selectivity estimate is used.
     *
     * @param comp the comparison expression
     *
     * @param exprSchema a schema specifying the environment that the expression
     *        will be evaluated within
     *
     * @param stats a collection of column-statistics to use in making
     *        selectivity estimates
     *
     * @return a selectivity estimate in the range [0, 1].
     */
    public static float estimateCompareSelectivity(CompareOperator comp,
                                                   Schema exprSchema, ArrayList<ColumnStats> stats) {

        float selectivity = DEFAULT_SELECTIVITY;

        // Move the comparison into a normalized order so that it's easier to
        // write the logic for analysis.  Specifically, this will ensure that
        // if we are comparing a column and a value, the column will always be
        // on the left and the value will always be on the right.
        comp.normalize();

        Expression left = comp.getLeftExpression();
        Expression right = comp.getRightExpression();

        // If the comparison is simple enough then compute its selectivity.
        // Otherwise, just use the default selectivity.
        if (left instanceof ColumnValue && right instanceof LiteralValue) {
            // Comparison:  column op value
            selectivity = estimateCompareColumnValue(comp.getType(),
                    (ColumnValue) left, (LiteralValue) right, exprSchema, stats);

            logger.debug("Estimated selectivity of cmp-col-val operator \"" +
                    comp + "\" as " + selectivity);
        } else if (left instanceof ColumnValue && right instanceof ColumnValue) {
            // Comparison:  column op column
            selectivity = estimateCompareColumnColumn(comp.getType(),
                    (ColumnValue) left, (ColumnValue) right, exprSchema, stats);

            logger.debug("Estimated selectivity of cmp-col-col operator \"" +
                    comp + "\" as " + selectivity);
        }

        return selectivity;
    }


    /**
     * This helper function computes a selectivity estimate for a comparison
     * between a column and a literal value.  Note that the comparison is always
     * assumed to have the column-name on the <em>left</em>, and the literal
     * value on the <em>right</em>.  Examples would be <tt>T1.A &gt; 5</tt>, or
     * <tt>T2.C = 15</tt>.
     *
     * @param compType the type of the comparison, e.g. equals, not-equals, or
     *        some inequality comparison
     *
     * @param columnValue the column that is used in the comparison
     * @param literalValue the value that the column is being compared to
     *
     * @param exprSchema a schema specifying the environment that the expression
     *        will be evaluated within
     *
     * @param stats a collection of column-statistics to use in making
     *        selectivity estimates
     *
     * @return a selectivity estimate in the range [0, 1].
     */
    private static float estimateCompareColumnValue(CompareOperator.Type compType,
                                                    ColumnValue columnValue, LiteralValue literalValue,
                                                    Schema exprSchema, ArrayList<ColumnStats> stats) {

        // Comparison:  column op value

        float selectivity = DEFAULT_SELECTIVITY;

        // Pull out the critical values for making the estimates.

        int colIndex = exprSchema.getColumnIndex(columnValue.getColumnName());
        ColumnInfo colInfo = exprSchema.getColumnInfo(colIndex);
        SQLDataType sqlType = colInfo.getType().getBaseType();
        ColumnStats colStats = stats.get(colIndex);

        Object value = literalValue.evaluate();

        switch (compType) {
            case EQUALS:
            case NOT_EQUALS:
                // Compute the equality value.  Then, if inequality, invert the
                // result.

                final int numUniqueValues = colStats.getNumUniqueValues();
                if (numUniqueValues > 0) {
                    selectivity = 1f / numUniqueValues;
                    if (compType == CompareOperator.Type.NOT_EQUALS) {
                        selectivity = 1f - selectivity;
                    }
                } // else unknown, so use default selectivity.

                break;

            case GREATER_OR_EQUAL:
            case LESS_THAN:
                // Compute the greater-or-equal value.  Then, if less-than,
                // invert the result.

                // Only estimate selectivity for this kind of expression if the
                // column's type supports it.

                if (typeSupportsCompareEstimates(sqlType) &&
                        colStats.hasDifferentMinMaxValues()) {

                    selectivity = computeRatio(value, colStats.getMaxValue(), colStats.getMinValue(), colStats.getMaxValue());

                    if (compType == CompareOperator.Type.LESS_THAN) {
                        selectivity = 1f - selectivity;
                    }
                }

                break;

            case LESS_OR_EQUAL:
            case GREATER_THAN:
                // Compute the less-or-equal value.  Then, if greater-than,
                // invert the result.

                // Only estimate selectivity for this kind of expression if the
                // column's type supports it.

                if (typeSupportsCompareEstimates(sqlType) &&
                        colStats.hasDifferentMinMaxValues()) {

                    selectivity = computeRatio(colStats.getMinValue(), value, colStats.getMinValue(), colStats.getMaxValue());

                    if (compType == CompareOperator.Type.GREATER_THAN) {
                        selectivity = 1f - selectivity;
                    }
                }

                break;

            default:
                // Shouldn't be any other comparison types...
                assert false : "Unexpected compare-operator type:  " + compType;
        }

        return selectivity;
    }


    /**
     * This helper function computes a selectivity estimate for a comparison
     * between two columns.  Examples would be <tt>T1.A = T2.A</tt>.
     *
     * @param compType the type of the comparison, e.g. equals, not-equals, or
     *        some inequality comparison
     *
     * @param columnOne the first column that is used in the comparison
     * @param columnTwo the second column that is used in the comparison
     *
     * @param exprSchema a schema specifying the environment that the expression
     *        will be evaluated within
     *
     * @param stats a collection of column-statistics to use in making
     *        selectivity estimates
     *
     * @return a selectivity estimate in the range [0, 1].
     */
    private static float estimateCompareColumnColumn(CompareOperator.Type compType,
                                                     ColumnValue columnOne, ColumnValue columnTwo,
                                                     Schema exprSchema, ArrayList<ColumnStats> stats) {

        // Comparison:  column op column

        float selectivity = DEFAULT_SELECTIVITY;

        // Pull out the critical values for making the estimates.

        int colOneIndex = exprSchema.getColumnIndex(columnOne.getColumnName());
        int colTwoIndex = exprSchema.getColumnIndex(columnTwo.getColumnName());

        // Can't do anything, columns are not recognised
        if (colOneIndex == -1 || colTwoIndex == -1) {
            return DEFAULT_SELECTIVITY;
        }

        ColumnStats colOneStats = stats.get(colOneIndex);
        ColumnStats colTwoStats = stats.get(colTwoIndex);

        final int numUniqueValues1 = colOneStats.getNumUniqueValues();
        final int numUniqueValues2 = colTwoStats.getNumUniqueValues();
        if (numUniqueValues1 > 0 && numUniqueValues2 > 0) {
            switch (compType) {
                case NOT_EQUALS:
                case EQUALS:
                    selectivity = (float) Math.min(numUniqueValues1, numUniqueValues2)
                            / (numUniqueValues1 * numUniqueValues2);
                    if (compType == CompareOperator.Type.NOT_EQUALS) selectivity = 1 - selectivity;
                    break;
                default:
                    break;
            }
        }

        // TODO:  Compute the selectivity.  Note that the ColumnStats type
        //        will return special values to indicate "unknown" stats;
        //        your code should detect when this is the case, and fall
        //        back on the default selectivity.

        return selectivity;
    }


    /**
     * This method computes the function
     * (<em>high</em><sub>1</sub> - <em>low</em><sub>1</sub>) /
     * (<em>high</em><sub>2</sub> - <em>low</em><sub>2</sub>), given
     * <tt>Object</tt>-values that can be coerced into types that can
     * be used for arithmetic.  This operation is useful for estimating the
     * selectivity of comparison operations, if we know the minimum and maximum
     * values for a column.
     * <p>
     * The result of this operation is clamped to the range [0, 1].
     *
     * @param low1 the low value for the numerator
     * @param high1 the high value for the numerator
     * @param low2 the low value for the denominator
     * @param high2 the high value for the denominator
     *
     * @return the ratio of (<em>high</em><sub>1</sub> - <em>low</em><sub>1</sub>) /
     *         (<em>high</em><sub>2</sub> - <em>low</em><sub>2</sub>), clamped
     *         to the range [0, 1].
     */
    private static float computeRatio(Object low1, Object high1,
                                      Object low2, Object high2) {

        Object diff1 = ArithmeticOperator.evalObjects(
                ArithmeticOperator.Type.SUBTRACT, high1, low1);

        Object diff2 = ArithmeticOperator.evalObjects(
                ArithmeticOperator.Type.SUBTRACT, high2, low2);

        Object ratio = ArithmeticOperator.evalObjects(
                ArithmeticOperator.Type.DIVIDE, diff1, diff2);

        float fltRatio = TypeConverter.getFloatValue(ratio);

        logger.debug(String.format("Ratio:  (%s - %s) / (%s - %s) = %.2f",
                high1, low1, high2, low2, fltRatio));

        // Clamp the value to the range [0, 1].
        if (fltRatio < 0.0f)
            fltRatio = 0.0f;
        else if (fltRatio > 1.0f)
            fltRatio = 1.0f;

        return fltRatio;
    }
}

