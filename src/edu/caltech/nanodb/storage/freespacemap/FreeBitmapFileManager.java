package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.queryeval.TableStats;
import edu.caltech.nanodb.relations.TableSchema;
import edu.caltech.nanodb.storage.*;
import edu.caltech.nanodb.storage.heapfile.HeaderPage;
import edu.caltech.nanodb.storage.heapfile.HeapTupleFile;
import edu.caltech.nanodb.storage.heapfile.HeapTupleFileManager;
import org.apache.log4j.Logger;

import java.io.IOException;

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

        FreeSpaceMapFile freeSpaceMapFile = new FreeBitmapFile(dbFile);
        saveFreeSpaceMapFile(freeSpaceMapFile);
        return freeSpaceMapFile;
    }


    @Override
    public FreeSpaceMapFile openFreeSpaceMapFile(DBFile dbFile) throws IOException {

        logger.info("Opening existing free bitmap file " + dbFile);

        return null; // TODO: Implement opening free bitmap from dbFile.

//        // Table schema is stored into the header page, so get it and prepare
//        // to write out the schema information.
//        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);
//        PageReader hpReader = new PageReader(headerPage);
//        // Skip past the page-size value.
//        hpReader.setPosition(HeaderPage.OFFSET_SCHEMA_START);
//
//        // Read in the schema details.
//        SchemaWriter schemaWriter = new SchemaWriter();
//        TableSchema schema = schemaWriter.readTableSchema(hpReader);
//
//        // Read in the statistics.
//        StatsWriter statsWriter = new StatsWriter();
//        TableStats stats = statsWriter.readTableStats(hpReader, schema);
//
//        return new HeapTupleFile(storageManager, this, dbFile, schema, stats);
    }

    @Override
    public void saveFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {

        if (freeSpaceMapFile == null)
            throw new IllegalArgumentException("freeSpaceMapFile cannot be null");

        // TODO: Implement saving free bitmap file.

//        // Curiously, we never cast the tupleFile reference to HeapTupleFile,
//        // but still, it would be very awkward if we tried to update the
//        // metadata of some different kind of tuple file...
//        if (!(freeSpaceMapFile instanceof HeapTupleFile)) {
//            throw new IllegalArgumentException(
//                    "tupleFile must be an instance of HeapTupleFile");
//        }
//
//        DBFile dbFile = freeSpaceMapFile.getDBFile();
//
//        TableSchema schema = freeSpaceMapFile.getSchema();
//        TableStats stats = freeSpaceMapFile.getStats();
//
//        // Table schema is stored into the header page, so get it and prepare
//        // to write out the schema information.
//        DBPage headerPage = storageManager.loadDBPage(dbFile, 0);
//        PageWriter hpWriter = new PageWriter(headerPage);
//        // Skip past the page-size value.
//        hpWriter.setPosition(HeaderPage.OFFSET_SCHEMA_START);
//
//        // Write out the schema details now.
//        SchemaWriter schemaWriter = new SchemaWriter();
//        schemaWriter.writeTableSchema(schema, hpWriter);
//
//        // Compute and store the schema's size.
//        int schemaEndPos = hpWriter.getPosition();
//        int schemaSize = schemaEndPos - HeaderPage.OFFSET_SCHEMA_START;
//        HeaderPage.setSchemaSize(headerPage, schemaSize);
//
//        // Write in empty statistics, so that the values are at least
//        // initialized to something.
//        StatsWriter statsWriter = new StatsWriter();
//        statsWriter.writeTableStats(schema, stats, hpWriter);
//        int statsSize = hpWriter.getPosition() - schemaEndPos;
//        HeaderPage.setStatsSize(headerPage, statsSize);
    }

    @Override
    public void deleteFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException {
        // TODO
        throw new UnsupportedOperationException("NYI:  deleteFreeSpaceMapFile()");
    }

}
