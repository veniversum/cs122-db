package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.*;
import edu.caltech.nanodb.storage.heapfile.HeaderPage;
import org.apache.log4j.Logger;

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
        int size = reader.readInt();
        byte[] freeSpaceMap = new byte[65536];
        reader.read(freeSpaceMap, HeaderPage.OFFSET_SCHEMA_SIZE + 1, size);

        // TODO: Add reading from multiple pages

        return new ByteFsmFile(this.storageManager, this, dbFile, freeSpaceMap, size);
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

        DBFile dbFile = freeSpaceMapFile.getDBFile();
        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);

        PageWriter pageWriter = new PageWriter(headerPage);
        pageWriter.setPosition(HeaderPage.OFFSET_SCHEMA_START);
        pageWriter.writeInt(byteFsmFile.getMapSize());
        pageWriter.write(byteFsmFile.getMap(), HeaderPage.OFFSET_SCHEMA_SIZE + 1, byteFsmFile.getMapSize());

        logger.debug("Wrote byte fsm of size " + byteFsmFile.getMapSize() + " to " + dbFile);

        // TODO: Write to multiple pages if needed.

    }

    @Override
    public void deleteFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {
        // TODO
        throw new UnsupportedOperationException("NYI:  deleteFreeSpaceMapFile()");
    }

}
