package edu.caltech.nanodb.storage.freespacemap;

import edu.caltech.nanodb.storage.DBFile;
import edu.caltech.nanodb.storage.StorageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Class managing a free space map file for a particular table.
 */
public class FreeBitmapFile extends FreeSpaceMapFile {

    private List<Boolean> bitmap;

    public FreeBitmapFile(StorageManager storageManager, FreeBitmapFileManager freeBitmapFileManager, DBFile dbFile, List<Boolean> bitmap) {
        super(storageManager, freeBitmapFileManager, dbFile);

        this.bitmap = bitmap;
    }

    public List<Boolean> getBitmap() {
        return bitmap;
    }
}
