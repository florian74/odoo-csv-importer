package caf.com.odooimporter.task.stores;

import caf.com.odooimporter.task.FileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// get file to import from an existing directory on the server
@Slf4j
public class FolderStore extends FileStore {
    
    String path;
    
    public FolderStore(String folder) {
        log.info("Using folder store", path);
        this.path = folder;
    }
    
    @Override
    public Set<Path> getFiles() {

        try  {
            return Files.walk(Paths.get(path))
                    .filter(Files::isRegularFile).collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("could not look for file in folder", e);
            return new HashSet<>();
        }
    }

    @Override
    public void reset() {
        try {
            super.clearFolder(path);
        } catch (Exception e) {
            log.error("could not reset store", e);
        }
    }
}
