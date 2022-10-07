package caf.com.odooimporter.config;


import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "configuration")
@PropertySource(value = "classpath:mapping.yaml", factory = YamlFactory.class)
@ToString
public class Mapper {

    List<Naming> namings = new ArrayList<>();
    
    List<Model> orders = new ArrayList<>(); 
    
    List<Mapping> mappings = new ArrayList<>();

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
        List<Field> fields = new ArrayList<>();

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Field {
        String name;                                // name of the model field
        String header;                              // name of the csv header
        List<Target> target = new ArrayList<>();    // list of successive mapping
        String set;                                 // default value to use
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
    }
    
}