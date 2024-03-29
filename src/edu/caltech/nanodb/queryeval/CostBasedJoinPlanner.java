package edu.caltech.nanodb.queryeval;


import edu.caltech.nanodb.expressions.*;
import edu.caltech.nanodb.plannodes.*;
import edu.caltech.nanodb.queryast.FromClause;
import edu.caltech.nanodb.queryast.SelectClause;
import edu.caltech.nanodb.queryast.SelectValue;
import edu.caltech.nanodb.relations.JoinType;
import edu.caltech.nanodb.relations.Schema;
import edu.caltech.nanodb.relations.TableInfo;
import javafx.util.Pair;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * This planner implementation uses dynamic programming to devise an optimal
 * join strategy for the query.  As always, queries are optimized in units of
 * <tt>SELECT</tt>-<tt>FROM</tt>-<tt>WHERE</tt> subqueries; optimizations
 * don't currently span multiple subqueries.
 */
public class CostBasedJoinPlanner extends AbstractPlannerImpl {

    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(CostBasedJoinPlanner.class);


    /**
     * This helper class is used to keep track of one "join component" in the
     * dynamic programming algorithm.  A join component is simply a query plan
     * for joining one or more leaves of the query.
     * <p>
     * In this context, a "leaf" may either be a base table or a subquery in
     * the <tt>FROM</tt>-clause of the query.  However, the planner will
     * attempt to push conjuncts down the plan as far as possible, so even if
     * a leaf is a base table, the plan may be a bit more complex than just a
     * single file-scan.
     */
    private static class JoinComponent {
        /**
         * This is the join plan itself, that joins together all leaves
         * specified in the {@link #leavesUsed} field.
         */
        public PlanNode joinPlan;

        /**
         * This field specifies the collection of leaf-plans that are joined by
         * the plan in this join-component.
         */
        public HashSet<PlanNode> leavesUsed;

        /**
         * This field specifies the collection of all conjuncts use by this join
         * plan.  It allows us to easily determine what join conjuncts still
         * remain to be incorporated into the query.
         */
        public HashSet<Expression> conjunctsUsed;

        /**
         * Constructs a new instance for a <em>leaf node</em>.  It should not
         * be used for join-plans that join together two or more leaves.  This
         * constructor simply adds the leaf-plan into the {@link #leavesUsed}
         * collection.
         *
         * @param leafPlan the query plan for this leaf of the query.
         *
         * @param conjunctsUsed the set of conjuncts used by the leaf plan.
         *        This may be an empty set if no conjuncts apply solely to
         *        this leaf, or it may be nonempty if some conjuncts apply
         *        solely to this leaf.
         */
        public JoinComponent(PlanNode leafPlan, HashSet<Expression> conjunctsUsed) {
            leavesUsed = new HashSet<>();
            leavesUsed.add(leafPlan);

            joinPlan = leafPlan;

            this.conjunctsUsed = conjunctsUsed;
        }

        /**
         * Constructs a new instance for a <em>non-leaf node</em>.  It should
         * not be used for leaf plans!
         *
         * @param joinPlan the query plan that joins together all leaves
         *        specified in the <tt>leavesUsed</tt> argument.
         *
         * @param leavesUsed the set of two or more leaf plans that are joined
         *        together by the join plan.
         *
         * @param conjunctsUsed the set of conjuncts used by the join plan.
         *        Obviously, it is expected that all conjuncts specified here
         *        can actually be evaluated against the join plan.
         */
        public JoinComponent(PlanNode joinPlan, HashSet<PlanNode> leavesUsed,
                             HashSet<Expression> conjunctsUsed) {
            this.joinPlan = joinPlan;
            this.leavesUsed = leavesUsed;
            this.conjunctsUsed = conjunctsUsed;
        }
    }


    /**
     * Returns the root of a plan tree suitable for executing the specified
     * query.
     *
     * @param selClause an object describing the query to be performed
     *
     * @return a plan tree for executing the specified query
     *
     * @throws java.io.IOException if an IO error occurs when the planner attempts to
     *         load schema and indexing information.
     */
    public PlanNode makePlan(SelectClause selClause,
        List<SelectClause> enclosingSelects) throws IOException {

        selClause = decorrelateSelectScalar(selClause);

        final SimpleExpressionProcessor processor = new SimpleExpressionProcessor();
        final List<SelectValue> selectValues = selClause.getSelectValues();

        final List<SelectClause> subqueryEnclosingSelects =
                Collections.singletonList(selClause);
        // TODO: Pass all enclosing selects instead of just the parent one?
//        subqueryEnclosingSelects.addAll(enclosingSelects);
        final SubqueryPlanner subqueryPlanner =
                new SubqueryPlanner(this, subqueryEnclosingSelects);

        /*
        Process expressions in select values and having clause.
        We need to replace aggregate function calls with string identifiers here.
         */
        Environment selectSubqEnv = new Environment();
        boolean selectSubQueriesFound = false;
        for (SelectValue sv : selectValues) {
            if (sv.isExpression()) {
                Expression e = sv.getExpression().traverse(processor);
                if (subqueryPlanner.planSubqueries(e, selectSubqEnv) != null)
                    selectSubQueriesFound = true;
                sv.setExpression(e);
            }
        }
        if (!selectSubQueriesFound) selectSubqEnv = null;
        Expression havingExpr = selClause.getHavingExpr();
        Environment havingSubqEnv = null;
        if (havingExpr != null) {
            havingExpr = havingExpr.traverse(processor);
            havingSubqEnv =  subqueryPlanner.planSubqueries(havingExpr);
        }

        // Process subqueries in WHERE clause first.
        Environment whereSubqEnv = null;
        if (selClause.getWhereExpr() != null) {
            whereSubqEnv =
                    subqueryPlanner.planSubqueries(selClause.getWhereExpr());
        }

        /*
        Process the from clause, by construction of join nodes,
        subqueries, renaming table, etc. See deconstructFrom().
         */
        PlanNode node = null;
        final FromClause fromClause = selClause.getFromClause();
        if(fromClause != null) {
            if (fromClause.isBaseTable()){
                Expression predicate = null;
                if (!fromClause.isRenamed())
                    predicate = selClause.getWhereExpr();
                node = makeSimpleSelect(fromClause.getTableName(), predicate,
                        enclosingSelects);
            } else if (fromClause.isDerivedTable()) {
                node = makePlan(fromClause.getSelectClause(), Collections.singletonList(selClause));
            } else {
                // Must be a join, create optimal join plan
                HashSet<Expression> expressions = new HashSet<>();
                if (enclosingSelects != null && enclosingSelects.size() > 0) {
                    HashMap<String, String> reverseTableNameMap = new HashMap<>();
                    String tableName = enclosingSelects.get(0).getFromClause().getResultName();
                    HashSet<Expression> parentExpressions = new HashSet<>();
                    enclosingSelects.get(0)
                            .getFromClause()
                            .getSelectClause()
                            .getSchema()
                            .getColumnInfos()
                            .forEach(c -> reverseTableNameMap.put(c.getName(), c.getTableName()));
                    PredicateUtils.collectConjuncts(enclosingSelects.get(0).getWhereExpr(), parentExpressions);
                    ExpressionProcessor scProcessor = new SubqueryConjunctsExpressionProcessor(reverseTableNameMap, tableName);
                    final Iterator<Expression> iter = parentExpressions.iterator();

                    while (iter.hasNext()) {
                        Expression e = iter.next();
                        Expression processed = e.duplicate().traverse(scProcessor);
                        if (!e.equals(processed)) {
                            expressions.add(processed);
                            iter.remove();
                        }
                    }

                    enclosingSelects.get(0).setWhereExpr(PredicateUtils.makePredicate(parentExpressions));
                }

                if (selClause.getWhereExpr() != null) {
                    PredicateUtils.collectConjuncts(selClause.getWhereExpr(), expressions);
                    selClause.setWhereExpr(null);
                }
                JoinComponent joinPlan = makeJoinPlan(fromClause, expressions);
                node = joinPlan.joinPlan;

                /*
                Restore ordering of columns for SELECT * after join reordering if necessary.
                 */
                if (selClause.isTrivialProject() &&
                        !node.getSchema().getColumnInfos().equals(selClause.getSchema().getColumnInfos())) {
                    node = new ProjectNode(node,
                            selClause.getSchema().getColumnInfos()
                                    .stream()
                                    .map(i -> new SelectValue(new ColumnValue(i.getColumnName()), null))
                                    .collect(Collectors.toList()));
                }
            }
            if (fromClause.isRenamed())
                node = new RenameNode(node, fromClause.getResultName());
        }

        /*
        Filter on the where clause.
         */
        if (selClause.getWhereExpr() != null) {
            assert fromClause != null;
            if (!fromClause.isBaseTable() || fromClause.isRenamed()) {
                node = new SimpleFilterNode(node, selClause.getWhereExpr());
            }
        }
        if (whereSubqEnv != null) node.setEnvironment(whereSubqEnv);

        /*
        Process group by clause and aggregate function calls if we need to.
         */
        final List<Expression> groupByExprs = selClause.getGroupByExprs();
        if (subqueryPlanner.containsSubqueries(groupByExprs)) {
            throw new ExpressionException("Group by clause contains a " +
                    "subquery!");
        }
        if (groupByExprs.size() > 0
                || !processor.getRenamedFunctionCallMap().isEmpty()) {
            node = new HashedGroupAggregateNode(node, groupByExprs, processor.getRenamedFunctionCallMap());
            if (havingSubqEnv != null) node.setEnvironment(havingSubqEnv);
        }

        /*
        Filter on the having clause, now that we've evaluated the function calls.
        We can't evaluate the having clause if there are unevaluated functions in it.
         */
        if (havingExpr != null)
            node = new SimpleFilterNode(node, havingExpr);

        /*
        Project the results if we need to.
         */
        if (!selClause.isTrivialProject()) {
            node = new ProjectNode(node, selClause.getSelectValues());
        }
        if (selectSubqEnv != null) node.setEnvironment(selectSubqEnv);

        /*
        Sort the results if we need to.
         */
        final List<OrderByExpression> orderByExprs = selClause.getOrderByExprs();
        for (OrderByExpression orderExpr : orderByExprs) {
            if (subqueryPlanner.containsSubqueries(orderExpr.getExpression())) {
                throw new ExpressionException("Order by clause contains a " +
                        "subquery!");
            }
        }
        if (orderByExprs.size() > 0)
            node = new SortNode(node, orderByExprs);

        /*
        Optionally, skip a number of rows and limit the total number of output rows.
         */
        if (selClause.isLimitSet() || selClause.isOffsetSet())
            node = new LimitOffsetNode(node, selClause.getLimit(), selClause.getOffset());

        /*
        Prepare node before returning.
        This will recursively prepare all child nodes in the planning tree,
        and generate the output schema.
         */
        node.prepare();
        return node;
    }

    /**
     * Decorrelates scalar queries in the specified select clause. Expected
     * query form:
     *    SELECT a, ..., (SELECT ... FROM t2 WHERE c = t1.d) sq FROM t1 ...
     *    WHERE ...;
     *
     * @param selectClause
     * @return
     * @throws IOException
     */
    private SelectClause decorrelateSelectScalar(SelectClause selectClause)
            throws IOException {

        List<Pair<FromClause, Expression>> joinPairs = new ArrayList<>();

        for (SelectValue sv : selectClause.getSelectValues()) {
            if (!sv.isExpression()) continue;
            if (!(sv.getExpression() instanceof SubqueryOperator)) continue;
            SubqueryOperator subOp = (SubqueryOperator) sv.getExpression();
            SelectClause subquerySelect = subOp.getSubquery();

            if (subquerySelect.getSelectValues().size() != 1) continue;

            SelectValue selVal = subquerySelect.getSelectValues().get(0);
            FromClause fromClause = subquerySelect.getFromClause();
            String tableName = fromClause.getTableName();
            Expression whereExpr = subquerySelect.getWhereExpr();
            if (!(selVal.isSimpleColumnValue())) continue;
            if (fromClause == null || !fromClause.isBaseTable()) continue;
            if (!(whereExpr instanceof CompareOperator)) continue;

            ColumnValue originalColVal = (ColumnValue) selVal.getExpression();
            CompareOperator compOp = (CompareOperator) whereExpr;
            if (compOp.getType() != CompareOperator.Type.EQUALS) continue;

            Expression leftExpr = compOp.getLeftExpression();
            Expression rightExpr = compOp.getRightExpression();
            if (!(leftExpr instanceof ColumnValue)
                    || !(rightExpr instanceof ColumnValue)) continue;

            ColumnValue leftColVal = (ColumnValue) leftExpr;
            ColumnValue rightColVal = (ColumnValue) rightExpr;

            if (leftColVal.getColumnName().getTableName()
                    .equals(rightColVal.getColumnName().getTableName()))
                continue;

            ColumnValue localColVal;
            if (leftColVal.getColumnName().getTableName().equals(tableName))
                localColVal = leftColVal;
            else if (rightColVal.getColumnName().getTableName()
                    .equals(tableName))
                localColVal = rightColVal;
            else continue;

            localColVal.getColumnName().setTableName("xxxxxx");

            FromClause newFromClause = new FromClause(tableName, "xxxxxx");
            newFromClause.computeSchema(storageManager.getTableManager());
            joinPairs.add(new Pair<>(newFromClause, whereExpr));

            if (subquerySelect.isTrivialProject()) {
                ColumnValue newColVal =
                        new ColumnValue(new ColumnName("xxxxxx",
                                selVal.getAlias()));
                sv.setExpression(newColVal);
            } else {
                originalColVal.getColumnName().setTableName("xxxxxx");
                sv.setExpression(originalColVal);
            }

        }

        FromClause currentLeftFromClause = selectClause.getFromClause();
        for (Pair<FromClause, Expression> joinPair : joinPairs) {
            FromClause newFromClause =
                    new FromClause(currentLeftFromClause, joinPair.getKey(),
                            JoinType.LEFT_OUTER);
            newFromClause.setOnExpression(joinPair.getValue());
            currentLeftFromClause = newFromClause;
        }
        selectClause.setFromClause(currentLeftFromClause);

        return selectClause;

    }

    /**
     * Given the top-level {@code FromClause} for a SELECT-FROM-WHERE block,
     * this helper generates an optimal join plan for the {@code FromClause}.
     *
     * @param fromClause the top-level {@code FromClause} of a
     *        SELECT-FROM-WHERE block.
     * @param extraConjuncts any extra conjuncts (e.g. from the WHERE clause,
     *        or HAVING clause)
     * @return a {@code JoinComponent} object that represents the optimal plan
     *         corresponding to the FROM-clause
     * @throws IOException if an IO error occurs during planning.
     */
    private JoinComponent makeJoinPlan(FromClause fromClause,
        Collection<Expression> extraConjuncts) throws IOException {

        // These variables receive the leaf-clauses and join conjuncts found
        // from scanning the sub-clauses.  Initially, we put the extra conjuncts
        // into the collection of conjuncts.
        HashSet<Expression> conjuncts = new HashSet<>();
        ArrayList<FromClause> leafFromClauses = new ArrayList<>();

        collectDetails(fromClause, conjuncts, leafFromClauses);

        logger.debug("Making join-plan for " + fromClause);
        logger.debug("    Collected conjuncts:  " + conjuncts);
        logger.debug("    Collected FROM-clauses:  " + leafFromClauses);
        logger.debug("    Extra conjuncts:  " + extraConjuncts);

        if (extraConjuncts != null)
            conjuncts.addAll(extraConjuncts);

        // Create a subplan for every single leaf FROM-clause, and prepare the
        // leaf-plan.

        logger.debug("Generating plans for all leaves");
        ArrayList<JoinComponent> leafComponents = generateLeafJoinComponents(
            leafFromClauses, conjuncts);

        // Print out the results, for debugging purposes.
        if (logger.isDebugEnabled()) {
            for (JoinComponent leaf : leafComponents) {
                logger.debug("    Leaf plan:\n" +
                    PlanNode.printNodeTreeToString(leaf.joinPlan, true));
            }
        }

        // Build up the full query-plan using a dynamic programming approach.

        JoinComponent optimalJoin =
            generateOptimalJoin(leafComponents, conjuncts);

        // Check that we've applied all possible conjuncts - otherwise the
        // user has specified some bogus conjuncts.
        HashSet<Expression> leftOverConjs = new HashSet<>(conjuncts);
        leftOverConjs.removeAll(optimalJoin.conjunctsUsed);
        if (!leftOverConjs.isEmpty())
            throw new ExpressionException("Some conjuncts weren't recognised: " +
                    leftOverConjs);

        PlanNode plan = optimalJoin.joinPlan;
        logger.info("Optimal join plan generated:\n" +
            PlanNode.printNodeTreeToString(plan, true));

        return optimalJoin;
    }


    /**
     * This helper method pulls the essential details for join optimization
     * out of a <tt>FROM</tt> clause.
     *
     * The logic is carried out by extracting "leaf" nodes and conjuncts from
     * the query. "Leaf" nodes are defined as either base-tables, derived
     * tables or outer-joins. If the supplied <tt>FromClause</tt> is a leaf,
     * it is added to the <tt>leafFromClauses</tt> list and returned.
     *
     * Any <tt>FromClause</tt> that isn't a leaf must be a join. In this
     * case, the method extracts the ON expression from the supplied
     * FromClause and stores it into the provided <tt>conjuncts</tt>. Then,
     * the method is called recursively on left and right children of the
     * <tt>FromClause</tt>.
     *
     * @param fromClause the from-clause to collect details from
     *
     * @param conjuncts the collection to add all conjuncts to
     *
     * @param leafFromClauses the collection to add all leaf from-clauses to
     */
    private void collectDetails(FromClause fromClause,
        HashSet<Expression> conjuncts, ArrayList<FromClause> leafFromClauses) {

        // Treat base-tables, subqueries and outer-joins as leaves
        if (fromClause.isBaseTable()
                || fromClause.isDerivedTable()
                || fromClause.isOuterJoin()) {
            leafFromClauses.add(fromClause);
            return;
        }

        assert fromClause.isJoinExpr();
        PredicateUtils
                .collectConjuncts(fromClause.getOnExpression(), conjuncts);

        FromClause leftFromClause = fromClause.getLeftChild();
        FromClause rightFromClause = fromClause.getRightChild();
        collectDetails(leftFromClause, conjuncts, leafFromClauses);
        collectDetails(rightFromClause, conjuncts, leafFromClauses);
    }


    /**
     * This helper method performs the first step of the dynamic programming
     * process to generate an optimal join plan, by generating a plan for every
     * leaf from-clause identified from analyzing the query.  Leaf plans are
     * usually very simple; they are built either from base-tables or
     * <tt>SELECT</tt> subqueries.  The most complex detail is that any
     * conjuncts in the query that can be evaluated solely against a particular
     * leaf plan-node will be associated with the plan node.  <em>This is a
     * heuristic</em> that usually produces good plans (and certainly will for
     * the current state of the database), but could easily interfere with
     * indexes or other plan optimizations.
     *
     * @param leafFromClauses the collection of from-clauses found in the query
     *
     * @param conjuncts the collection of conjuncts that can be applied at this
     *                  level
     *
     * @return a collection of {@link JoinComponent} object containing the plans
     *         and other details for each leaf from-clause
     *
     * @throws IOException if a particular database table couldn't be opened or
     *         schema loaded, for some reason
     */
    private ArrayList<JoinComponent> generateLeafJoinComponents(
        Collection<FromClause> leafFromClauses, Collection<Expression> conjuncts)
        throws IOException {

        // Create a subplan for every single leaf FROM-clause, and prepare the
        // leaf-plan.
        ArrayList<JoinComponent> leafComponents = new ArrayList<>();
        for (FromClause leafClause : leafFromClauses) {
            HashSet<Expression> leafConjuncts = new HashSet<>();

            PlanNode leafPlan =
                makeLeafPlan(leafClause, conjuncts, leafConjuncts);

            JoinComponent leaf = new JoinComponent(leafPlan, leafConjuncts);
            leafComponents.add(leaf);
        }

        return leafComponents;
    }


    /**
     * Constructs a plan tree for evaluating the specified from-clause.
     *
     * This method operates on "leaf" nodes generated by
     * {@link CostBasedJoinPlanner#collectDetails}, so <tt>FromClause</tt>
     * input can only be a base-table, derived table or an outer-join.
     *
     * In case of a derived table, {@link
     * CostBasedJoinPlanner#makePlan(SelectClause, List)} is called recursively
     * on the relevant select clause.
     *
     * In case of a base-table, a {@link FileScanNode} node for the relevant
     * table is created.
     *
     * In case of an outer-join, {@link
     * CostBasedJoinPlanner#makeJoinPlan(FromClause, Collection)} is used to
     * build the sub-plans for left and right child. For each child, we try
     * to push conjuncts down a child if the outer side of the join is not
     * opposite the child in question.
     *
     * In all cases, we try to apply expressions from <tt>conjuncts</tt> to
     * the resultant {@link FileScanNode}/sub-plan/join-plan. Additionally,
     * in all cases we rename the table if required by <tt>fromClause</tt>.
     *
     * @param fromClause the select nodes that need to be joined.
     *
     * @param conjuncts additional conjuncts that can be applied when
     *        constructing the from-clause plan.
     *
     * @param leafConjuncts this is an output-parameter.  Any conjuncts
     *        applied in this plan from the <tt>conjuncts</tt> collection
     *        should be added to this out-param.
     *
     * @return a plan tree for evaluating the specified from-clause
     *
     * @throws IOException if an IO error occurs when the planner attempts to
     *         load schema and indexing information.
     *
     * @throws IllegalArgumentException if the specified from-clause is a join
     *         expression that isn't an outer join, or has some other
     *         unrecognized type.
     */
    private PlanNode makeLeafPlan(FromClause fromClause,
        Collection<Expression> conjuncts, HashSet<Expression> leafConjuncts)
        throws IOException {


        PlanNode node;
        Schema schema;
        if (fromClause.isDerivedTable()) {
            node = makePlan(fromClause.getSelectClause(),
                    Collections.emptyList());

            // This node is guaranteed to be prepared, so we can just fetch
            // the schema straight away.
            schema = node.getSchema();
        } else if (fromClause.isJoinExpr()) {

            // If we're here, it's an outer join.
            assert fromClause.isOuterJoin();

            FromClause leftFrom = fromClause.getLeftChild();
            FromClause rightFrom = fromClause.getRightChild();

            HashSet<Expression> leftConjs = new HashSet<>();
            HashSet<Expression> rightConjs = new HashSet<>();
            if (!fromClause.hasOuterJoinOnRight()) {
                PredicateUtils.findExprsUsingSchemas(conjuncts, true,
                        leftConjs, leftFrom.getSchema());
            }
            if (!fromClause.hasOuterJoinOnLeft()) {
                PredicateUtils.findExprsUsingSchemas(conjuncts, true,
                        rightConjs, rightFrom.getSchema());
            }

            PlanNode leftChild = makeJoinPlan(leftFrom, leftConjs).joinPlan;
            PlanNode rightChild = makeJoinPlan(rightFrom, rightConjs).joinPlan;

            node = new NestedLoopJoinNode(
                    leftChild,
                    rightChild,
                    fromClause.getJoinType(),
                    fromClause.getOnExpression());

            // We don't really care about the order of the columns in the
            // schema, we just want to see what columns appear in it, so we
            // can build a temporary schema from child FromClause's
            schema = new Schema();
            schema.append(leftFrom.getSchema());
            schema.append(rightFrom.getSchema());
        } else {
            // Must be a base table, load it from file scan node.
            TableInfo tableInfo = storageManager.getTableManager()
                    .openTable(fromClause.getTableName());
            node = new FileScanNode(tableInfo, null);

            // In this case, we can just use the table's schema.
            schema = tableInfo.getSchema();
        }

        HashSet<Expression> applicableConjuncts = new HashSet<>();
        PredicateUtils.findExprsUsingSchemas(conjuncts, true,
                applicableConjuncts, schema);
        if (!applicableConjuncts.isEmpty()) {
            leafConjuncts.addAll(applicableConjuncts);
            Expression pred = PredicateUtils.makePredicate(applicableConjuncts);
            node = PlanUtils.addPredicateToPlan(node, pred);
        }

        if (fromClause.isRenamed()) {
            node = new RenameNode(node, fromClause.getResultName());
        }

        node.prepare();
        return node;
    }


    /**
     * This helper method builds up a full join-plan using a dynamic programming
     * approach.  The implementation maintains a collection of optimal
     * intermediate plans that join <em>n</em> of the leaf nodes, each with its
     * own associated cost, and then uses that collection to generate a new
     * collection of optimal intermediate plans that join <em>n+1</em> of the
     * leaf nodes.  This process completes when all leaf plans are joined
     * together; there will be <em>one</em> plan, and it will be the optimal
     * join plan (as far as our limited estimates can determine, anyway).
     *
     * @param leafComponents the collection of leaf join-components, generated
     *        by the {@link #generateLeafJoinComponents} method.
     *
     * @param conjuncts the collection of all conjuncts found in the query
     *
     * @return a single {@link JoinComponent} object that joins all leaf
     *         components together in an optimal way.
     */
    private JoinComponent generateOptimalJoin(
        ArrayList<JoinComponent> leafComponents, Set<Expression> conjuncts) {
        HashMap<HashSet<PlanNode>, JoinComponent> joinPlans = new HashMap<>();

        // Initially populate joinPlans with just the N leaf plans.
        for (JoinComponent leaf : leafComponents)
            joinPlans.put(leaf.leavesUsed, leaf);

        while (joinPlans.size() > 1) {
            logger.debug("Current set of join-plans has " + joinPlans.size() +
                " plans in it.");

            // This is the set of "next plans" we will generate.  Plans only
            // get stored if they are the first plan that joins together the
            // specified leaves, or if they are better than the current plan.
            HashMap<HashSet<PlanNode>, JoinComponent> nextJoinPlans =
                new HashMap<>();

            for (JoinComponent plan : joinPlans.values()) {

                for (JoinComponent leaf : leafComponents) {

                    if (plan.leavesUsed.containsAll(leaf.leavesUsed)) continue;
                    PlanNode leftNode = plan.joinPlan;
                    PlanNode rightNode = leaf.joinPlan;

                    // Check if we can add any conjuncts
                    HashSet<Expression> conjsUsed =
                            new HashSet<>(plan.conjunctsUsed);
                    conjsUsed.removeAll(leaf.conjunctsUsed);
                    HashSet<Expression> unusedConjs = new HashSet<>(conjuncts);
                    unusedConjs.removeAll(conjsUsed);
                    HashSet<Expression> conjToUse = new HashSet<>();
                    PredicateUtils.findExprsUsingSchemas(unusedConjs,
                            false, conjToUse,
                            leftNode.getSchema(), rightNode.getSchema());
                    conjsUsed.addAll(conjToUse);
                    Expression pred = null;
                    if (!conjToUse.isEmpty()) {
                        pred = PredicateUtils.makePredicate(conjToUse);
                    }

                    PlanNode node = new NestedLoopJoinNode(
                            leftNode,
                            rightNode,
                            JoinType.INNER,
                            pred);
                    node.prepare();

                    // Get all leaves used in the new plan
                    HashSet<PlanNode> leavesUsed =
                            new HashSet<>(plan.leavesUsed);
                    leavesUsed.addAll(leaf.leavesUsed);

                    // Check if the new plan is better (or the first plan for
                    // these leaves). If so, insert it/overwrite the old plan.
                    JoinComponent prevPlan = nextJoinPlans.get(leavesUsed);
                    if (prevPlan == null || comparePlanCosts(
                            prevPlan.joinPlan.getCost(), node.getCost())) {
                        nextJoinPlans.put(leavesUsed,
                                new JoinComponent(node, leavesUsed, conjsUsed));
                    }

                }

            }

            joinPlans = nextJoinPlans;
        }

        // At this point, the set of join plans should only contain one plan,
        // and it should be the optimal plan.

        assert joinPlans.size() == 1 : "There can be only one optimal join plan!";
        return joinPlans.values().iterator().next();
    }

    /**
     * Compares the supplied plan costs and determines which one is better.
     *
     * @param oldCost Cost of the old plan
     * @param newCost Cost of the new plan
     * @return true if new cost is better, false otherwise
     */
    private boolean comparePlanCosts(PlanCost oldCost, PlanCost newCost) {
        // TODO: Add a more elaborate check
        double nOldCost = oldCost.cpuCost + oldCost.ioCost;
        double nNewCost = newCost.cpuCost + newCost.ioCost;
        return nOldCost > nNewCost;
    }

    /**
     * Constructs a simple select plan that reads directly from a table, with
     * an optional predicate for selecting rows.
     * <p>
     * While this method can be used for building up larger <tt>SELECT</tt>
     * queries, the returned plan is also suitable for use in <tt>UPDATE</tt>
     * and <tt>DELETE</tt> command evaluation.  In these cases, the plan must
     * only generate tuples of type {@link edu.caltech.nanodb.storage.PageTuple},
     * so that the command can modify or delete the actual tuple in the file's
     * page data.
     *
     * @param tableName The name of the table that is being selected from.
     *
     * @param predicate An optional selection predicate, or {@code null} if
     *        no filtering is desired.
     *
     * @return A new plan-node for evaluating the select operation.
     *
     * @throws IOException if an error occurs when loading necessary table
     *         information.
     */
    public SelectNode makeSimpleSelect(String tableName, Expression predicate,
        List<SelectClause> enclosingSelects) throws IOException {
        if (tableName == null)
            throw new IllegalArgumentException("tableName cannot be null");

        if (enclosingSelects != null) {
            // If there are enclosing selects, this subquery's predicate may
            // reference an outer query's value, but we don't detect that here.
            // Therefore we will probably fail with an unrecognized column
            // reference.
            logger.warn("Currently we are not clever enough to detect " +
                "correlated subqueries, so things are likely to break...");
        }

        // Open the table.
        TableInfo tableInfo = storageManager.getTableManager().openTable(tableName);

        // Make a SelectNode to read rows from the table, with the specified
        // predicate.
        SelectNode selectNode = new FileScanNode(tableInfo, predicate);
        selectNode.prepare();
        return selectNode;
    }
}
