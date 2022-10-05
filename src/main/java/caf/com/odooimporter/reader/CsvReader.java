package caf.com.odooimporter.reader;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Slf4j
public class CsvReader implements FileReader {
    
    String separator;
    
    public CsvReader(String separator) {
        this.separator = separator;
    }

    // interface method
    public List<List<String>> readAll(File file) {
        return readCsv(file);
    }
    
    
    private List<List<String>> readCsv(File file) {
        log.info("separator: "  + separator);
        try {
            List<List<String>> records = new ArrayList<>();
            try (Scanner scanner = new Scanner(file);) {
                while (scanner.hasNextLine()) {
                    records.add(getRecordFromLine(scanner.nextLine()));
                }
            }
            return records;
        } catch (Exception e) {
            // should not occur
        }
        return null;
    }


    private List<String> getRecordFromLine(String line) {
        List<String> values = new ArrayList<String>();
        try (Scanner rowScanner = new Scanner(line)) {
            rowScanner.useDelimiter(separator);
            while (rowScanner.hasNext()) {
                values.add(rowScanner.next());
            }
        }
        return values;
    }
}
