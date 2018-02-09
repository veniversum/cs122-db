package edu.caltech.nanodb.plannodes;


import java.io.IOException;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.caltech.nanodb.expressions.Expression;
import edu.caltech.nanodb.expressions.FunctionCall;
import edu.caltech.nanodb.expressions.OrderByExpression;
import edu.caltech.nanodb.expressions.TupleLiteral;

import edu.caltech.nanodb.queryeval.PlanCost;
import edu.caltech.nanodb.relations.Tuple;


/**
 * Implements grouping and aggregation by using hashing as a method to
 * identify groups.
 */
public class HashedGroupAggregateNode extends GroupAggregateNode {

    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(HashedGroupAggregateNode.class);


    private LinkedHashMap<TupleLiteral, Map<String, FunctionCall>> computedAggregates;

    private Iterator<TupleLiteral> groupIterator;


    private boolean done;


    public HashedGroupAggregateNode(PlanNode subplan,
        List<Expression> groupByExprs, Map<String, FunctionCall> aggregates) {
        super(subplan, groupByExprs, aggregates);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HashedGroupAggregateNode) {
            HashedGroupAggregateNode other = (HashedGroupAggregateNode) obj;

            return groupByExprs.equals(other.groupByExprs) &&
                   aggregates.equals(other.aggregates) &&
                   leftChild.equals(other.leftChild);
        }
        return false;
    }


    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + groupByExprs.hashCode();
        hash = 31 * hash + aggregates.hashCode();
        hash = 31 * hash + leftChild.hashCode();
        return hash;
    }


    // Use parent javadocs.
    @Override
    public String toString() {
        return "HashedGroupAggregate[groupBy=" + groupByExprs +
            ", aggregates=" + aggregates + "]";
    }


    /**
     * The hashed grouping/aggregate operation does not order its results in
     * any way.
     */
    public List<OrderByExpression> resultsOrderedBy() {
        return null;
    }


    /**
     * The hashed grouping/aggregate operation does not support marking, since
     * it must fully consume its input before it can produce any results.
     */
    public boolean supportsMarking() {
        return false;
    }


    /**
     * The hashed grouping/aggregate operation does not require marking.
     */
    public boolean requiresLeftMarking() {
        return false;
    }


    /**
     * The hashed grouping/aggregate operation does not require marking.
     */
    public boolean requiresRightMarking() {
        return false;
    }


    /**
     * Gets the next tuple that fulfills the conditions for this plan node.
     * If the node has a child, it should call getNextTuple() on the child.
     * If the node is a leaf, the tuple comes from some external source such
     * as a table file, the network, etc.
     *
     * @return the next tuple to be generated by this plan, or <tt>null</tt>
     *         if the plan has finished generating plan nodes.
     *
     * @throws java.io.IOException if table data cannot be read from the filesystem
     * @throws IllegalStateException if a plan node is not properly initialized
     */
    public Tuple getNextTuple() throws IllegalStateException, IOException {
        if (done)
            return null;

        TupleLiteral result = null;

        if (computedAggregates == null) {
            computeAggregates();
            groupIterator = computedAggregates.keySet().iterator();
        }

        if (groupIterator.hasNext()) {
            // Construct the result tuple from the group, and from the
            // computed aggregate values.

            TupleLiteral group = groupIterator.next();
            Map<String, FunctionCall> groupAggregates = computedAggregates.get(group);
            result = generateOutputTuple(group, groupAggregates);
        }
        else {
            // No more groups.
            done = true;
        }

        return result;
    }


    /**
     * This helper function iterates through <u>all</u> tuples generated by
     * the subplan, using an internal hash table to compute the grouping and
     * aggregate results that this plan-node will output.
     *
     * @throws IOException
     */
    private void computeAggregates() throws IOException {
        Tuple inputTuple;

        computedAggregates = new LinkedHashMap<>();

        // Pull tuples from the left child until we run out.
        while ((inputTuple = leftChild.getNextTuple()) != null) {
            environment.clear();
            environment.addTuple(inputSchema, inputTuple);

            // Get the group values for the current row.
            TupleLiteral groupValues = evaluateGroupByExprs();

            // logger.debug("Group values = " + groupValues);

            // Look up the collection of aggregate functions for this group,
            // or create one if it doesn't already exist.
            Map<String, FunctionCall> groupAggregates = computedAggregates.get(groupValues);
            if (groupAggregates == null) {
                groupAggregates = new LinkedHashMap<String, FunctionCall>();

                // logger.debug(" * Creating new computed aggregates for this group");

                // Clone each aggregate function, since aggregates keep some
                // internal scratch space for computation.
                for (String name : aggregates.keySet()) {
                    FunctionCall fnCall = aggregates.get(name);
                    groupAggregates.put(name, (FunctionCall) fnCall.duplicate());
                }

                computedAggregates.put(groupValues, groupAggregates);
            }

            // Now that we know the group, and we have aggregate functions to
            // do the computation, update each aggregate with the tuple's
            // current value.
            updateAggregates(groupAggregates);

            // Now that we are done with the current tuple, unpin it.
            inputTuple.unpin();
        }
    }


    public void prepare() {
        // Need to prepare the left child-node before we can do our own work.
        leftChild.prepare();

        // Use the helper function to prepare the schema of this grouping/aggregate
        // plan-node, since it is a complicated operation.
        prepareSchemaStats();

        // Grab the left child's cost, then update the cost based on the cost
        // of hashing and computing aggregates.
        PlanCost childCost = leftChild.getCost();
        if (childCost != null) {
            cost = new PlanCost(childCost);

            // Hashing is a constant-time operation per computation.
            cost.cpuCost += cost.numTuples * PlanCost.cpu_tuple_cost;

            // The actual number of tuples generated by this plan-node is equal
            // to the number of groups we have, so just use the estimate we
            // computed earlier.
            cost.numTuples = estimatedNumTuples;

            // Assume that computing each aggregate value costs one unit.
            cost.cpuCost += estimatedNumTuples * aggregates.size() * PlanCost.cpu_operator_cost;
        }
        else {
            logger.info(
                "Child's cost not available; not computing this node's cost.");
        }

        // TODO:  Estimate the final tuple-size.  It isn't hard, just tedious.
    }


    /**
     * Does any initialization the node might need.  This could include
     * resetting state variables or starting the node over from the beginning.
     *
     */
    public void initialize() {
        super.initialize();

        // Clear our state.
        computedAggregates = null;
        groupIterator = null;
        done = false;

        leftChild.initialize();
    }


    /**
     * The hashed grouping/aggregate plan node doesn't support marking.
     *
     * @throws UnsupportedOperationException always.
     */
    public void markCurrentPosition() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "Hashed grouping/aggregate node doesn't support marking");
    }


    /**
     * The hashed grouping/aggregate plan node doesn't support marking.
     *
     * @throws UnsupportedOperationException always.
     */
    public void resetToLastMark() {
        throw new UnsupportedOperationException(
            "Hashed grouping/aggregate node doesn't support marking");
    }


    /**
     * Perform any necessary clean up tasks. This should probably be called
     * when we are done with this plan node.
     */
    public void cleanUp() {
        // Clear our state.
        computedAggregates = null;
        groupIterator = null;
        leftChild.cleanUp();
    }
}
