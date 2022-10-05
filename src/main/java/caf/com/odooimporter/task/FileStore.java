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
    
    // reset file store so that it can be ready for the next iteration
    public abstract void reset();
    
    // remove all file from a given folder
    protected void clearFolder(String path) throws Exception {
        Files.walk(Paths.get(path))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(File::delete);
    }
}
