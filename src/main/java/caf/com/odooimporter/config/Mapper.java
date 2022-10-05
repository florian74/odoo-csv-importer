package caf.com.odooimporter.config;


import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "configuration")
@PropertySource(value = "classpath:mapping.yaml", factory = YamlFactory.class)
@ToString
public class Mapper {

    List<Naming> namings;
    
    List<Model> orders; 
    
    List<Mapping> mappings;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Naming {
        String model;
        String template;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Model { 
        String name;
    }
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Mapping {

        String model;
        List<Field> fields;

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Field {
        String name;
        String header;
        Target target;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Target {
        String model;
        String field;
        String ref;
        String override;
    }
    
}