package edu.caltech.nanodb.relations;


import edu.caltech.nanodb.queryeval.TableStats;
import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.TupleFile;
import edu.caltech.nanodb.storage.freespacemap.FreeBitmapFile;
import edu.caltech.nanodb.storage.freespacemap.FreeSpaceMapFile;


/**
 * This class represents a single table in the database, including the table's
 * name, and the tuple file that holds the table's data.
 */
public class TableInfo {
    /** If a table name isn't specified, this value is used instead. */
    public static final String UNNAMED_TABLE = "(unnamed)";


    /** The name of this table. */
    private String tableName;


    /** The tuple file that holds the table's data. */
    private TupleFile tupleFile;


    /** The file that holds the table's free space map. */
    private FreeSpaceMapFile freeSpaceMapFile;


    /**
     * Construct a table-information object that represents the specified
     * table name and associated tuple file.
     *
     * @param tableName the name of the table in the database
     *
     * @param tupleFile the tuple file that holds the table's data
     * @deprecated
     */
    public TableInfo(String tableName, TupleFile tupleFile) {
        if (tableName == null)
            tableName = UNNAMED_TABLE;

        this.tableName = tableName;
        this.tupleFile = tupleFile;
    }


    /**
     * Construct a table-information object that represents the specified
     * table name, associated tuple file and a free space map file.
     *
     * @param tableName the name of the table in the database
     *
     * @param tupleFile the tuple file that holds the table's data
     *
     * @param freeSpaceMapFile file that holds the table's free space map
     */
    public TableInfo(String tableName, TupleFile tupleFile, FreeSpaceMapFile freeSpaceMapFile) {
        if (tableName == null)
            tableName = UNNAMED_TABLE;

        this.tableName = tableName;
        this.tupleFile = tupleFile;
        this.freeSpaceMapFile = freeSpaceMapFile;
    }


    /**
     * Returns the associated table name.
     *
     * @return the associated table name
     */
    public String getTableName() {
        return tableName;
    }


    /**
     * Returns the tuple file that holds the table's data.
     *
     * @return the tuple file that holds the table's data.
     */
    public TupleFile getTupleFile() {
        return tupleFile;
    }


    /** A helper function to simplify retrieving the table's {@code DBFile}. */
    public DBFile getDBFile() {
        return tupleFile.getDBFile();
    }


    /** A helper function to simplify retrieving the table's schema. */
    public TableSchema getSchema() {
        return tupleFile.getSchema();
    }


    public TableStats getStats() {
        return tupleFile.getStats();
    }
}
