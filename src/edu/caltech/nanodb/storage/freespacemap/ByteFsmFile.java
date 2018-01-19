package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.DBPage;
import edu.caltech.nanodb.storage.StorageManager;
import edu.caltech.nanodb.storage.TupleFile;
import edu.caltech.nanodb.storage.heapfile.DataPage;
import org.apache.log4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

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
    private long checksum;

    public ByteFsmFile(StorageManager storageManager, ByteFsmFileManager byteFsmFileManager,
                       DBFile dbFile, byte[] map, int mapSize) {
        super(storageManager, byteFsmFileManager, dbFile);

        this.map = map;
        this.mapSize = mapSize;
        this.tupleFilePageSize = dbFile.getPageSize();
        this.multiplier = 256.0f / this.tupleFilePageSize;
        this.checksum = calculateChecksum();
    }

    public ByteFsmFile(StorageManager storageManager, ByteFsmFileManager byteFsmFileManager,
                       DBFile dbFile, byte[] map, int mapSize, long checksum) {
        super(storageManager, byteFsmFileManager, dbFile);

        this.map = map;
        this.mapSize = mapSize;
        this.tupleFilePageSize = dbFile.getPageSize();
        this.multiplier = 256.0f / this.tupleFilePageSize;
        this.checksum = checksum;
    }

    public byte[] getMap() {
        return map;
    }

    public long getChecksum() {
        return checksum;
    }

    public int getMapSize() {
        return mapSize;
    }

    private static int unsignedByteToInt(final byte b) {
        return b & 0xFF;
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
        if (pageNo > mapSize) mapSize++;
    }

    private long calculateChecksum() {
        Checksum checksum = new CRC32();
        checksum.update(map, 0, mapSize);
        return checksum.getValue();
    }

    @Override
    public boolean checkIntegrity() {
        return calculateChecksum() == checksum;
    }

    @Override
    public void rebuild(TupleFile tupleFile) throws IOException {

        DBFile tupleDbFile = tupleFile.getDBFile();
        int pageNo = 1;
        while (true) {
            try {
                DBPage dbPage = storageManager.loadDBPage(tupleDbFile, pageNo);
                int freeSpace = DataPage.getFreeSpaceInPage(dbPage);
                byte freeSpaceFraction = (byte) Math.floor(freeSpace * multiplier);
                map[pageNo - 1] = freeSpaceFraction;
            } catch (EOFException eofe) {
                // Reached the end of file, done building.
                break;
            }
            pageNo++;
        }

        mapSize = pageNo;
        checksum = calculateChecksum();
    }
}
