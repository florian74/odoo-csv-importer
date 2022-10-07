package caf.com.odooimporter.rpc;

import caf.com.odooimporter.config.Mapper;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OdooValue {
    public OdooModel model;
    
    public String field;
    
    public Object value;
}
