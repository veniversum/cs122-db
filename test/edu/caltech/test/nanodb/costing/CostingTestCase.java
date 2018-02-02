package edu.caltech.test.nanodb.costing;

import edu.caltech.nanodb.relations.TableInfo;
import edu.caltech.nanodb.storage.StorageManager;
import edu.caltech.nanodb.storage.TableManager;
import edu.caltech.test.nanodb.sql.SqlTestCase;
import org.testng.annotations.BeforeClass;

import java.util.HashMap;

public abstract class CostingTestCase extends SqlTestCase {

    private String[] tables;
    protected HashMap<String, TableInfo> tableInfoMap;

    public CostingTestCase(String sqlPropName, String[] tables) {
        super(sqlPropName);
        this.tables = tables;
        this.tableInfoMap = new HashMap<>();
    }

    @BeforeClass
    @Override
    public void beforeClass() throws Exception {
        super.beforeClass();

        // Analyze all tables to make sure they're ready for the tests.
        // Save all of the TableStats to hashmap that will be accessed in tests.
        StorageManager storageManager = server.getStorageManager();
        TableManager tableManager = storageManager.getTableManager();
        for (String table : tables) {
            TableInfo tableInfo = tableManager.openTable(table);
            tableManager.analyzeTable(tableInfo);
            tableInfoMap.put(table, tableInfo);
        }
    }

    /**
     * This test checks that at least one value was successfully inserted into
     * each of the test tables.
     *
     * @throws Exception if any query parsing or execution issues occur.
     */
    public void testTablesAreReady() throws Throwable {
        for (String table : tables) {
            testTableNotEmpty(table);
        }
    }

}
