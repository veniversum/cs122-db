package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.StorageManager;

/**
 * Class managing a free space map file for a particular table.
 */
public class ByteFsmFile extends FreeSpaceMapFile {

    private byte[] map;
    private int mapSize;

    public ByteFsmFile(StorageManager storageManager, ByteFsmFileManager byteFsmFileManager, DBFile dbFile, byte[] map, int mapSize) {
        super(storageManager, byteFsmFileManager, dbFile);

        this.map = map;
        this.mapSize = mapSize;
    }

    public byte[] getMap() {
        return map;
    }

    public int getMapSize() {
        return mapSize;
    }

    @Override
    public int findSuitablePage(int requiredSize) {
        return 0;
    }
}
