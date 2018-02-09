package edu.caltech.nanodb.plannodes;

import edu.caltech.nanodb.expressions.OrderByExpression;
import edu.caltech.nanodb.queryeval.ColumnStats;
import edu.caltech.nanodb.queryeval.PlanCost;
import edu.caltech.nanodb.relations.Tuple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static edu.caltech.nanodb.plannodes.PlanNode.OperationType.LIMIT_OFFSET;

public class LimitOffsetNode extends PlanNode {
    private final int limit;
    private final int offset;
    private int cursor;
    private int mark;

    public LimitOffsetNode(PlanNode leftChild, int limit, int offset) {
        super(LIMIT_OFFSET, leftChild);
        if (limit < 0) throw new IllegalArgumentException("Limit must be positive, got " + limit);
        if (offset < 0) throw new IllegalArgumentException("Offset must be positive, got " + offset);
        this.limit = limit;
        this.offset = offset;
        this.cursor = 0;
        // TODO: Check that results of leftChild must be ordered?
    }

    @Override
    public List<OrderByExpression> resultsOrderedBy() {
        return leftChild.resultsOrderedBy();
    }

    @Override
    public boolean supportsMarking() {
        return leftChild.supportsMarking();
    }

    @Override
    public boolean requiresLeftMarking() {
        return false;
    }

    @Override
    public boolean requiresRightMarking() {
        return false;
    }

    @Override
    public void prepare() {
        // Need to prepare the left child-node before we can do our own work.
        leftChild.prepare();

        // Grab the schema and stats from the left child.
        schema = leftChild.getSchema();
        ArrayList<ColumnStats> childStats = leftChild.getStats();

        // TODO:  Compute the cost of the plan node!
        cost = new PlanCost(leftChild.cost);

        cost.cpuCost += (offset + limit) * PlanCost.cpu_tuple_cost;

        stats = childStats;
    }

    @Override
    public Tuple getNextTuple() throws IllegalStateException, IOException {
        while (cursor < offset) {
            leftChild.getNextTuple();
            cursor++;
        }
        cursor++;
        if (cursor - offset <= limit) {
            return leftChild.getNextTuple();
        }
        return null;
    }

    @Override
    public void markCurrentPosition() {
        leftChild.markCurrentPosition();
        mark = cursor;
    }

    @Override
    public void resetToLastMark() {
        cursor = mark;
    }

    @Override
    public void cleanUp() {
        leftChild.cleanUp();
    }

    @Override
    public String toString() {
        return "LimitOffset[limit:  " + limit + "     offset:  " + offset + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LimitOffsetNode) {
            LimitOffsetNode other = (LimitOffsetNode) obj;
            return leftChild.equals(other.leftChild) &&
                    limit == other.limit &&
                    offset == other.offset;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + limit;
        hash = 31 * hash + offset;
        hash = 31 * hash + leftChild.hashCode();
        return hash;
    }
}
