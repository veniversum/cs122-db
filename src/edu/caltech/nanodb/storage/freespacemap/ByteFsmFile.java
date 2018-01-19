package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.StorageManager;
import org.apache.log4j.Logger;

/**
 * Class managing a free space map file for a particular table.
 */
public class ByteFsmFile extends FreeSpaceMapFile {

    private static Logger logger = Logger.getLogger(ByteFsmFile.class);

    private byte[] map;
    private int mapSize;
    private int tupleFilePageSize;
    private float multiplier;

    public ByteFsmFile(StorageManager storageManager, ByteFsmFileManager byteFsmFileManager,
                       DBFile dbFile, byte[] map, int mapSize) {
        super(storageManager, byteFsmFileManager, dbFile);

        this.map = map;
        this.mapSize = mapSize;
        this.tupleFilePageSize = dbFile.getPageSize();
        this.multiplier = 256.0f / this.tupleFilePageSize;
    }

    public byte[] getMap() {
        return map;
    }

    static int bytesToUnsigned(byte b) {
        return b & 0xFF;
    }

    public int getMapSize() {
        return mapSize;
    }

    @Override
    public int findSuitablePage(int requiredSize) {
        float freeSpaceFraction = (float) requiredSize * multiplier;
        for (int i = 0; i < this.mapSize; i++) {
            if (freeSpaceFraction < bytesToUnsigned(this.map[i])) return i + 1;
        }
        return this.mapSize + 1;
    }

    @Override
    public void updateFreeSpace(int pageNo, int freeSpace) {
        byte freeSpaceFraction = (byte) Math.floor(freeSpace * multiplier);
        map[pageNo - 1] = freeSpaceFraction;
        if (pageNo > this.mapSize) this.mapSize++;
    }
}
