#Instaructions for Mac, Linux will be similar

Install myxql and configure seucrity
```
brew install mysql
brew services start mysql
mysql_secure_installation
```

Login to test
``
mysql -u root -p
```


I then use `sh loaddata.sh` to load in the samepl data - the source filenames do change sometimes so this might need a little adjustment.

I then download and build  MongoSyphon [https://github.com/johnlpage/MongoSyphon] - this is a tool to convert tables to documents recursively and efficently. The Jar is incluced here for loading or updating in MongoDB if required.

```
java -jar MongoSyphon.jar -c mot_syphon_conf.json 
```




