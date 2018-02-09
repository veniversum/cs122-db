package edu.caltech.nanodb.expressions;

import java.util.Map;
import java.util.Set;

public class SubqueryConjunctsExpressionProcessor implements ExpressionProcessor {
    private Map<String, String> reverseTableNameMap;
    private String tableName;
    private Set<Expression> exprs;

    public SubqueryConjunctsExpressionProcessor(Map<String, String> reverseTableNameMap, String tableName) {
        this.reverseTableNameMap = reverseTableNameMap;
        this.tableName = tableName;
    }

    @Override
    public void enter(Expression node) {

    }

    @Override
    public Expression leave(Expression node) {
        if (node instanceof ColumnValue) {
            String columnName = ((ColumnValue) node).getColumnName().getColumnName();
            String tableName = ((ColumnValue) node).getColumnName().getTableName();
            if (reverseTableNameMap.containsKey(columnName) && this.tableName.equals(tableName)) {
                return new ColumnValue(new ColumnName(reverseTableNameMap.get(columnName), columnName));
            }
        }
        return node;
    }
}
