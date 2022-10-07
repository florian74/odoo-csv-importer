package caf.com.odooimporter.rpc;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OdooValue {
    public OdooModel model;
    
    public String field;
    
    public Object value;
}
