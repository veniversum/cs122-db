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

public class SubqueryPlanner {

    private AbstractPlannerImpl planner;

    private SubqueryExpressionProcessor subExprProc;

    private List<SelectClause> enclosingSelectsForSubqueries;

    public SubqueryPlanner(AbstractPlannerImpl planner,
                           List<SelectClause> enclosingSelectsForSubqueries) {
        this.planner = planner;
        this.enclosingSelectsForSubqueries = enclosingSelectsForSubqueries;
        this.subExprProc = new SubqueryExpressionProcessor();
    }

    public List<SubqueryOperator> extractSubqueries(Expression expression) {
        expression.traverse(this.subExprProc);
        List<SubqueryOperator> subOps = subExprProc.getSubqueryExpressions();
        subExprProc.resetSubqueryExpressions();
        return subOps;
    }

    public Environment planSubqueries(Expression expression)
            throws IOException {
        Environment environment = new Environment();
        return planSubqueries(expression, environment);
    }

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

    public boolean containsSubqueries(Collection<Expression> expressions) {
        for (Expression expr : expressions) {
            if (containsSubqueries(expr)) return true;
        }
        return false;
    }

    public boolean containsSubqueries(Expression expression) {
        if (expression == null) return false;
        expression.traverse(subExprProc);
        boolean result = !subExprProc.getSubqueryExpressions().isEmpty();
        subExprProc.resetSubqueryExpressions();
        return result;
    }

}
