package caf.com.odooimporter.rpc;

import caf.com.odooimporter.config.Mapper;
import caf.com.odooimporter.reader.FileReader;
import com.odoojava.api.OdooCommand;
import com.odoojava.api.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlrpc.XmlRpcException;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
public class Import {
    
    Path pathToFile;           // path to file to import
    OdooCommand command;       // odoo connector
    OdooModel model;           // import model to use
    
    public Import(Path pathToFile, OdooCommand command, OdooModel model) {
        this.pathToFile = pathToFile;
        this.command = command;
        this.model = model;
    }

    public void perform(Mapper mapper, List<OdooModel> allModels, FileReader reader) {

        List<List<String>> values = reader.readAll(pathToFile.toFile());
        log.debug(values.toString());
        
        Mapper.Mapping mapping = mapper.getMappings().stream().filter(m -> model.getName().equals(m.getModel())).findFirst().orElse(null);
        log.debug(mapper.toString());
        
        if (values.size() == 0) {
            throw new IllegalArgumentException("file is empty");
        }
        if (mapping == null) {
            throw new IllegalArgumentException("model " + mapping.getModel() + "  has no mapping define, please review yaml config mappings section.");
        }
        
        // read csv header
        List<String> header = values.get(0);
        log.info("headers: " + header.stream().collect(Collectors.joining(",")));
        
        // key: DB, value: index in array in column file
        Map<String, Integer> headerMap = new HashMap<>();
        mapping.getFields().stream().forEach(field -> {
            headerMap.put(field.getName(), header.indexOf(field.getHeader()));
        });
        log.info("headers Map: " + headerMap.keySet().stream().collect(Collectors.joining(",")));


        // find id header
        Integer idIndex = headerMap.get("id");
        Mapper.Field idField = mapping.getFields().stream().filter(f -> "id".equals(f.getName())).findFirst().orElse(null);
        
        if (idField == null) {
            throw new IllegalArgumentException("No id field is define in mapping");
        }
        
        // By line: check existence
        values.subList(1, values.size()).stream().forEach(
            line -> {
                try {
                        OdooObject toImport = new OdooObject();
                        toImport.model = model;
                    
                        // check value existence (external Ids or id)
                        Boolean exist = false;
                        if (idField.getTarget() == null) {
                            Response r = command.searchObject(model.getName(),  new Filter().add("id" , "=", Integer.valueOf(line.get(idIndex))).build());
                            if (r.getResponseObjectAsArray().length == 1) {
                                exist = true;
                            }
                        } else {
                            Response r = command.searchObject(idField.getTarget().getModel(),  new Filter().add(idField.getTarget().getField() , "=", line.get(idIndex)).build());
                            if (r.getResponseObjectAsArray().length == 1) {
                                exist = true;
                            }
                        }
                        toImport.exist = exist;
                        
                        
                        // get all values
                        for (int i=0; i<line.size(); i++) {
                            String csvHead = header.get(i);
                            Mapper.Field field = mapping.getFields().stream().filter(f -> f.getHeader().equals(csvHead)).findFirst().orElse(null);
                            
                            Object objectValue;
                            
                            if (field == null) {
                                log.info("no mapping found for csv header " + csvHead);
                                continue;
                            }
                            if (model.getTypes().get(field.getName()) == null) {
                                log.info("field " + field.getName() + " does not exist in model " + model);
                            }
                            
                            // when no target is indicated
                            // import directly field when it target directly the right model
                            if (field.getTarget() == null) {
                                objectValue = model.getObject(line.get(i), field.getName());
                                toImport.values.put(field.getName(), objectValue);
                            }
                            // when a target is indicated
                            // copy the ref field, following the model search over value, field and model 
                            // do nothing if it does not exist
                            // I consider you should have had done the import in a previous file
                            // You could also reuse the same file for an other mapping. (But if you do this, use one generated external ids column per model)
                            else {
                                Mapper.Target fieldTarget = field.getTarget();
                                OdooModel targetModel = allModels.stream().filter(model1 -> model1.getName().equals(fieldTarget.getModel())).findFirst().orElse(null);
                                Response r = command.searchObject(
                                        field.getTarget().getModel(),
                                        new Filter().add(fieldTarget.getField() , "=", targetModel.getObject(line.get(i), fieldTarget.getField())).build()
                                );
                                if (r.getResponseObjectAsArray().length > 0) {
                                    
                                    if (fieldTarget.getRef() != null ) {
                                        
                                        String[] refFields = new String[1];
                                        refFields[0] = fieldTarget.getRef();
                                        log.trace("model: " + targetModel.getName() + " ,field: " + fieldTarget.getRef() + ", ids: " + Arrays.stream(r.getResponseObjectAsArray()).findFirst().orElse(null));
                                        Object[] readResponse = command.readObject(targetModel.getName(), r.getResponseObjectAsArray(), refFields);
                                        objectValue = ((Map<String, Object>) readResponse[0]).get(fieldTarget.getRef());
                                        log.trace("found: " + objectValue.toString());
                                        
                                        // handle many to many object formatting
                                        objectValue = model.wrapMany2Many(objectValue, targetModel.getTypes().get(fieldTarget.getRef()), field.getName());
                                        
                                        // import only when a value is found
                                        toImport.values.put(field.getName(), objectValue);
                                    }
                                } else {
                                    log.info("No value found for field " + field.getName() + " , value will be empty");
                                }
                            }
                            
                        }

                        
                        if (! exist) {

                            // create if not exist
                            log.info("creating object, with values " + toImport.values.entrySet().stream().map(a -> a.toString()).collect(Collectors.joining(",")));
                            Integer newId = (Integer) command.createObject(model.getName(), toImport.values);
                            
                            // also create reference id if used
                            if (idField.getTarget() != null) {
                                Map<String, Object> internalIdsValues = new HashMap<>();
                                internalIdsValues.put("module", "__import__");
                                internalIdsValues.put("model", model.getName());
                                internalIdsValues.put("name", line.get(idIndex));
                                internalIdsValues.put("res_id", newId);
                                
                                command.createObject(idField.getTarget().getModel(), internalIdsValues);
                            }
                        } else {
                            // update
                            command.writeObject(model.getName(), (Integer) toImport.values.get("id"), toImport.values);
                        }
                       
                } catch (Exception e) {
                    log.info("cannot import line", e);
                }
            }
        );

    }

}
