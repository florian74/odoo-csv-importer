package caf.com.odooimporter.task.stores;

import caf.com.odooimporter.task.FileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// get file to import from an existing directory on the server
@Slf4j
public class FolderStore extends FileStore {
    
    String path;
    String tmpPath;
    Boolean isLocked = false;
    
    
    private String getPath() {
        if (isLocked) {
            return tmpPath;
        }
        return path;
    }
    
    public FolderStore(String folder) {
        log.info("Using folder store:" + path);
        this.path = folder;
        this.tmpPath = path + "/tmp";
        
        try {
            Files.createDirectories(Paths.get(tmpPath));
        } catch (Exception e) {
            log.error("cannot create temp directory", e);
        }
    }
    
    @Override
    public Set<Path> getFiles() {

        try  {
            return Files.walk(Paths.get(getPath()))
                    .filter(Files::isRegularFile)
                    .peek(f -> log.info("file found " + f.getFileName()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("could not look for file in folder", e);
            return new HashSet<>();
        }
    }

    @Override
    public boolean lock() {
        this.isLocked = true;
        return moveItems(path, tmpPath);
    }

    @Override
    public boolean unLock() {
        this.isLocked = false;
        return moveItems(tmpPath, path);
    }
    
    public boolean moveItems(String pathFrom, String pathTo) {
        try {
            return Files.walk(Paths.get(pathFrom)).filter(Files::isRegularFile).map(
                        f -> {
                            try {
                                Files.move(f, Paths.get(pathTo + "/"  + f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                return true;
                            } catch (Exception e) {
                                log.error("could not move to directory", e);
                                return false;
                            }
                        }
                ).filter(b -> b == false)
                .findFirst()
                .orElse(true);
        } catch (IOException e) {
            log.error("could not move directory from " + pathFrom + " to " + pathTo, e);
            return false;
        }
    }

    @Override
    public void reset() {
        try {
            super.clearFolder(getPath());
            
            if (isLocked) {
                unLock();
            }
        } catch (Exception e) {
            log.error("could not reset store", e);
        }
    }
}
