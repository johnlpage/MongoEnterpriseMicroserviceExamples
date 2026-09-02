Probabilistic Data Generator
==============

build

` mvn clean package `

usage:

`java -jar DataGen.jsr inputDir docsToGenerate outputFile [batchSize] [oneupStart] [randomSeed]`

`batchSize` is optional and defaults to 2000. It controls how many documents are held in
memory at once before being written and released.

`oneupStart` is optional and defaults to 0. It sets the initial value of the `@ONEUP`
counter (see below) instead of starting at 0. This is intended for running multiple
instances of the generator in parallel against the same input directory - each instance is
given a different, non-overlapping `oneupStart` (e.g. 0, 1000000, 2000000, ...) so that
`@ONEUP` values such as a listing ID do not collide across the output files. Note that all
`@ONEUP` fields in a given run share a single counter, so a document with two `@ONEUP`
fields advances the counter by two per document, not one.

`randomSeed` is optional and defaults to 0, giving repeatable output across runs. When
running multiple instances in parallel a different `randomSeed` should be given to each
instance in addition to a different `oneupStart`; otherwise every instance draws the same
sequence of random field values and the parallel runs differ only in their `@ONEUP` fields.

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
