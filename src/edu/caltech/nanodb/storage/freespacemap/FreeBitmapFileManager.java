package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.queryeval.TableStats;
import edu.caltech.nanodb.relations.TableSchema;
import edu.caltech.nanodb.storage.*;
import edu.caltech.nanodb.storage.heapfile.HeaderPage;
import edu.caltech.nanodb.storage.heapfile.HeapTupleFile;
import edu.caltech.nanodb.storage.heapfile.HeapTupleFileManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FreeBitmapFileManager implements FreeSpaceMapFileManager {

    /** A logging object for reporting anything interesting that happens. */
    private static Logger logger = Logger.getLogger(FreeBitmapFileManager.class);


    /** A reference to the storage manager. */
    private StorageManager storageManager;


    public FreeBitmapFileManager(StorageManager storageManager) {
        if (storageManager == null)
            throw new IllegalArgumentException("storageManager cannot be null");

        this.storageManager = storageManager;
    }

    public FreeSpaceMapFile createFreeSpaceMapFile(DBFile dbFile) throws IOException {

        logger.info(String.format("Initializing new free bitmap file %s", dbFile));

        FreeSpaceMapFile freeSpaceMapFile = new FreeBitmapFile(this.storageManager, this, dbFile, new ArrayList<>());
        saveFreeSpaceMapFile(freeSpaceMapFile);
        return freeSpaceMapFile;
    }


    @Override
    public FreeSpaceMapFile openFreeSpaceMapFile(DBFile dbFile) throws IOException {

        logger.info("Opening existing free bitmap file " + dbFile);

        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);
        PageReader reader = new PageReader(headerPage);
        reader.setPosition(HeaderPage.OFFSET_SCHEMA_START);
        int size = reader.readInt();
        List<Boolean> bitmap = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bitmap.set(i, reader.readBoolean());
        }

        // TODO: Add reading from multiple pages

        return new FreeBitmapFile(this.storageManager, this, dbFile, bitmap);
    }

    @Override
    public void saveFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {

        if (freeSpaceMapFile == null)
            throw new IllegalArgumentException("freeSpaceMapFile cannot be null");

        if (!(freeSpaceMapFile instanceof FreeBitmapFile)) {
            throw new IllegalArgumentException(
                    "freeSpaceMapFile must be an instance of FreeBitmapFile");
        }

        FreeBitmapFile bitmapFile = (FreeBitmapFile) freeSpaceMapFile;

        DBFile dbFile = freeSpaceMapFile.getDBFile();
        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);

        PageWriter pageWriter = new PageWriter(headerPage);
        pageWriter.setPosition(HeaderPage.OFFSET_SCHEMA_START);
        pageWriter.writeInt(bitmapFile.getBitmap().size());

        for (boolean hasFreeSpace : bitmapFile.getBitmap()) {
            pageWriter.writeBoolean(hasFreeSpace);
        }

        logger.debug("Wrote bitmap of size " + bitmapFile.getBitmap().size() + " to " + dbFile);

        // TODO: Write to multiple pages if needed.

    }

    @Override
    public void deleteFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {
        // TODO
        throw new UnsupportedOperationException("NYI:  deleteFreeSpaceMapFile()");
    }

}
