package caf.com.odooimporter.task;


import caf.com.odooimporter.rpc.Importer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.nio.file.Path;
import java.util.Set;

@Controller
@Slf4j
public class Scheduler {
    
    Importer importer;
    
    FileStore store;
    
    public Scheduler(@Autowired Importer importer, @Autowired FileStore store) {
        this.importer = importer;
        this.store = store;
        log.info("sheduler loaded");
    }
    
    // look for the files
    @Scheduled(cron = "${cron.schedule}")
    public void execute() {
        log.info("Task begin");
        
        Set<Path> paths = store.getFiles();
        log.info("found " + paths.size() + " element to import");
        
        importer.importAll(paths);
       
        
       log.info("store reset start");
       store.reset();
        
    }
    
}
