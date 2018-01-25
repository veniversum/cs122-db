package edu.caltech.nanodb.queryeval;


import edu.caltech.nanodb.expressions.Expression;
import edu.caltech.nanodb.expressions.SimpleExpressionProcessor;
import edu.caltech.nanodb.plannodes.*;
import edu.caltech.nanodb.queryast.FromClause;
import edu.caltech.nanodb.queryast.SelectClause;
import edu.caltech.nanodb.queryast.SelectValue;
import edu.caltech.nanodb.relations.TableInfo;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;


/**
 * This class generates execution plans for very simple SQL
 * <tt>SELECT * FROM tbl [WHERE P]</tt> queries.  The primary responsibility
 * is to generate plans for SQL <tt>SELECT</tt> statements, but
 * <tt>UPDATE</tt> and <tt>DELETE</tt> expressions will also use this class
 * to generate simple plans to identify the tuples to update or delete.
 */
@SuppressWarnings("Duplicates")
public class SimplePlanner extends AbstractPlannerImpl {

    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(SimplePlanner.class);


    /**
     * Returns the root of a plan tree suitable for executing the specified
     * query.
     *
     * @param selClause an object describing the query to be performed
     *
     * @return a plan tree for executing the specified query
     *
     * @throws IOException if an IO error occurs when the planner attempts to
     *         load schema and indexing information.
     */
    @Override
    public PlanNode makePlan(SelectClause selClause, List<SelectClause> enclosingSelects) throws IOException {
        final SimpleExpressionProcessor processor = new SimpleExpressionProcessor();
        final List<SelectValue> selectValues = selClause.getSelectValues();

        /*
        Process expressions in select values and having clause.
        We need to replace aggregate function calls with string identifiers here.
         */
        for (SelectValue sv : selectValues) {
            if (sv.isExpression()) {
                Expression e = sv.getExpression().traverse(processor);
                sv.setExpression(e);
            }
        }
        Expression havingExpr = selClause.getHavingExpr();
        if (havingExpr != null)
            havingExpr = havingExpr.traverse(processor);

        // Todo remove after implementing enclosing selects
        if (enclosingSelects != null && !enclosingSelects.isEmpty()) {
            throw new UnsupportedOperationException(
                "Not implemented:  enclosing queries");
        }

        /*
        Process the from clause, by construction join nodes,
        renaming table, etc.
         */
        final FromClause fromClause = selClause.getFromClause();
        PlanNode node = deconstructFrom(fromClause);

        /*
        Filter on the where clause.
         */
        if (selClause.getWhereExpr() != null)
            node = new SimpleFilterNode(node, selClause.getWhereExpr());

        /*
        Process group by clause and aggregate function calls if we need to.
         */
        final List<Expression> groupByExprs = selClause.getGroupByExprs();
        if (groupByExprs.size() > 0 || !processor.getRenamedFunctionCallMap().isEmpty())
            node = new HashedGroupAggregateNode(node, groupByExprs, processor.getRenamedFunctionCallMap());

        /*
        Filter on the having clause, now that we've evaluated the function calls.
        We can't evaluate the having clause if there are unevaluated functions in it.
         */
        if (havingExpr != null)
            node = new SimpleFilterNode(node, havingExpr);

        /*
        Finally, project the results if we need to.
         */
        if (!selClause.isTrivialProject()) {
            node = new ProjectNode(node, selClause.getSelectValues());
        }

        /*
        Prepare node before returning.
        This will recursively prepare all child nodes in the planning tree,
        and generate the output schema.
         */
        node.prepare();
        return node;
    }

    private PlanNode deconstructFrom(FromClause fromClause) throws IOException {
        if (fromClause == null) return null;
        if (fromClause.isBaseTable()) {
            TableInfo tableInfo = storageManager.getTableManager().openTable(fromClause.getTableName());
            PlanNode node = new FileScanNode(tableInfo, null);
            if (fromClause.isRenamed()) node = new RenameNode(node, fromClause.getResultName());
            return node;
        }
        return new NestedLoopJoinNode(deconstructFrom(fromClause.getLeftChild())
                , deconstructFrom(fromClause.getRightChild())
                , fromClause.getJoinType()
                , fromClause.getOnExpression());
    }
    /**
     * Constructs a simple select plan that reads directly from a table, with
     * an optional predicate for selecting rows.
     * <p>
     * While this method can be used for building up larger <tt>SELECT</tt>
     * queries, the returned plan is also suitable for use in <tt>UPDATE</tt>
     * and <tt>DELETE</tt> command evaluation.  In these cases, the plan must
     * only generate tuples of type {@link edu.caltech.nanodb.storage.PageTuple},
     * so that the command can modify or delete the actual tuple in the file's
     * page data.
     *
     * @param tableName The name of the table that is being selected from.
     *
     * @param predicate An optional selection predicate, or {@code null} if
     *        no filtering is desired.
     *
     * @return A new plan-node for evaluating the select operation.
     *
     * @throws IOException if an error occurs when loading necessary table
     *         information.
     */
    public SelectNode makeSimpleSelect(String tableName, Expression predicate,
        List<SelectClause> enclosingSelects) throws IOException {
        if (tableName == null)
            throw new IllegalArgumentException("tableName cannot be null");

        if (enclosingSelects != null) {
            // If there are enclosing selects, this subquery's predicate may
            // reference an outer query's value, but we don't detect that here.
            // Therefore we will probably fail with an unrecognized column
            // reference.
            logger.warn("Currently we are not clever enough to detect " +
                "correlated subqueries, so expect things are about to break...");
        }

        // Open the table.
        TableInfo tableInfo = storageManager.getTableManager().openTable(tableName);

        // Make a SelectNode to read rows from the table, with the specified
        // predicate.
        SelectNode selectNode = new FileScanNode(tableInfo, predicate);
        selectNode.prepare();
        return selectNode;
    }
}

