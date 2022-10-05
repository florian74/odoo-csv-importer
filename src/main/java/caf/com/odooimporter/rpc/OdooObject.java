package caf.com.odooimporter.rpc;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OdooObject {
    
    public OdooModel model;
    
    public Map<String, Object> values = new HashMap<>();
    
    public Boolean exist = false;
    
}
