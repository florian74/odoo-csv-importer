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
import java.util.stream.IntStream;

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
        log.debug("headers: " + header.stream().collect(Collectors.joining(",")));
        
        // key: DB, value: index in array in column file
        Map<String, Integer> headerMap = new HashMap<>();
        mapping.getFields().stream().forEach(field -> {
            headerMap.put(field.getName(), header.indexOf(field.getHeader()));
        });
        headerMap.values().removeIf(index -> (index == -1));  // if some header are not found but mapping actually set default value, it's still fine
        log.debug("headers Map: " + headerMap.keySet().stream().collect(Collectors.joining(",")));


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
                        // create object
                        OdooObject toImport = new OdooObject();
                        toImport.model = model;
                        
                        // set default value (set params in fields)
                        mapping.getFields().forEach(field -> {
                            if (field.getSet() != null) {
                                toImport.values.put(field.getName(), field.getSet());
                            }
                        });

                        // load string value to object according to header map
                        IntStream.range(0, line.size()).forEach(idx -> {
                            String csvHead = header.get(idx);
                            mapping.getFields().stream()
                                    .filter(field -> csvHead.equals(field.getHeader()))
                                    .forEach(field -> {
                                                log.info("found " + field.getName() + " ,value " + line.get(idx) + " header: " + field.getHeader());
                                                toImport.values.put(field.getName(), line.get(idx));
                                            }
                                    );
                        });
                        
                        log.info(toImport.values.toString());
                        
                        mapping.getFields().forEach(field -> {
                            Object value = getValue(toImport, field, allModels);
                            log.info("found " +  value +  ", for field " + field.getName());
                            toImport.values.put(field.getName(), value);
                        });
                        toImport.values.values().removeIf(Objects::isNull);

                        // check existence
                        Response existenceRequest = command.searchObject(model.getName(), new Filter().add("id", "=", toImport.values.get("id") ).build());
                        toImport.exist = existenceRequest.getResponseObjectAsArray().length == 1; // id has been replaced

                        if (! toImport.exist) {

                            
                            // create if not exist
                            toImport.values.remove("id");
                            log.info("creating object, with values " + toImport.values.entrySet().stream().map(a -> a.toString()).collect(Collectors.joining(",")));
                            Integer newId = (Integer) command.createObject(model.getName(), toImport.values);
                            
                            // also create or update reference id if used
                            // This assume that id target always is external id
                            Map<String, Object> internalIdsValues = new HashMap<>();
                            internalIdsValues.put("module", "__import__");
                            internalIdsValues.put("model", model.getName());
                            internalIdsValues.put("name", line.get(idIndex));
                            internalIdsValues.put("res_id", newId);
                            String externalModel = "ir.model.data";

                            // check existence of external id
                            Response externalIdList = command.searchObject(externalModel,  new Filter().add("name" , "=", line.get(idIndex)).build());
                            if (externalIdList.getResponseObjectAsArray().length == 1) {
                                Integer id = (Integer) externalIdList.getResponseObjectAsArray()[0];
                                command.writeObject(externalModel, id, internalIdsValues);
                            } else {
                                command.createObject(externalModel, internalIdsValues);
                            }
                        } else {
                            // update
                            log.info("updating object with values " + toImport.values.entrySet().stream().map(a -> a.toString()).collect(Collectors.joining(",")));
                            command.writeObject(model.getName(), (Integer) toImport.values.get("id"), toImport.values);
                            
                        }
                       
                } catch (Exception e) {
                    log.error("cannot import line", e);
                }
            }
        );

    }
    
    // OdooObject is an object that contains all default values as STRING.
    // This function return the processed object representation of the string.
    // It follows eihter target rules of simply cast the object to the right type
    public Object getValue(OdooObject odooObject, Mapper.Field field, List<OdooModel> allModels) {
        
        // when no target is indicated
        // import directly field when it target directly the right model
        String stringValue = (String) odooObject.values.get(field.getName());
        if (field.getTarget() == null || field.getTarget().size() == 0) {
            return model.getObject(stringValue, field.getName());
        }
        
        // when a target is indicated
        // copy the ref field, following the model search over value, field and model 
        // do nothing if it does not exist
        // I consider you should have had done the import in a previous file
        // You could also reuse the same file for an other mapping. (But if you do this, use one generated external ids column per model)
        OdooValue initValue = new OdooValue(null, null, stringValue);
        OdooValue resultValue = field.getTarget().stream().reduce(initValue ,(previous, target) -> {
            
            log.info("target is : " + target.toString() + " from value " + previous.value + " in field " + field.getName());
            OdooModel targetModel = allModels.stream().filter(model1 -> model1.getName().equals(target.getModel())).findFirst().orElse(null);
            
            // cast previous value to new field type
            // handle many to many object formatting
            // we assume that the transition is from coherent object type
            Object finalValue;
            if (previous.model != null) {
                finalValue = targetModel.wrapMany2Many(previous.value, previous.model.getTypes().get(previous.field), target.getField());
            } else {
                finalValue =  targetModel.getObject((String) previous.value, target.getField());
            }
            
            try {
                Response listObject = command.searchObject(
                        target.getModel(),
                        new Filter().add(target.getField() , "=", finalValue).build()
                );

                if (listObject.getResponseObjectAsArray().length > 0) {
                        if (target.getRef() != null ) {
                            String[] refFields = new String[1];
                            refFields[0] = target.getRef();
                            log.info("model: " + targetModel.getName() + " ,field: " + target.getRef() + ", ids: " + Arrays.stream(listObject.getResponseObjectAsArray()).findFirst().orElse(null));
                            Object[] readResponse = command.readObject(targetModel.getName(), listObject.getResponseObjectAsArray(), refFields);
                            finalValue = ((Map<String, Object>) readResponse[0]).get(target.getRef());
                            log.info("found: " + finalValue.toString());
                        }
                } else {
                    log.info("No value found for field " + target.getField() + " , value will be empty");
                }

            } catch (XmlRpcException e) {
               log.error("XML RPC exception", e);
            }

            log.info("value " + finalValue + ", model " + targetModel.getName() + " , field " + target.getRef());
            return new OdooValue(targetModel, target.getRef(), finalValue);

        }, (accumulation, newbie) -> newbie);
        // handle many to many object formatting
        if (resultValue.value == null) {
            log.info("cannot found field " + field.getName() + " for model " + model.getName() + "using base " + stringValue);
            return null;
        }
        return model.wrapMany2Many(resultValue.value, resultValue.model.getTypes().get(resultValue.field), field.getName());
    }

}
