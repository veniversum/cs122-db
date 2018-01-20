package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.relations.TableSchema;
import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.TupleFile;

import java.io.IOException;

/**
 * This interface defines the operations that can be performed on
 * {@link FreeSpaceMapFile}s, but that are at a higher level of implementation than
 * the free space map file itself.
 */
public interface FreeSpaceMapFileManager {

    public FreeSpaceMapFile createFreeSpaceMapFile(DBFile dbFile) throws IOException;


    public FreeSpaceMapFile openFreeSpaceMapFile(DBFile dbFile) throws IOException;


    public void saveFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException;


    public void deleteFreeSpaceMapFile(FreeSpaceMapFile freeSpaceMapFile) throws IOException;
}
