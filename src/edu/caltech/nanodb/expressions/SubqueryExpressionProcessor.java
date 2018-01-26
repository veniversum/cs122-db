package edu.caltech.nanodb.expressions;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SubqueryExpressionProcessor implements ExpressionProcessor {

    /**
     * A logging object for reporting anything interesting that happens.
     */
    private static Logger logger = Logger.getLogger(SubqueryExpressionProcessor.class);

    private List<SubqueryOperator> subqueryExpressions;

    public SubqueryExpressionProcessor() {
        this.subqueryExpressions = new ArrayList<>();
    }

    @Override
    public void enter(Expression node) {

    }

    @Override
    public Expression leave(Expression node) {
        if (node instanceof SubqueryOperator) {
            subqueryExpressions.add((SubqueryOperator) node);
        }
        return node;
    }

    public List<SubqueryOperator> getSubqueryExpressions() {
        return subqueryExpressions;
    }
}
