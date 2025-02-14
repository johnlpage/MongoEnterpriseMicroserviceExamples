Probabilistic Data Generator
==============

build

` mvn clean package `

usage:

`java -jar DataGen.jsr inputDir docsToGenerate outputFile`

This is designed to Generate JSON data for testing.
It takes it definition from a directory of compressed CSV files.

It uses CSV to make it east to extract statistics from existing data sets using SQL or
MongoDB aggregation.

Each file in the directory contributes one or more fields to each generated documents.
Each file in the directory must have a column "probability" which is the relative probaility
of using the line in the file for the values.

The simplest csv file might be something like

```
"colour","probability"
"red",50
"green",25
"blue", 5
```

This would mean all documents will have a colour field, there will be 10 times as many
with red as blue, and five times as many with green as blue.

You can have more than one field in a file allowing you to correlate fields with a given probabiluty

```angular2html
"country","city","probability"
"UK","London",25
"USA","Washington DC",35
```

Fieldnames with dots (.) in them denote nested objects in the JSON

```angular2html
"vehicle.make","vehicle.model","probability"
"FORD","F150",100
```

Creates

```angular2html
{ 
  "vehicle" : {
     "make": "FORD",
     "model": "F150"
  }
}
```

There are Special values that start with @ you can use where a litteral is not what you need

```angular2html
"recordNumber","probability"
"@ONEUP",100
```

Would add a number which increases by one starting at 1

````
@INTEGER,@DOUBLE,@DATE,@DATETIME
@JSON
@ARRAY
