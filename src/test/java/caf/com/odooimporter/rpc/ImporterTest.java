package caf.com.odooimporter.rpc;

import caf.com.odooimporter.config.Mapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ImporterTest {
    

    // ensure that order is respected using mapping order rules and naming rules
    @Test
    public void testPathPriority() {

        Mapper mapper = new Mapper();
        mapper.setNamings(List.of(
                new Mapper.Naming("res.partner", "*_provider.csv"),
                new Mapper.Naming("product.product", "*_product.csv"),
                new Mapper.Naming("product.supplierInfo", "*_product.csv"),
                new Mapper.Naming("pos.category", "*_category.csv")
        ));
        
        mapper.setOrders(
                List.of(
                        new Mapper.Model("pos.category"),
                        new Mapper.Model("res.partner"),
                        new Mapper.Model("product.product"),
                        new Mapper.Model("product.supplierInfo")
                )
        );
        mapper.setMappings(new ArrayList<>());
        
        
        Importer importer = new Importer(null, mapper, null, null);
        Set<Path> files = new HashSet<>();
        files.add(Path.of("toto_category.csv"));
        files.add(Path.of("coucou_product.csv"));
        files.add(Path.of("coucou_provider.csv"));
        files.add(Path.of("coucou_category.csv"));
        files.add(Path.of("ignoreMe_cat.csv"));
        files.add(Path.of("titi_category.csv"));
        
        List<Import> imports = importer.createImports(files);
        
        assert imports.size() == 6;
        
        assert "pos.category".equals(imports.get(0).getModel().getName());
        assert "pos.category".equals(imports.get(1).getModel().getName());
        assert "pos.category".equals(imports.get(2).getModel().getName());
        assert "res.partner".equals(imports.get(3).getModel().getName());
        assert "product.product".equals(imports.get(4).getModel().getName());
        assert "coucou_product.csv".equals(imports.get(4).getPathToFile().getFileName().toString());
        assert "product.supplierInfo".equals(imports.get(5).getModel().getName());
        assert "coucou_product.csv".equals(imports.get(5).getPathToFile().getFileName().toString());
        
        
    }
}
