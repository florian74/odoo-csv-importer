package caf.com.odooimporter.reader;

import java.io.File;
import java.util.List;

// See Config class to see what instances is used
public interface FileReader {
    
    // read all including header
    public List<List<String>> readAll(File file);
}
