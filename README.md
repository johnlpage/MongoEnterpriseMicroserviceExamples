# MongoDB Enterprise Microservice Examples (Memex)

## Mews

***More Documentation in the README in the memex Subdirectory***

This Project contains a set of example of Enterprise
Microservices using Java, Spring Boot , Spring Data MongoDB and
native MongoDB capabilities.

It is freely licensed using Apache 2 to allow you to take the code and
do whatever you want with it.

It's been provided with a Web Service / REST Controller to
make it easy to access and test and learn from but this can easily be replaces with
Kafka or Batch based processing. Several of the services
are designed to work with streams.

It also includes a simple frontend. Unlike the services code the frontend this is _not_ intended as any sort of howto
example, it's as simplistic as possible and is purely to allow you to experiment
without having
to use cURL for querying.

The Services included are

* Bulk Load / Update JSON
* Bulk Load / Update JSON with History
* Pre and Post write Triggers
* Basic CRUD Operations
* Query including Query Costing engine
* Atlas Search
* Launch Preflight (Check for indexes, constraints etc.)
* Bulk streaming output

## DataGen

A statistical/probabilistic based data generator used to generate
JSON data statistically similar to a source data set.

## SAMPLE_DATA

This was developed using the UK VOSA MOT Dataset ( 15 years
of motor vehicle annual inspections). This cdata is available
as CSV with scripts to load it into MySQL. This directory has
scripts to download the data, load it into MySQL ( I have Postgres
versions somewhere too) and then a config for MongoSyphon to
convert it from tabular to document format. This all is now an
exercise for the reader as DataGen produces near identical data.

