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

APP_SERVER_PORT     -> port to use
APP_SERVER_LOG_PATH -> path to log directory
```
After that the server can be run as a spring boot jar with java -jar build.jar


I usually use a shell function in bin folder that run contains all the needed command to run the project.

file structure: 

```
/home/odoo/importer
          /bin
              csv-importer-cli.sh
          /log
          /workspace
```

csv-importer-cli.sh:
```
#/bin/bash

odoo-importer() {
  case $1 in

   'update')
      echo 'updating odoo importer'
      odoo-importer 'env';
      odoo-importer 'stop';
      cd $APP_HOME;
      wget https://archiva.fp-ws.fr/releases/caf/com/odoo-importer/LATEST/odoo-importer-LATEST.jar --no-check-certificate
      mv odoo-importer-LATEST.jar $APP_JAR_NAME
      odoo-importer 'start';
      ;;
   'start')
      echo 'starting importer  server'
      odoo-importer 'env';
      cd $APP_HOME
      java -jar $APP_HOME/$APP_JAR_NAME &
      ;;
   'stop')
      odoo-importer 'env';
      export importer_pid=`ps aux | grep odoo-importer.jar | awk 'NR==1{print$2}' | cut -d' ' -f1`
      kill $importer_pid
      ;;
   'env')
      export APP_HOME=/home/odoo/importer
      export APP_JAR_NAME=odoo-importer.jar
      export APP_SERVER_LOG_PATH=$APP_HOME/log
      export APP_SERVER_PORT=7654

      export WORKING_DIR=$APP_HOME/workspace

      export MINIO_URL=s3.xxxxxxxx.com
      export MINIO_ACCESS_KEY=xxxxxx
      export MINIO_SECRET_KEY=xxxxxx
      export MINIO_BUCKET=xxxxxx


      export ODOO_HOST=odoo.xxxxxxxxx.com
      export ODOO_PORT=8069
      export ODOO_DATABASE=xxxx
      export ODOO_LOGIN=xxxxxxxx
      export ODOO_PASSWORD=xxxxxxx
      ;;
   *)
     echo 'available command'
     echo ' - start : run the app'
     echo ' - stop : stop the app'
     echo ' - env: set the env'
     ;;
   esac
}

export -f odoo-importer


```

Running the first time would then be like: 
```
source ./csv-importer-cli.sh
odoo-importer update
```

When the jar is downloaded you also can go with start and stop
```
source ./csv-importer-cli.sh
odoo-importer start
odoo-importer stop
```

You can even go further and register the source command to your bash_profile.


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
                log.info("fields:  " + fields.toString() );

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
  namings:
    - model: "pos.category"
      template: "*_category.csv"
    - model: "res.partner"
      template: "*_provider.csv"
    - model: "product.product"
      template: "*_product.csv"
    - model: "product.supplierinfo"
      template: "*_product.csv"  
    - model: "stock.move"
      template: "*_product.csv"
    
  #execution order of model importation, all partner, then all product, then all stock  
  orders:
    - name: "pos.category"
    - name: "res.partner"
    - name: "product.product"
    - name: "product.supplierinfo"
    - name: "stock.move" 

  mappings:
    - model: "product.product"                 # model name to map
      fields:                                  # list of fields to map
        - name: "id"                           # there should always be an id field
          header: "ID"
          target: 
              - model: "ir.model.data"
                field: "name"
                ref: "res_id"
        - name: "pos_categ_id"                 # field name that can be found in <model>.txt
          header: "Categorie du point de vente"                   # header in csv file provided, for multiple match, separated by "," first will win
          target: 
              - model:  "pos.category"           #target model
                field:  "name"                   #field that should match csv data
                ref:    "id"                     #value to use in mapping 
        - name: "categ_id"                 # field name that can be found in <model>.txt
          header: "FAMILLE"                   # header in csv file provided, for multiple match, separated by "," first will win
          target:
              - model: "product.category"           #target model
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
             - model: "account.tax"
               field: "name"
               ref: "id"
        - name: "available_in_pos"
          header: "Disponible dans le PdV"
          set: "true"
        - name: "detailed_type"
          header: "Type d'article"
          set: "product"
        - name: "description"
          header: "DESCRIPTION"
        - name: "barcode"
          header: "CODE_BARRE"

    - model: "res.partner"
      fields:
        - name: "id"
          header: "id"
          target:
            - model: "ir.model.data"
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
        - name: "parent_id"
          header: "Parent"
          target:
            - model: "pos.category"
              field: "name"
              reF: "id"
        - name: "display_name"
          header: "Categorie du point de vente"  
        - name: "id"
          header: "id"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"
 
    - model: "product.supplierinfo"
      fields:
        - name: "min_qty"
          header: "STOCK"
        - name: "id"
          header: "ID FOURNISSEUR"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"      
        - name: "product_id"
          header: "ID"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"
        - name: "name"
          header: "FOURNISSEUR"
          target:
            - model: "res.partner"
              field: "email"
              ref: "id"
        - name: "product_tmpl_id"
          header: "ID"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"
            - model: "product.product"
              field: "id"
              ref: "product_tmpl_id" 
    
    - model: "stock.move"
      fields:
        - name: "product_id"
          header: "ID"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id" 
        - name: "product_tmpl_id"
          header: "ID"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"
            - model: "product.product"
              field: "id"
              ref: "product_tmpl_id"
        - name: "name"
          header: "NOM" 
        - name: "procure_method"
          set: "make_to_stock"
        - name: "product_uom"
          set: "Unités"
          target:
            - model: "uom.uom"
              field: "name"
              ref: "id"
        - name: "product_uom_qty"
          header: "STOCK"
        - name: "quantity_done"
          header: "STOCK"
        - name: "availability"
          header: "STOCK"
        - name: "state"
          set: "done"
        - name: "move_lines_count"
          set: "1"
        - name: "is_inventory"
          set: "true"
        - name: "location_id"
          set: "Inventory adjustment"
          target:
            - model: "stock.location"
              field: "name"
              ref: "id"
        - name: "location_dest_id"
          set: "Stock"
          target:
            - model: "stock.location"
              field: "name"
              ref: "id"
        - name: "id"
          header: "ID STOCK"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"   
```


Let's explain a bit further:

##namings rules: 

this rules allow a user to associate a file name with an odoo model
for instance if the file to import is related to category, you might call it my_category_to_import.csv
then you will declare in namings section that there will be a mapping between your csv file and the pos.category model

##orders rules:

this rules declare the order of the successive imports that will occur.
The reason is simple, if a value is not found, it will not be added. So use the orders rules to be sure 
the items exists at the right time for the the right model

##mappings rules:

this rules declare how a model will be map.
this is a list of all model mapping that can be declared.
If you associated a file to a model but do not declare mapping, nothing will be done.

contains the model name and the fields to map.

###fields

fields can have up to 4 attributes, name, header, set and target.

NAME - is the model field name, the one that exist in odoo. 
A lot of model field can be found in src/main/resources files

HEADER - is the CSV header to be map with. This should exist in the file. If not value will be null

SET - is the default value. This is a string value, all defined mapping are string values, let the system find the types for you 

TARGET - is an advance options that will be detailed right after.


###target

Target is a list of object that contains 3 attributes. 

model  - the model to look for the value

field  - the field that should match the csv value or previous target value

ref    - the field that we should keep as final value


for instance let's analyse the pos.category mapping

```

    - model: "pos.category"
      fields:
        - name: "name"
          header: "Categorie du point de vente"
        - name: "parent_id"
          header: "Parent"
          target:
            - model: "pos.category"
              field: "name"
              reF: "id"
        - name: "display_name"
          header: "Categorie du point de vente"  
        - name: "id"
          header: "id"
          target:
            - model: "ir.model.data"
              field: "name"
              ref: "res_id"
```

first field is the name of the category. The value is taken from CSV header that match the string 
"Categorie du point de vente" and that's all

second field is parent_id. But odoo model require a real category id of its database, which we cannot know
BUT the CSV header can provide the name field of this category.
The target rule is used, and describe the fact that we are looking in the pos.category model a record that match the name given by our csv,
and we are looking for id field

third field is id. "id" field is mandatory. We need to use the concept of external id. This concept allow the importer
to define a string that identify the record in database. So that if you provide it twice, second call will trigger an update and 
not a creation request. That is ir.model.data does.
so we provide the given external id in csv file and make it match with the name of the recorde ir_model_data and get the res_id that is the odoo id

NB: use one external id per model, you can use one file that contains as many generated id as the number of models it implies
if you try to import model with identical external id but different model, resulting behavior can be chaotics.
You may lose the update possibilities.

Target is a list and can repeat. And the next target will be computed using the previous target value as input.

The concept of imbricated target, produce the value actually like this pseudo code:
```
   value := default value //SET RULE
   value = CSV.get(Header) //HEADER RULE
   for all model_request in target {    //list of (MODEL, FIELD, REF)
      value = SELECT model_request.ref FROM model_request.model WHERE [model_request.field]=value
   }
   return value
```

Current mapping is able to:
- create new category
- create new partner
- create product and associate to category and partner
- add stock movement to adjust quantity of created products




