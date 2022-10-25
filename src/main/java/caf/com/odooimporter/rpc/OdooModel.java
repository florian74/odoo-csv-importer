package caf.com.odooimporter.rpc;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class OdooModel {
    
    private String name;
    
    // Object here is actually Map<String, Object>
    private Map<String, Object> allFields;
    
    private String dateFormat;
    
    public OdooModel(String name, Map<String, Object> allFields, String dateFormat) {
        this.name = name;
        this.allFields = allFields;
        this.dateFormat = dateFormat;
    }
    
    public String getName() {
        return name;
    }
    
    
    public Set<String> getRequiredField() {
        return allFields.entrySet().stream().filter(
                s -> {
                    try {
                        Map<String, Object> types = (Map<String, Object>) s.getValue();
                        return (Boolean) types.get("required");
                    } catch (Exception e) {
                        log.error("cannot get required field value", e);
                    }
                    return true;
                }
        ).map(s -> s.getKey()).collect(Collectors.toSet());
    }
    
    public Map<String, String> getTypes() {
        return allFields.entrySet().stream().map(
                s -> {
                    try {
                        Map<String, Object> types = (Map<String, Object>) s.getValue();
                        return new AbstractMap.SimpleEntry<>(s.getKey(), (String) types.get("type"));
                    } catch (Exception e) {
                        log.error("cannot get type", e);
                    }
                    return new AbstractMap.SimpleEntry<>(s.getKey(), "");
                }
        ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }


    public Object cast(String stringValue, String type) {
        
        if (stringValue == null) {
            return null;
        }
        
        try {
            switch (type) {
                case "char":
                case "text":   
                case "html":
                case "selection":
                    return stringValue;
                case "datetime":
                    SimpleDateFormat formater = new SimpleDateFormat(dateFormat);
                    return formater.parse(stringValue);
                case "boolean":
                    return Boolean.parseBoolean(stringValue);
                case "integer":
                case "many2one":
                case "many2one_reference":
                    try {
                        return Integer.parseInt(stringValue);
                    } catch (NumberFormatException e) {
                        return stringValue;
                    }
                case "float":
                    return Double.parseDouble(stringValue);
                case "binary":
                    log.info("not supported");
                    return null;
                  
                case "one2many":
                // many2many are actually typed Object[], guess this is the same for one2many
                case "many2many":
                    return new Object[]{Integer.parseInt(stringValue)};
                default:
                    log.info("no mapping found for type " + type);
                    return null;

            }
        } catch (Exception e) {
            log.info("an error occured while casting", e);
            return null;
        }


    }
    
    
    public Object getObject(String stringValue, String field) {
        return cast(stringValue, getTypes().get(field));
    }
    
    //handle manyToMany and one2Many conversion
    //adapt the type to Object[] or simplify to Object[] to Object according to field types
    //many2many -> object and object -> many2many
    public Object wrapMany2Many(Object objectValue, String refType, String fieldName) {
        
        String fieldType = getTypes().get(fieldName);
        if (fieldType == null) {
            throw new IllegalArgumentException("field " + fieldName + " is not in model " + name);
        }
        
        if (fieldType.equals(refType)) {
            return objectValue;
        }
        
        try {
            switch (refType) {
                case "one2many":
                case "many2many":
                    if ( ! "many2many".equals(fieldType) && ! "one2many".equals(fieldType)) {
                        Object[] values = (Object[]) objectValue;
                        return cast(values[0].toString(), fieldType);
                    }
                    return objectValue; // this can only be reach to convert one2many -> many2many
                
                default:
                    if ("many2many".equals(fieldType) || "one2many".equals(fieldType)) {
                        return new Object[]{objectValue};
                    }
                    return cast(objectValue.toString(), fieldType);
            }
        } catch (Exception e) {
            log.info("an error occured while adapting many2many", e);
            return null;
        }
    }
}
