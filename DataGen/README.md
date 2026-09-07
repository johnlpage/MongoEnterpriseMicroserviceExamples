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

Would add a number which increases by one starting at 1. Note the counter is shared
per generator run: if a single document has two `@ONEUP` fields (or one is nested inside
an `@ARRAY` sub-generator), each occurrence advances the same counter, so it increases by
two per document rather than one.

### Special (`@...`) value reference

All of these are only recognised in a CSV cell - they are ordinary literal strings
everywhere else. Arguments are given in parentheses, comma-separated.

- **`@ONEUP`** - no arguments. Auto-incrementing counter, starts at 0 (or at the
  `oneupStart` command-line argument) and increases by 1 each time it is evaluated.

- **`@INTEGER(from,to)`** - a random whole number, inclusive of both `from` and `to`.
  Example: `@INTEGER(1,6)` simulates a die roll.

- **`@DOUBLE(a,b)`** - a random floating point number between `a` and `b`. The two
  bounds can be given in either order (largest first or smallest first) - the result
  will always fall between the smaller and larger of the two.

- **`@DATE(startDate,endDate)`** - a random calendar date (no time component) between
  the two dates inclusive, formatted `YYYY-MM-DD`, e.g. `@DATE(2022-01-02,2022-12-31)`.

- **`@DATETIME(startDateTime,endDateTime)`** - a random date/time between the two
  ISO-8601 local date-times, e.g. `@DATETIME(2022-01-01T00:00:00,2022-12-31T23:59:00)`.
  Note: despite the name, the generator currently only randomises by whole minutes
  within the number of *days* between the two bounds, and it is written out in
  `YYYY-MM-DD` date-only format (the time-of-day portion is calculated but not
  emitted) - treat this field as date-precision only, not true datetime precision.

- **`@JSON({...})`** - embeds a literal, hand-written JSON object as the value of this
  field. Useful for fixed nested sub-documents where you don't need per-field
  randomisation, or where you want to enumerate a small number of realistic whole
  objects (each CSV row is itself a "probability" weighted alternative, so you can give
  several different `@JSON(...)` rows in the same file to pick between several
  hand-authored sub-documents). Because the value is placed inside a CSV cell, JSON
  double-quotes inside it must be escaped by doubling them (standard CSV quoting), e.g.
  `"@JSON({""colour"":""red""})"`. Parsed JSON is cached internally (keyed by a hash of
  the raw text) so repeating the same literal across many rows/documents is cheap.

- **`@ARRAY(subdirectory,n)`** - generates an array of `n` sub-documents, each one
  produced by running the *entire* generator recursively against the CSV files found in
  `subdirectory` (a directory nested inside the current input directory, containing its
  own set of CSV files following all the same rules described in this document,
  including further nested `@ARRAY`s if needed). Use this for variable-length embedded
  arrays such as line items, history entries, or failed-test details. Example:
  `"@ARRAY(faileditems,19)"` produces an array of 19 documents built from
  `<inputDir>/faileditems/*.csv[.gz]`.

### Special field name: `ROOT`

If a column is named `ROOT` (instead of a normal field name or dotted path), its
generated value (which must be a JSON object, typically via `@JSON(...)`) *replaces*
the entire document being built at that point, rather than being nested as a field
inside it. This is only really useful combined with `@ARRAY`, to generate an array of
sub-documents whose shape is not a wrapper object but literally the object itself.

### How multiple CSV files combine into one document

Every `.csv` or `.csv.gz` file found directly inside the input directory represents one
independently-chosen "slice" of the output document. For each document generated, the
tool:

1. Picks one row from each file in the directory, independently, weighted by that
   file's own `probability` column (higher `probability` values relative to the sum of
   all `probability` values in that file make that row's values more likely to be
   chosen - see the worked colour example above).
2. Copies every non-`probability` column from the chosen row into the output document,
   expanding any `@...` special value first.
3. Merges the fields from every file together into a single output document. This is
   how you correlate some fields (e.g. put `country` and `city` in the same file/row so
   they always agree) while leaving other fields (e.g. `colour`) to vary completely
   independently in their own file.

Files nested inside a subdirectory (i.e. used via `@ARRAY(subdirectory,n)`) are *not*
picked up as top-level slices of the parent document - they are only used when
referenced from an `@ARRAY(...)` special value.

### Field value type coercion

Plain (non-`@`) CSV cell values are always read as text by the CSV parser, but when
written into the generated JSON document the tool tries, in order: 64-bit integer, then
floating point, then boolean (`true`/`false`), falling back to a string. Empty cells and
the literal text `null` are omitted from the document entirely rather than being written
as an empty string/null.
