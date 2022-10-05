package caf.com.odooimporter.config;

import caf.com.odooimporter.reader.CsvReader;
import caf.com.odooimporter.reader.FileReader;
import caf.com.odooimporter.task.FileStore;
import caf.com.odooimporter.task.stores.FolderStore;
import caf.com.odooimporter.task.stores.MinioFileStore;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:application.properties")
@Setter
@Slf4j
public class Config {

    // FILE STORAGE
    
    @Value("${store.type}")
    String type;
    
    @Value("${store.folder.path}")
    String folder;

    // MINIO
    @Value("${store.minio.url}")
    String minioUrl;

    @Value("${store.minio.accessKey}")
    String accessKey;

    @Value("${store.minio.secretKey}")
    String secretKey;

    @Value("${store.minio.bucket}")
    String bucket;
    
    
    @Value("${csv.separator}")
    String separator;
    
    @Bean
    public FileStore fileStore() {
        if (type.equals("minio")) {
            return new MinioFileStore(folder, minioUrl, secretKey, accessKey, bucket);
        }
        return new FolderStore(folder);
    }
    
    
    // file reader to use, only csv is used
    // but if other are used, get a value from application.properties and make the swicth here
    @Bean
    public FileReader fileReader() {
        return new CsvReader(separator);
    }
    

}
