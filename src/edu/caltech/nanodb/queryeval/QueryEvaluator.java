package edu.caltech.nanodb.queryeval;


import edu.caltech.nanodb.commands.QueryCommand;
import edu.caltech.nanodb.plannodes.PlanNode;

import edu.caltech.nanodb.relations.Schema;
import edu.caltech.nanodb.relations.Tuple;
import org.apache.log4j.Logger;


public class QueryEvaluator {

    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(QueryEvaluator.class);

    /**
     * Executes the specified query plan, and feeds the results to the specified
     * tuple processor.
     *
     * @param plan the query plan to execute
     *
     * @param processor the tuple-processor to receive the results
     *
     * @return An object containing statistics about the plan evaluation.
     *
     * @throws Exception if an error occurs during query evaluation or during
     *         tuple-processing.
     */
    public static EvalStats executePlan(PlanNode plan, TupleProcessor processor)
        throws Exception {

        logger.debug("Begun executing plan of type " + plan.toString());

        // Execute the plan, and record some basic statistics as we go.

        long startTime = System.nanoTime();

        Schema resultSchema = plan.getSchema();
        processor.setSchema(resultSchema);

        plan.initialize();

        int rowsProduced = 0;
        try {
            Tuple tuple;
            while (true) {
                // Get the next tuple.  If there aren't anymore, we're done!
                tuple = plan.getNextTuple();
                if (tuple == null)
                    break;

                rowsProduced++;

                // Do whatever we're supposed to do with the tuple.
                processor.process(tuple);
            }
        }
        finally {
            plan.cleanUp();
        }

        processor.finish();

        long elapsedTimeNanos = System.nanoTime() - startTime;

        // Return the basic statistics we gathered.
        return new EvalStats(rowsProduced, elapsedTimeNanos);
    }
}

