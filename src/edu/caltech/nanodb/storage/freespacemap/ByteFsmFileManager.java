package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.*;
import edu.caltech.nanodb.storage.heapfile.DataPage;
import edu.caltech.nanodb.storage.heapfile.HeaderPage;
import org.apache.log4j.Logger;

import java.io.EOFException;
import java.io.IOException;

public class ByteFsmFileManager implements FreeSpaceMapFileManager {

    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(ByteFsmFileManager.class);


    /** A reference to the storage manager. */
    private StorageManager storageManager;


    public ByteFsmFileManager(StorageManager storageManager) {
        if (storageManager == null)
            throw new IllegalArgumentException("storageManager cannot be null");

        this.storageManager = storageManager;
    }

    public FreeSpaceMapFile createFreeSpaceMapFile(DBFile dbFile) throws IOException {

        logger.info(String.format("Initializing new byte fsm file %s", dbFile));

        FreeSpaceMapFile freeSpaceMapFile = new ByteFsmFile(this.storageManager, this, dbFile, new byte[65536], 0);
        saveFreeSpaceMapFile(freeSpaceMapFile);
        return freeSpaceMapFile;
    }


    @Override
    public FreeSpaceMapFile openFreeSpaceMapFile(DBFile dbFile) throws IOException {

        logger.info("Opening existing byte fsm file " + dbFile);

        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);
        PageReader reader = new PageReader(headerPage);
        reader.setPosition(HeaderPage.OFFSET_SCHEMA_START);
        int mapSize = reader.readInt();
        int pageSize = dbFile.getPageSize();
        byte[] map = new byte[65536];

        // Read as much of the map as possible from the remainder of the header page
        int headerPageLeftover = pageSize - 10;
        reader.read(map, 0, Math.min(mapSize, headerPageLeftover));


        // Read the remaining part of the map from subsequent pages
        int leftToRead = Math.max(0, mapSize - headerPageLeftover);
        int mapOffset = headerPageLeftover;
        int pageNo = 1;
        while (leftToRead > 0) {
            DBPage dbPage = storageManager.loadDBPage(dbFile, pageNo);
            int toBeRead = Math.min(leftToRead, pageSize);
            reader = new PageReader(dbPage);
            reader.read(map, mapOffset, toBeRead);
            leftToRead -= toBeRead;
            mapOffset += pageSize;
            pageNo++;
        }

        logger.debug("Read byte fsm of size " + mapSize + " (" + pageNo + " pages) from " + dbFile);

        return new ByteFsmFile(this.storageManager, this, dbFile, map, mapSize);
    }

    @Override
    public void saveFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {

        if (freeSpaceMapFile == null)
            throw new IllegalArgumentException("freeSpaceMapFile cannot be null");

        if (!(freeSpaceMapFile instanceof ByteFsmFile)) {
            throw new IllegalArgumentException(
                    "freeSpaceMapFile must be an instance of ByteFsmFile");
        }

        ByteFsmFile byteFsmFile = (ByteFsmFile) freeSpaceMapFile;
        byte[] map = byteFsmFile.getMap();
        int mapSize = byteFsmFile.getMapSize();

        // Retrieve header page, write map size as the 3rd byte
        DBFile dbFile = freeSpaceMapFile.getDBFile();
        int pageSize = dbFile.getPageSize();
        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);
        PageWriter pageWriter = new PageWriter(headerPage);
        pageWriter.setPosition(HeaderPage.OFFSET_SCHEMA_START);
        pageWriter.writeInt(mapSize);

        // Write as much of the map as possible into the remainder of the header page
        int headerPageLeftover = pageSize - 10;

        pageWriter.write(map, 0, Math.min(mapSize, headerPageLeftover));

        // Write the remaining part of the map into subsequent pages, extending file if needed
        int overflow = Math.max(0, mapSize - headerPageLeftover);

        int mapOffset = headerPageLeftover;
        int pageNo = 1;
        while (overflow > 0) {
            DBPage dbPage = storageManager.loadDBPage(dbFile, pageNo, true);
            int toBeWritten = Math.min(overflow, pageSize);
            pageWriter = new PageWriter(dbPage);
            pageWriter.write(map, mapOffset, toBeWritten);
            overflow -= toBeWritten;
            mapOffset += pageSize;
            pageNo++;
        }

        logger.debug("Wrote byte fsm of size " + mapSize + " (" + pageNo + " pages) to " + dbFile);

    }

    @Override
    public void deleteFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {
        // TODO
        throw new UnsupportedOperationException("NYI:  deleteFreeSpaceMapFile()");
    }

}
