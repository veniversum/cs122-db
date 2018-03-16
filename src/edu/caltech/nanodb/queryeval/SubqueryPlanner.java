package edu.caltech.nanodb.queryeval;

import edu.caltech.nanodb.expressions.Expression;
import edu.caltech.nanodb.expressions.SubqueryExpressionProcessor;
import edu.caltech.nanodb.expressions.SubqueryOperator;
import edu.caltech.nanodb.plannodes.PlanNode;
import edu.caltech.nanodb.queryast.SelectClause;

import java.io.IOException;
import java.util.List;

public class SubqueryPlanner {

    private AbstractPlannerImpl planner;

    private SubqueryExpressionProcessor subExprProc;

    public SubqueryPlanner(AbstractPlannerImpl planner) {
        this.planner = planner;
        this.subExprProc = new SubqueryExpressionProcessor();
    }

    public void planSubqueries(Expression expression,
                               List<SelectClause> enclosingSelects)
            throws IOException {
        expression.traverse(this.subExprProc);
        List<SubqueryOperator> subOps = subExprProc.getSubqueryExpressions();
        for (SubqueryOperator subOp : subOps) {
            PlanNode plan =
                    planner.makePlan(subOp.getSubquery(), enclosingSelects);
            subOp.setSubqueryPlan(plan);
        }
        subExprProc.resetSubqueryExpressions();
    }

    public boolean containsSubqueries(Expression expression) {
        if (expression == null) return false;
        expression.traverse(subExprProc);
        boolean result = !subExprProc.getSubqueryExpressions().isEmpty();
        subExprProc.resetSubqueryExpressions();
        return result;
    }

}
