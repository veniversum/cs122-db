package edu.caltech.nanodb.expressions;

import edu.caltech.nanodb.functions.AggregateFunction;
import edu.caltech.nanodb.functions.Function;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleExpressionProcessor implements ExpressionProcessor {
    private final Map<String, FunctionCall> renamedFunctionCallMap;

    public SimpleExpressionProcessor() {
        renamedFunctionCallMap = new HashMap<>();
    }

    @Override
    public void enter(Expression node) {

    }

    @Override
    public Expression leave(Expression node) {
        if (node instanceof FunctionCall) {
            renamedFunctionCallMap.put(node.toString(), (FunctionCall) node);
            final Function f = ((FunctionCall) node).getFunction();
//            if (f instanceof AggregateFunction) {
//                final List<Expression> args = ((FunctionCall) node).getArguments();
//                args.get(0).evaluate()
//            }
            return new ColumnValue(new ColumnName(node.toString()));
        }
        return node;
    }

    public Map<String, FunctionCall> getRenamedFunctionCallMap() {
        return renamedFunctionCallMap;
    }
}
