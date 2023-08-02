package caf.com.odooimporter.task;

import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

// See Config class to see what instances is used
public abstract class FileStore {
    
    // get all the possible files from the stores
    // for now this is simple and does not allow sub paths
    public abstract Set<Path> getFiles();
    
    // lock the file list, move the files to a temporary folder
    // return true if lock is successfull
    public abstract boolean lock();
    
    // remove the temporary folder, and remaining files are copy back to the location
    // return true if unlock is successfull
    public abstract boolean unLock();
    
    // reset file store so that it can be ready for the next iteration
    // perform unLock if not done
    public abstract void reset();
    
    // remove all file from a given folder
    protected void clearFolder(String path) throws Exception {
        Files.walk(Paths.get(path))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(File::delete);
    }
}
