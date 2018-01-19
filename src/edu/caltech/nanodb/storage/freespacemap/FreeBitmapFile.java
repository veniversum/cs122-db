package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;

/**
 * Class managing a free space map file for a particular table.
 */
public class FreeBitmapFile extends FreeSpaceMapFile {

    public FreeBitmapFile(FreeBitmapFileManager freeBitmapFileManager, DBFile dbFile) {
        super(freeBitmapFileManager, dbFile);
    }

}
