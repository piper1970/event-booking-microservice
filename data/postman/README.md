# Instructions for using Postman Templates

The template files in this directory will not work as is.  A number of variables in the file have placeholders
in them where the **KC_REALM** environment should be substituted.

In this directory (*data/postman*), two scripts, **insert_realm.sh** and **insert_realm.bat** have been added to do the substitution.

## Setting up Environment
Prior to running these scripts, KC_REALM must be loaded in the environment.

To ensure **KC_REALM** is loaded in the environment, see [Setting up environment](../../README.md#setting-up-environment-for-locally-run-microservices) in the README.md file in the root directory.

Make sure to run the command from that directory. 

## Making substitutions in template files
Once **KC_REALM** has been loaded into the environment, the following command should create the proper json files for all compose environments.

Make sure to run the command from the *data/postman* directory.

### For MacOS/Linux Users:

`for file in *.template; do file_clean=${file%.template}; ./insert_realm.sh ${file_clean}; done`.

### For Windows Users:

`for %f in (*.template) do @set "file_clean=%~nf" & call insert_realm.bat %file_clean%`

*Note: this hasn't been tested in Windows. The scripts and commands were formulated in Claude.ai 
based of the MacOS/Linux versions.*




