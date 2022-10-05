package caf.com.odooimporter.rpc;

import caf.com.odooimporter.config.Mapper;
import caf.com.odooimporter.reader.FileReader;
import com.odoojava.api.OdooCommand;
import com.odoojava.api.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlrpc.XmlRpcException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class Importer {

    OdooCommand service;    // connector
    Mapper mapper;          // yaml config for mapping
    List<OdooModel> models; // all model in use in this mapping
    FileReader reader;      // file reader
    String dateFormat;      // dateFormat
    
    public Importer(@Autowired Connector connector,
                    @Autowired Mapper mapper,
                    @Autowired FileReader fileReader,
                    @Value("${csv.datePattern}") String dateFormat) 
    {
        this.mapper = mapper;
        this.reader = fileReader;
        this.dateFormat = dateFormat;
        if (connector != null && connector.getIsOk()) {
            this.service = new OdooCommand(connector.getSession());
        }
        // load model info
        this.models = getModels();
       
    }
    
    // Create all import taks and run them
    public void importAll(Set<Path> paths) {
        
        if (service == null) {
            log.error("service is not ready, nothing will be done");
            return;
        }
        
        // read Loop
        createImports(paths).stream().forEach(
                i -> {
                    try {
                        log.info("importing " + i.getPathToFile().toString());
                        i.perform(mapper, models, this.reader);
                        log.info(i.getPathToFile().toString() + "successfully imported successfully");
                    } catch (Exception e) {
                        log.info("Could not import file " + i.getPathToFile().toString() + " an exception occured", e);
                    }
                    
                }
        );
    }
    
    public List<Import> createImports(Set<Path> paths) {
        // create imports tasks
        List<Import> imports = paths.stream()
        .flatMap(
            path -> this.mapper.getNamings().stream().flatMap(
                naming -> Arrays.stream(naming.getTemplate().split(",")).map(
                        tpl -> {
                            Pattern pattern = Pattern.compile(tpl.replaceAll("\\*", ".*"));
                            Matcher matcher = pattern.matcher(path.toString());
                            if (matcher.matches()) {
                                return new Import(path, service, findModel(naming.getModel()));
                            }
                            return null;
                        }
                )
            )
        )
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

        log.trace("imports: " + imports.stream().map(Import::getModel).map(OdooModel::getName).collect(Collectors.joining(",")));
        log.trace("order: " + mapper.getOrders().stream().map(o -> o.getName()).collect(Collectors.joining(",")));
        
        // reorder imports task according to mapping configuration
        return this.mapper.getOrders().stream()
            .map(o -> o.getName())
            .flatMap(model -> imports.stream().filter(i -> i.getModel().getName().equals(model)))
            .filter(Objects::nonNull).collect(Collectors.toList());
    }
    
    // get all model and submodels
    public List<OdooModel> getModels() {
        
            List<String> additionalModels = mapper.getMappings().stream().filter(
                    mapping -> mapping.getFields() != null
            ).map(Mapper.Mapping::getFields)
                    .flatMap(f -> f.stream())
                    .filter(f -> f.getTarget() != null)
                    .map(f -> f.getTarget().getModel())
                    .distinct().collect(Collectors.toList());
            
            List<String> mapperModel = mapper.getNamings().stream().map(Mapper.Naming::getModel).collect(Collectors.toList());
        
            return Stream.concat(additionalModels.stream(), mapperModel.stream())
                    .map(
                            model -> {
                                Map<String, Object> f = null;
                                try {
                                    if (service != null ) {
                                        f = service.getFields(model, new String[0]);
                                    }
                                    return new OdooModel(model, f, dateFormat);
                                } catch (XmlRpcException e) {
                                    e.printStackTrace();
                                }
                                return null;
                            }
                    ).filter(Objects::nonNull)
                    .collect(Collectors.toList());
    }
    
    // find one specific model in list
    public OdooModel findModel(String name) {
        return models.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }
    
  
}
