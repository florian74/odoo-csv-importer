# odoo-csv-importer
Spring boot server with a cron task that import csv files from minio to odoo according to mapping configuration


# PURPOSE

Import any csv file with a cron task that runs according to configuration, to reproduce what can be done on the odoo import page. 
Mapping can be provided as input so that it relies only on configuration.
The file can be taken either from a directory, or from minio's S3

# RUNNING

The server run with different environment variable to be provided.

```
if you are using minio: 
MINIO_URL        -> minio url without https://
MINIO_ACCESS_KEY -> minio user to use;
MINIO_SECRET_KEY -> minio user password;
MINIO_BUCKET     -> minio target bucket;

directory where file are downloaded and read
WORKING_DIR     -> path to working directory, idealy ensure owner is the same as the user who run the server

odoo data
ODOO_PORT=8069; -> minio rpc port, default is 8069
ODOO_DATABASE   -> odoo database name
ODOO_PASSWORD   -> odoo password, odoo 15, api key seems not to work so use a dedicated user password
ODOO_LOGIN      -> odoo login
```
After that the server can be run as a spring boot jar with java -jar build.jar
This part is coming soon, I didn't deployed it yet.

# DEPENDENCY

The dependency uses a odoo java jar from my own repository.
I did just build the odoo-java-api jar and put it on a other repository because they did'nt tag the latest version for odoo 15

# CONFIGURATION

application.properties and mapping.yaml are the configuration file to use and they can be copied and provided to the jar command
with -cp path2Application.properties:path2mapping.yaml
Spring always use the last file found in the classpath.

The mapping required some knowledge of the model concept.
Each data in odoo is store in a model that actually is a sql table.
I have create files in src/main/resources that contains the models data, I needed for my case. 
The file ir.model.txt list all possible models
Other .txt files carry the name of their model and list the field available for each model.

You can get models name by requiring the ir.model. And you can get fields detail using the getField command in the java client.
The exemple of java code below list all field of the "product.supplierInfo" model, then display the data of this table.
(this is the tab in products where you can add supplier details in odoo front end)
All models has an Id, The odoo concept of External Id, used for csv import is actually hiddent in ir.model.data. 
This model contains the mapping between a model Id and a human readable string


```
 try {
                Map<String, Object> fields = service.getFields("product.supplierinfo", new String[]{});
                log.info("fields from tax " + fields.toString() );

                Response p = service.searchObject("product.supplierinfo", new Object[]{});
                Object[] q = service.readObject("product.supplierinfo", p.getResponseObjectAsArray(), fields.keySet().toArray(new String[0]));
                Arrays.stream(q).forEach(obj ->
                        log.info(((Map<String, Object>) obj).toString())
                );
                
     
            } catch (Exception e) {
                log.error("execption occured", e);
            }
```


In the mapping you will consider models first. 
And then assign for each field of the csv the value you want.
the target rule hepls you to get a row from a model and then find the field you are looking for.

this is a mapping exemple: 

```configuration:
  #assign file to  model according to their name
  #define in this section the mapping between the name and the models to use
  namings:
    - model: "pos.category"
      template: "*_category.csv"
    - model: "res.partner"
      template: "*_provider.csv"
    - model: "product.product"
      template: "*_product.csv"
    
  #define in this section the execution order of model importation, for instance: all category, then all partner, then all product  
  orders:
    - name: "pos.category"
    - name: "res.partner"
    - name: "product.product"
    
  #model config mapper
  mappings:
    - model: "product.product"                 # model name to map
      fields:                                  # list of fields to map
        - name: "id"                           # there should always be an id field
          header: "ID"
          target: 
              model: "ir.model.data"
              field: "name"
              ref: "res_id"
        - name: "pos_categ_id"                 # field name that can be found in <model>.txt
          header: "Categorie du point de vente"                   # header in csv file provided, for multiple match, separated by "," first will win
          target: 
              model:  "pos.category"           #target model
              field:  "name"                   #field that should match csv data
              ref:    "id"                     #value to use in mapping 
        - name: "categ_id"                 # field name that can be found in <model>.txt
          header: "FAMILLE"                   # header in csv file provided, for multiple match, separated by "," first will win
          target:
              model: "product.category"           #target model
              field: "name"                   #field that should match csv data
              ref: "id"                     #value to use in mapping    
        - name: "price"                
          header: "Prix de vente"
        - name: "standard_price"
          header: "PRIX_HT"
        - name: "name"
          header: "NOM"
        - name: "taxes_id"
          header: "Taxes a la vente"
          target:
             model: "account.tax"
             field: "name"
             ref: "id"
        - name: "available_in_pos"
          header: "Disponible dans le PdV"
        - name: "detailed_type"
          header: "Type d'article"  
        - name: "description"
          header: "DESCRIPTION"
        - name: "barcode"
          header: "CODE_BARRE"

    - model: "res.partner"
      fields:
        - name: "id"
          header: "id"
          target:
            model: "ir.model.data"
            field: "name"
            ref: "res_id"
        - name: "email"
          header: "Courriel"
        - name: "name"
          header: "Nom"
        - name: "street"
          header: "Rue"
        - name: "comment"
          header: "Notes"
             
    - model: "pos.category"
      fields:
        - name: "name"
          header: "Categorie du point de vente"
        - name: "id"
          header: "id"
          target:
              model: "ir.model.data"
              field: "name"
              ref: "res_id"```





