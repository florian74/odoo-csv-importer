package caf.com.odooimporter.rpc;

import java.util.ArrayList;
import java.util.List;

// util class for filter object in search_object command
public class Filter {
    
    List<List<Object>> filter;
    
    
    public Filter() {
        this.filter = new ArrayList<>();
    }
    
    public Filter add(Object... args) {
        List<Object> subFilter = new ArrayList<>();
        for (Object arg : args) {
            subFilter.add(arg);
        }
        this.filter.add(subFilter);
        return this;
    }
    
    public Object[] build() {
        return filter.stream().map(list -> list.toArray()).toArray();
    }
    
    
}
