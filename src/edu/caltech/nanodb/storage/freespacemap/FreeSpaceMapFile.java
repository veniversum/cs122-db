package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.StorageManager;

public abstract class FreeSpaceMapFile {

    private StorageManager storageManager;

    private FreeSpaceMapFileManager fsmFileManager;

    /** File that stores the free space bitmap. */
    private DBFile dbFile;

    FreeSpaceMapFile(StorageManager storageManager, FreeSpaceMapFileManager fsmFileManager, DBFile dbFile) {

        if (storageManager == null)
            throw new IllegalArgumentException("storageManager cannot be null");
        if (fsmFileManager == null)
            throw new IllegalArgumentException("fsmFileManager cannot be null");
        if (dbFile == null)
            throw new IllegalArgumentException("dbFile cannot be null");

        this.storageManager = storageManager;
        this.fsmFileManager = fsmFileManager;
        this.dbFile = dbFile;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public FreeSpaceMapFileManager getFsmFileManager() {
        return fsmFileManager;
    }

    public abstract int findSuitablePage(int requiredSize);

    public abstract void updateFreeSpace(int pageNo, int freeSpace);

    /**
     * Returns the {@code DBFile} object that this free space map is stored in.
     *
     * @return the {@code DBFile} object that this free space map is stored in.
     */
    public DBFile getDBFile() {
        return dbFile;
    }

}
