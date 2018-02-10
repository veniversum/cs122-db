package edu.caltech.nanodb.plannodes;


import edu.caltech.nanodb.expressions.Expression;
import edu.caltech.nanodb.expressions.OrderByExpression;
import edu.caltech.nanodb.queryeval.PlanCost;
import edu.caltech.nanodb.queryeval.SelectivityEstimator;
import edu.caltech.nanodb.relations.JoinType;
import edu.caltech.nanodb.relations.Tuple;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;


/**
 * This plan node implements a nested-loop join operation, which can support
 * arbitrary join conditions but is also the slowest join implementation.
 */
public class NestedLoopJoinNode extends ThetaJoinNode {
    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(NestedLoopJoinNode.class);


    /** Most recently retrieved tuple of the left relation. */
    private Tuple leftTuple;

    /** Most recently retrieved tuple of the right relation. */
    private Tuple rightTuple;

    private boolean joined;


    /** Set to true when we have exhausted all tuples from our subplans. */
    private boolean done;


    public NestedLoopJoinNode(PlanNode leftChild, PlanNode rightChild,
                JoinType joinType, Expression predicate) {

        super(leftChild, rightChild, joinType, predicate);
    }


    /**
     * Checks if the argument is a plan node tree with the same structure, but not
     * necessarily the same references.
     *
     * @param obj the object to which we are comparing
     */
    @Override
    public boolean equals(Object obj) {

        if (obj instanceof NestedLoopJoinNode) {
            NestedLoopJoinNode other = (NestedLoopJoinNode) obj;

            return predicate.equals(other.predicate) &&
                leftChild.equals(other.leftChild) &&
                rightChild.equals(other.rightChild);
        }

        return false;
    }


    /** Computes the hash-code of the nested-loop plan node. */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + (predicate != null ? predicate.hashCode() : 0);
        hash = 31 * hash + leftChild.hashCode();
        hash = 31 * hash + rightChild.hashCode();
        return hash;
    }


    /**
     * Returns a string representing this nested-loop join's vital information.
     *
     * @return a string representing this plan-node.
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("NestedLoop[");

        buf.append("join type: ").append(joinType).append(", ");

        if (predicate != null)
            buf.append("pred:  ").append(predicate);
        else
            buf.append("no pred");

        if (schemaSwapped)
            buf.append(" (schema swapped)");

        buf.append(']');

        return buf.toString();
    }


    /**
     * Creates a copy of this plan node and its subtrees.
     */
    @Override
    protected PlanNode clone() throws CloneNotSupportedException {
        NestedLoopJoinNode node = (NestedLoopJoinNode) super.clone();

        // Clone the predicate.
        if (predicate != null)
            node.predicate = predicate.duplicate();
        else
            node.predicate = null;

        return node;
    }


    /**
     * Nested-loop joins can conceivably produce sorted results in situations
     * where the outer relation is ordered, but we will keep it simple and just
     * report that the results are not ordered.
     */
    @Override
    public List<OrderByExpression> resultsOrderedBy() {
        return null;
    }


    /** True if the node supports position marking. **/
    public boolean supportsMarking() {
        return leftChild.supportsMarking() && rightChild.supportsMarking();
    }


    /** True if the node requires that its left child supports marking. */
    public boolean requiresLeftMarking() {
        return false;
    }


    /** True if the node requires that its right child supports marking. */
    public boolean requiresRightMarking() {
        return false;
    }


    @Override
    public void prepare() {
        super.prepare();
        // Simple trick to use left outer join technique for right outer joins,
        // by just switching the left and right subplans.
        if (joinType == JoinType.RIGHT_OUTER) {
            schemaSwapped = true;
            joinType = JoinType.LEFT_OUTER;
            PlanNode tmp = leftChild;
            leftChild = rightChild;
            rightChild = tmp;
        }
        // Need to prepare the left and right child-nodes before we can do
        // our own work.
        leftChild.prepare();
        rightChild.prepare();

        // Use the parent class' helper-function to prepare the schema.
        prepareSchemaStats();

        PlanCost leftChildCost = leftChild.getCost();
        PlanCost rightChildCost = rightChild.getCost();
        if (leftChildCost != null && rightChildCost != null) {
            // O(NM) ops inside loop
            final float crossProductSize = leftChildCost.numTuples * rightChildCost.numTuples;
            final float selectivity = SelectivityEstimator.estimateSelectivity(predicate, schema, stats);

            // TODO support tuple size for joins which return less columns than the sum of the 2 tables.
            cost = new PlanCost(0,
                    leftChildCost.tupleSize + rightChildCost.tupleSize,
                    leftChildCost.cpuCost + leftChildCost.numTuples * rightChildCost.cpuCost,
                    (long) (leftChildCost.numBlockIOs + leftChildCost.numTuples * rightChildCost.numBlockIOs),
                    leftChildCost.numBlockIOs * PlanCost.random_page_cost +
                            leftChildCost.numTuples * rightChildCost.numBlockIOs * PlanCost.seq_page_cost +
                            PlanCost.random_page_cost - PlanCost.seq_page_cost);

            // Comparison of tuples requires CPU time.
            cost.cpuCost += crossProductSize * PlanCost.cpu_operator_cost;
            int numTuplesCreated = 0;
            switch (joinType) {
                case LEFT_OUTER:
                    numTuplesCreated += (1f - selectivity) * leftChildCost.numTuples;
                case CROSS:
                case INNER:
                    numTuplesCreated += selectivity * crossProductSize;
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported join type " + joinType);
            }

            // Creation of joined tuple requires CPU time.
            cost.cpuCost += numTuplesCreated * PlanCost.cpu_tuple_cost;
            cost.numTuples += numTuplesCreated;
        } else {
            logger.info(
                    "Child's cost not available; not computing this node's cost.");
        }
    }


    public void initialize() {
        super.initialize();

        done = false;
        leftTuple = null;
        rightTuple = null;
    }


    /**
     * Returns the next joined tuple that satisfies the join condition.
     *
     * @return the next joined tuple that satisfies the join condition.
     *
     * @throws IOException if a db file failed to open at some point
     */
    public Tuple getNextTuple() throws IOException {
        if (done) return null;

        while (getTuplesToJoin()) {
            if (canJoinTuples()) {
                joined = true;
                return joinTuples(leftTuple, rightTuple);
            }
        }

        return null;
    }


    /**
     * This helper function implements the logic that sets {@link #leftTuple}
     * and {@link #rightTuple} based on the nested-loop logic.
     *
     * @return {@code true} if another pair of tuples was found to join, or
     *         {@code false} if no more pairs of tuples are available to join.
     */
    private boolean getTuplesToJoin() throws IOException {
        if (leftTuple == null) leftTuple = leftChild.getNextTuple();
        rightTuple = rightChild.getNextTuple();
        if (rightTuple == null) {
            if (joinType != JoinType.LEFT_OUTER || joined) {
                leftTuple = leftChild.getNextTuple();
                rightChild.initialize();
                rightTuple = rightChild.getNextTuple();
                joined = false;
            }
        }
        done = leftTuple == null;
        return !done;
    }


    private boolean canJoinTuples() {
        // If the predicate was not set, we can always join them!
        if (predicate == null)
            return true;
        if (rightTuple == null && !joined)
            return joinType == JoinType.LEFT_OUTER;

        environment.clear();
        environment.addTuple(leftSchema, leftTuple);
        environment.addTuple(rightSchema, rightTuple);

        return predicate.evaluatePredicate(environment);
    }


    public void markCurrentPosition() {
        leftChild.markCurrentPosition();
        rightChild.markCurrentPosition();
    }


    public void resetToLastMark() throws IllegalStateException {
        leftChild.resetToLastMark();
        rightChild.resetToLastMark();

        // TODO:  Prepare to reevaluate the join operation for the tuples.
        //        (Just haven't gotten around to implementing this.)
    }


    public void cleanUp() {
        leftChild.cleanUp();
        rightChild.cleanUp();
    }
}
