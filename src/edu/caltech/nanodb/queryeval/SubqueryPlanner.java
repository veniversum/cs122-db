package edu.caltech.nanodb.queryeval;

import edu.caltech.nanodb.expressions.Environment;
import edu.caltech.nanodb.expressions.Expression;
import edu.caltech.nanodb.expressions.SubqueryExpressionProcessor;
import edu.caltech.nanodb.expressions.SubqueryOperator;
import edu.caltech.nanodb.plannodes.PlanNode;
import edu.caltech.nanodb.queryast.SelectClause;
import org.antlr.stringtemplate.language.Expr;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class SubqueryPlanner {

    /**
     * A reference to a parent planner implementation used to generate subquery
     * plans.
     */
    private AbstractPlannerImpl planner;

    /**
     * An instance of a subquery expression processor used to discover
     * subqueries in expressions.
     */
    private SubqueryExpressionProcessor subExprProc;

    /**
     * A collection of enclosing selects from the query that own this
     * SubqueryPlanner.
     */
    private List<SelectClause> enclosingSelectsForSubqueries;

    /**
     *
     * @param planner
     * @param enclosingSelectsForSubqueries
     */
    public SubqueryPlanner(AbstractPlannerImpl planner,
                           List<SelectClause> enclosingSelectsForSubqueries) {
        this.planner = planner;
        this.enclosingSelectsForSubqueries = enclosingSelectsForSubqueries;
        this.subExprProc = new SubqueryExpressionProcessor();
    }

    /**
     * Extracts all subqueries from the supplied Expression and returns them
     * as a list.
     *
     * @param expression Expression that subqueries will be extracted from
     * @return List subqueries extracted from the specified expression
     */
    public List<SubqueryOperator> extractSubqueries(Expression expression) {
        expression.traverse(this.subExprProc);
        List<SubqueryOperator> subOps = subExprProc.getSubqueryExpressions();
        subExprProc.resetSubqueryExpressions();
        return subOps;
    }

    /**
     * Traverses the supplied Expression instance to identify subqueries. A
     * plan is generated for each of these subqueries. This method also
     * generates a new Environment instance, which is assigned as the parent
     * environment to every plan node in identified subqueries.
     *
     * @param expression Expression that will be traversed to identify
     *                   subqueries.
     * @return Returns Environment instance that was assigned to generated
     * plan nodes if there was at least one subquery found, or null otherwise
     * @throws IOException
     */
    public Environment planSubqueries(Expression expression)
            throws IOException {
        Environment environment = new Environment();
        return planSubqueries(expression, environment);
    }

    /**
     * Traverses the supplied Expression instance to identify subqueries. A
     * plan is generated for each of these subqueries. This method use the
     * supplied Environment instance, which is assigned as the parent
     * environment to every plan node in identified subqueries.
     *
     * @param expression Expression that will be traversed to identify
     *                   subqueries.
     * @param environment Environment instance that will be assigned to found
     *                   subqueries
     *
     * @return Returns Environment instance that was assigned to generated
     * plan nodes if there was at least one subquery found, or null otherwise
     * @throws IOException
     */
    public Environment planSubqueries(Expression expression,
                                      Environment environment)
            throws IOException {
        List<SubqueryOperator> subOps = extractSubqueries(expression);
        for (SubqueryOperator subOp : subOps) {
            PlanNode plan = planner.makePlan(subOp.getSubquery(),
                    enclosingSelectsForSubqueries);
            plan.addParentEnvironmentToPlanTree(environment);
            subOp.setSubqueryPlan(plan);
        }
        if (subOps.isEmpty()) return null;
        else return environment;
    }

    /**
     * Checks if the supplied collection of expressions contains at least one
     * subquery in any of the expressions.
     * @param expressions
     * @return
     */
    public boolean containsSubqueries(Collection<Expression> expressions) {
        for (Expression expr : expressions) {
            if (containsSubqueries(expr)) return true;
        }
        return false;
    }

    /**
     * Checks if the supplied expression contains any subqueries.
     * @param expression
     * @return
     */
    public boolean containsSubqueries(Expression expression) {
        if (expression == null) return false;
        expression.traverse(subExprProc);
        boolean result = !subExprProc.getSubqueryExpressions().isEmpty();
        subExprProc.resetSubqueryExpressions();
        return result;
    }

}
