package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;

public abstract class FreeSpaceMapFile {

    private FreeSpaceMapFileManager freeSpaceMapFileManager;

    /** File that stores the free space bitmap. */
    private DBFile dbFile;

    FreeSpaceMapFile(FreeSpaceMapFileManager freeSpaceMapFileManager, DBFile dbFile) {

        if (freeSpaceMapFileManager == null)
            throw new IllegalArgumentException("freeSpaceMapFileManager cannot be null");

        if (dbFile == null)
            throw new IllegalArgumentException("dbFile cannot be null");

        this.freeSpaceMapFileManager = freeSpaceMapFileManager;
        this.dbFile = dbFile;
    }

    public FreeSpaceMapFileManager getFreeSpaceMapFileManager() {
        return freeSpaceMapFileManager;
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
