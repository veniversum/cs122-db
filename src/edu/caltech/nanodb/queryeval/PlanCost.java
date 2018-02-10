package edu.caltech.nanodb.queryeval;


/**
 * This class holds a collection of values that represent the cost of a
 * plan-node (and its subplans).  All of these cost estimates are ultimately
 * based on statistics computed from actual tables, but the estimation process
 * is very approximate.  For more involved plans, the best we can really hope
 * for is that better, faster plans will be assigned lower costs than worse,
 * slower plans.
 *
 * @see TableStats
 */
public class PlanCost {
    public static final float seq_page_cost = 1.0f;
    public static final float random_page_cost = 4.0f;
    public static final float cpu_tuple_cost = 0.01f;
    public static final float cpu_index_tuple_cost = 0.005f; // Unused since we don't have indexes now
    public static final float cpu_operator_cost = 0.0025f;

    /**
     * The estimated number of tuples produced by the node.  We use a
     * floating-point value because the computations frequently involve
     * fractional numbers and it's not very effective to use integers or longs.
     */
    public float numTuples;


    /** The average tuple size of tuples produced by the node. */
    public float tupleSize;


    /**
     * An estimate of the overall computational cost of the plan node, in some
     * imaginary unit.  Each plan node must perform some amount of computation
     * to produce its results, and this cost is clearly accumulated as results
     * flow up the plan tree.
     */
    public float cpuCost;


    /**
     * The estimated number of disk-block accesses required to execute the node.
     */
    public long numBlockIOs;

    public float ioCost;


    /**
     * Constructs a PlanCost object from its component fields.
     *
     * @param numTuples the estimated number of tuples that will be produced
     *
     * @param tupleSize the estimated size of the produced tuples in bytes
     *
     * @param cpuCost an estimate of the overall computational cost of the plan
     *        node, in some imaginary unit
     *
     * @param numBlockIOs the estimated number of block reads and writes that
     *        will be performed in evaluating the query
     */
    public PlanCost(float numTuples, float tupleSize,
                    float cpuCost, long numBlockIOs, float ioCost) {

        this.numTuples = numTuples;
        this.tupleSize = tupleSize;
        this.cpuCost = cpuCost;
        this.numBlockIOs = numBlockIOs;
        this.ioCost = ioCost;
    }


    /**
     * Constructs a PlanCost object from another cost object.
     *
     * @param c the cost-object to duplicate
     */
    public PlanCost(PlanCost c) {
        this(c.numTuples, c.tupleSize, c.cpuCost, c.numBlockIOs, c.ioCost);
    }


    @Override
    public String toString() {
        return String.format("[tuples=%.1f, tupSize=%.1f, cpuCost=%.1f, blockIOs=%d, ioCost=%.1f]",
            numTuples, tupleSize, cpuCost, numBlockIOs, ioCost);
    }
}
