package edu.caltech.nanodb.expressions;

import edu.caltech.nanodb.functions.Function;
import edu.caltech.nanodb.functions.SimpleFunction;

import java.util.HashMap;
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
            final Function f = ((FunctionCall) node).getFunction();
            if (!(f instanceof SimpleFunction)) {
                renamedFunctionCallMap.put(node.toString(), (FunctionCall) node);
                return new ColumnValue(new ColumnName(node.toString()));
            }
        }
        return node;
    }

    public Map<String, FunctionCall> getRenamedFunctionCallMap() {
        return renamedFunctionCallMap;
    }
}
