package caf.com.odooimporter.task.stores;

import caf.com.odooimporter.task.FileStore;
import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


// get files to import from a minio server
@Slf4j
@Getter
@Setter
public class MinioFileStore extends FileStore {

    
    String path;
    Boolean isLocked = false;
    String bucket;
    MinioClient client;
    
    // TODO parametrized this 3 fields
    String prefix = "auto/";            // must end with /
    String tmpPrefix = "tmp/";

    String autoFolder = "auto";
    String importedFolder = "imported"; // must not end with /
    String temporaryFolder = "tmp"; // must not end with /
    Boolean deleteSource = true;
    
    private String getPrefix() {
        if (isLocked) {
            return tmpPrefix;
        }
        return prefix;
    }
    
    
    public MinioFileStore(String path, String url, String secretKey, String accessKey, String bucket) {
        log.info("Using minio store, url " + url +  ", path:" + path);
        this.path = path;
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
    
    @Override
    public boolean lock() {
        
        try {
            moveItems(temporaryFolder, true);
            isLocked = true;
            return true;
        } catch (Exception e) {
            log.error("could not move item from bucket", e);
            return false;
        }
        
    }
    
    @Override
    public boolean unLock() {
        try{
            moveItems(autoFolder, true);
            isLocked = false;
            return true;
        }  catch (Exception e) {
            log.error("could not move item from bucket", e);
            return false;
        }
    }
    
    // download all items in "auto" folder of the given bucket
    @Override
    public Set<Path> getFiles() {
        
        try  {
            listItems().forEach(
                    item -> {
                        try {
                            Item toDownload =item.get();
                            log.info(path + "/" + toDownload.objectName().substring(getPrefix().length()));
                            if (! toDownload.isDir()) {
                                Files.deleteIfExists(Paths.get(path + "/" + toDownload.objectName().substring(getPrefix().length())));
                                client.downloadObject(
                                        DownloadObjectArgs.builder()
                                                .bucket(bucket)
                                                .object(toDownload.objectName())
                                                .filename(path + "/" + toDownload.objectName().substring(getPrefix().length()))
                                                .build());
                            }
                        }
                        catch (Exception e) {
                            log.error("could not download item from bucket", e);
                        }
                    }
            );
            
            // return all downloaded file
            return Files.walk(Paths.get(path))
                    .filter(Files::isRegularFile).collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("could not look for file in folder", e);
            return new HashSet<>();
        }
        
        
    }
    
    // remove all file in working directory
    // move processed items to "imported" sub folder 
    @Override
    public void reset() {
        try {
            moveItems(importedFolder, deleteSource);
            super.clearFolder(path);

            if (isLocked) {
                unLock();
            }
            
        } catch (Exception e) {
            log.error("could not reset store", e);
        }
    }
    
    private Iterable<Result<Item>>  listItems() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (! exists) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket)
                    .build()
            );
        }
        
        return client.listObjects(ListObjectsArgs.builder()
                .prefix(getPrefix())
                .recursive(true)
                .bucket(bucket)
                .build());
    }
    
    private void moveItems(String folder, Boolean deleteSource) throws Exception {
        listItems().forEach(
                item -> {
                    try {
                        Item toMove =item.get();
                        if (! toMove.isDir()) {
                            client.copyObject(
                                    CopyObjectArgs.builder()
                                            .bucket(bucket)
                                            .object(folder + "/" + toMove.objectName().substring(getPrefix().length()))
                                            .source(
                                                    CopySource.builder()
                                                            .bucket(bucket)
                                                            .object(toMove.objectName())
                                                            .build())
                                            .build());

                            if (deleteSource) {
                                client.removeObject(
                                        RemoveObjectArgs.builder()
                                                .bucket(bucket)
                                                .object(toMove.objectName())
                                                .build()
                                );
                            }
                        }
                    }
                    catch (Exception e) {
                        log.error("could not download item from bucket", e);
                    }
                }
        );
        log.info("items moved to " + folder + " from " + getPrefix());
    }
}
