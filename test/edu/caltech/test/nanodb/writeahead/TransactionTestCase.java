package edu.caltech.test.nanodb.writeahead;

import edu.caltech.nanodb.transactions.TransactionManager;
import edu.caltech.test.nanodb.sql.SqlTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(enabled = false)
public class TransactionTestCase extends SqlTestCase {

    public TransactionTestCase(String setupSQLPropName) {
        super(setupSQLPropName);
    }

    @BeforeClass
    @Override
    public void beforeClass() throws Exception {

        // Enable transactions
        System.setProperty(TransactionManager.PROP_TXNS, "on");

        super.beforeClass();
    }

}
