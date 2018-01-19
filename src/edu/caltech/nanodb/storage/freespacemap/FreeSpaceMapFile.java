package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;

public abstract class FreeSpaceMapFile {

    /** File that stores the free space bitmap. */
    private DBFile dbFile;

    FreeSpaceMapFile(DBFile dbFile) {

        if (dbFile == null)
            throw new IllegalArgumentException("dbFile cannot be null");

        this.dbFile = dbFile;
    }

    /**
     * Returns the {@code DBFile} object that this free space map is stored in.
     *
     * @return the {@code DBFile} object that this free space map is stored in.
     */
    public DBFile getDbFile() {
        return dbFile;
    }

}
