package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.StorageManager;

/**
 * Class managing a free space map file for a particular table.
 */
public class ByteFsmFile extends FreeSpaceMapFile {

    private byte[] map;
    private int mapSize;
    private int tupleFilePageSize;

    public ByteFsmFile(StorageManager storageManager, ByteFsmFileManager byteFsmFileManager,
                       DBFile dbFile, byte[] map, int mapSize) {
        super(storageManager, byteFsmFileManager, dbFile);

        this.map = map;
        this.mapSize = mapSize;
        this.tupleFilePageSize = dbFile.getPageSize();
    }

    public byte[] getMap() {
        return map;
    }

    public int getMapSize() {
        return mapSize;
    }

    @Override
    public int findSuitablePage(int requiredSize) {
        byte freeSpaceFraction = (byte) Math.ceil(((float) requiredSize / tupleFilePageSize) * 256);
        for (int i = 0; i < this.mapSize; i++) {
            if (freeSpaceFraction < this.map[i]) return i + 1;
        }
        return this.mapSize + 1;
    }

    @Override
    public void updateFreeSpace(int pageNo, int freeSpace) {
        byte freeSpaceFraction = (byte) Math.floor(((float) freeSpace / tupleFilePageSize) * 256);
        map[pageNo - 1] = freeSpaceFraction;
        if (pageNo > this.mapSize) this.mapSize++;
    }
}
