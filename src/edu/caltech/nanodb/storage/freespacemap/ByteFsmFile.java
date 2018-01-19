package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.StorageManager;
import org.apache.log4j.Logger;

/**
 * Free space map implementation for tracking free space in a
 * <code>DBFile</code>.
 *
 * A byte is used for each page in the <code>DBFile</code>, allowing
 * for tupleFilePageSize / 265 granularity in the amount of free
 * space we can store.
 */
public class ByteFsmFile extends FreeSpaceMapFile {

    private static Logger logger = Logger.getLogger(ByteFsmFile.class);

    final private byte[] map;
    private int mapSize;
    final private int tupleFilePageSize;
    final private float multiplier;

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

    private static int unsignedByteToInt(final byte b) {
        return b & 0xFF;
    }

    public int getMapSize() {
        return mapSize;
    }

    /**
     * Finds the first page in the <code>DBFile</code> that
     * has free space greater than <code>requiredSize</code>.
     *
     * @param requiredSize required free space for data
     * @return pageNo of the page with free space
     */
    @Override
    public int findSuitablePage(final int requiredSize) {
        float freeSpaceFraction = multiplier * requiredSize;
        for (int i = 0; i < this.mapSize; i++) {
            if (freeSpaceFraction < unsignedByteToInt(this.map[i])) return i + 1;
        }
        return this.mapSize + 1;
    }


    /**
     * Updates the amount of free space in a page.
     *
     * @param pageNo    pageNo of the page to update
     * @param freeSpace amount of free space in page
     */
    @Override
    public void updateFreeSpace(final int pageNo, final int freeSpace) {
        byte freeSpaceFraction = (byte) Math.floor(freeSpace * multiplier);
        map[pageNo - 1] = freeSpaceFraction;
        if (pageNo > this.mapSize) this.mapSize++;
    }
}
