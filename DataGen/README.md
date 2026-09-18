Probabilistic Data Generator
==============

build

` mvn clean package `

usage:

`java -jar DataGen.jsr inputDir docsToGenerate outputFile [batchSize] [oneupStart] [randomSeed]`

The output file is **JSONL** (JSON Lines) - *not* a JSON array. Each generated document
is written as a single compact JSON object on its own line, with no commas between
documents and no enclosing `[ ]` brackets. This makes the output streamable: you can
feed it directly to `mongoimport` (which accepts JSONL), process it line-by-line, or
split it for parallel loading, without ever holding the whole dataset in memory or
parsing it as one giant document. (If you need a JSON array for a specific tool, convert
it afterwards, e.g. with `jq -s '.'` - but most tooling, including `mongoimport` and
`mongosh`'s file readers, prefers JSONL.)

`batchSize` is optional and defaults to 2000. It controls how many documents are held in
memory at once before being written and released.

`oneupStart` is optional and defaults to 0. It sets the starting value of every `@ONEUP`
counter (see below) instead of 1. This is intended for running multiple instances of the
generator in parallel against the same input directory - each instance is given a
different, non-overlapping `oneupStart` (e.g. 0, 1000001, 2000001, ...) so that `@ONEUP`
values such as a listing ID do not collide across the output files.

Every `@ONEUP` column is its own independent sequence. The counter is keyed by the CSV
file and column name it appears in, so no two `@ONEUP` fields share a counter - not two
different columns in the same file, not columns in two different top-level files, and
not columns in different `@ARRAY` subdirectories. Each sequence starts at 1 (or at the
supplied `oneupStart`) and increments by 1 every time that column is evaluated,
persisting across the whole run.

For example, with a root `claim_number` `@ONEUP` field and two different `@ARRAY`
sub-generators each contributing their own `@ONEUP` field (say `item_id` and `doc_id`),
you get three independent, contiguous sequences - `claim_number` 1, 2, 3, ...; `item_id`
1, 2, 3, ... (across every array element in every document); and `doc_id` 1, 2, 3, ...
(likewise) - never interleaved with each other. Every one of these per-column counters
is seeded from the same `oneupStart`, so passing a distinct `oneupStart` per parallel
instance still keeps every `@ONEUP` field - root-level or nested inside any `@ARRAY` -
collision-free across instances.

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

Would add a number which increases by one starting at 1. Writing the value as
`@ONEUP(cus,8)` instead would give the same counter as a zero-padded string with a
prefix: `"cus00000001"`, `"cus00000002"`, ... (see the `@ONEUP(prefix,width)` entry
below). Each `@ONEUP` column is its own
independent sequence - two `@ONEUP` fields in different columns (or in different CSV
files, or in different `@ARRAY` subdirectories) never share a counter; each counts
1, 2, 3, ... separately across the whole run - see the `oneupStart` description above
and the `@ONEUP` entry below for details.

### Special (`@...`) value reference

All of these are only recognised in a CSV cell - they are ordinary literal strings
everywhere else. Arguments are given in parentheses, comma-separated.

- **`@ONEUP`** - no arguments. Auto-incrementing counter. Each `@ONEUP` column (keyed by
  the CSV file and column name it appears in) is an independent sequence that starts at
  1 - or at the `oneupStart` command-line argument if one was supplied - and increases by
  1 each time that column is evaluated. No two `@ONEUP` fields share a counter, at any
  level of nesting - see the `oneupStart` description near the top of this file.

- **`@ONEUP(prefix,width)`** - string form of `@ONEUP`, for prefixed human-style
  identifiers such as `"cus00001522"`. Emits `prefix` followed by the counter
  zero-padded to `width` digits, as a single JSON string - e.g. `@ONEUP(cus,8)` produces
  `"cus00000001"`, `"cus00000002"`, ... (and `1522` documents later, `"cus00001522"`).
  `width` is optional: `@ONEUP(cus)` produces `"cus1"`, `"cus2"`, ... It uses the same
  per-column counter sequence and the same starting value (1, or `oneupStart`) as the
  plain form, and the result is always a string - even when it is entirely digits
  (e.g. `@ONEUP(,8)` -> `"00000001"`), the leading zeros are preserved and it is never
  coerced to a number.

- **`@INTEGER(from,to)`** - a random whole number, inclusive of both `from` and `to`.
  Example: `@INTEGER(1,6)` simulates a die roll.

- **`@DOUBLE(a,b)`** - a random floating point number between `a` and `b`, rounded to
  2 decimal places. The two bounds can be given in either order (largest first or
  smallest first) - the result will always fall between the smaller and larger of the two.

- **`@DATE(startDate,endDate)`** - a random calendar date (no time component) between
  the two dates inclusive, formatted `YYYY-MM-DD`, e.g. `@DATE(2022-01-02,2022-12-31)`.

- **`@DATETIME(startDateTime,endDateTime)`** - a random date *and time* between the two
  ISO-8601 local date-times, e.g. `@DATETIME(2022-01-01T00:00:00,2022-12-31T23:59:00)`.
  Unlike `@DATE`, the time-of-day is included: the value is randomised to whole-minute
  precision between the two bounds (inclusive of both) and written out as a full
  ISO-8601 UTC date-time string, `YYYY-MM-DDTHH:MM:SSZ` (e.g. `2022-05-14T13:07:00Z`).
  Because the output carries a `Z` offset, it can be deserialized directly into an
  `Instant` (or `OffsetDateTime`, or anything else that understands ISO-8601), which a
  date-only `YYYY-MM-DD` value cannot. If you don't need the time component, use
  `@DATE` instead. The input bounds are local date-times with no offset - the generator
  treats them as UTC when emitting the `Z` suffix.

- **`@STRING(text)`** - *as a cell value*: forces this one cell's value to be a JSON
  string, never coerced to a number/boolean - e.g. `@STRING(95814)` produces
  `"95814"`, not `95814`. The raw text between the parentheses is used verbatim (no
  JSON de-quoting - the surrounding CSV quoting is all you need), so values
  containing commas or parentheses work, e.g. `@STRING(958 14 (downtown))`. An empty
  `@STRING()` omits the field, matching the "no empty fields" rule. Use it for
  one-off cells; for a whole column of semantically-string values (ZIP codes, NPIs,
  CPT/CVX codes, ...) prefer the column-header form below, which avoids wrapping
  every cell.

- **`@STRING(fieldname)`** - *as a column header*: marks the entire column as
  forced-string. Write the header as `"@STRING(address.zip)"` instead of
  `"address.zip"` and every value that column produces is emitted as a JSON string:
  plain literals (so `95814` and `02108` both come out as strings - no mixed types
  in the JSONL) *and* the results of `@...` specials in that column (`@INTEGER`,
  `@ONEUP`, `@DOUBLE`, `@DATE`, `@DATETIME` - e.g. `@INTEGER(1,100)` yields `"57"`).
  Only the header changes; the data cells stay verbatim. Rules: plain empty cells
  and the literal `null` are still omitted (the usual "no empty fields" behaviour);
  `@JSON`/`@ARRAY` values in a forced column pass through unchanged (an object/array
  has no meaningful string form); and the marker composes with dotted paths
  (`@STRING(address.zip)` -> nested `address.zip`) and with `SCALAR`
  (`@STRING(SCALAR)` -> an array of string scalars). This is what
  `gen_datagen_csvs.py` emits automatically for any field whose source values were
  strings.

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

  The recursive sub-generator for a given subdirectory is created once and reused for
  every document/array in the whole run, so `@ONEUP` and random values keep progressing
  across the entire run rather than resetting per document. Each `@ONEUP` column inside
  the sub-generator is its own independent sequence (see the `oneupStart`/`@ONEUP`
  sections above), separate from every root-level sequence and from every other `@ARRAY`
  subdirectory's sequences, while its random values are still derived from (and so vary
  with) the top-level `randomSeed`, and its `@ONEUP` sequences still start from the same
  `oneupStart` - so parallel instances remain collision-free.

  By default each array element is a JSON *object* built from all the columns in the
  sub-directory's CSV file(s), exactly like the top-level document. If you want an array
  of plain scalars instead (e.g. an array of strings or numbers) rather than an array of
  objects, see the special `SCALAR` field name below.

### Special field name: `ROOT`

If a column is named `ROOT` (instead of a normal field name or dotted path), its
generated value (which must be a JSON object, typically via `@JSON(...)`) *replaces*
the entire document being built at that point, rather than being nested as a field
inside it. This is only really useful combined with `@ARRAY`, to generate an array of
sub-documents whose shape is not a wrapper object but literally the object itself.

### Special field name: `SCALAR` - arrays of scalars

`ROOT` (above) lets an `@ARRAY` sub-generator produce an array of *objects* whose shape
is the object itself, instead of a wrapper. `SCALAR` is the equivalent for arrays of
plain scalars - strings, numbers, booleans - with no object wrapper at all, e.g.
`["red", "green", "blue"]` or `[3, 17, 42]` rather than `[{"colour": "red"}, ...]`.

If a column is named `SCALAR`, its generated value *becomes* the whole "document" for
that array element, written out as a bare JSON value (string/number/boolean) instead of
being nested as a field of an object. As with `ROOT`, this is only meaningful inside an
`@ARRAY(subdirectory,n)` sub-generator, and the CSV file(s) in that subdirectory should
contain only the `SCALAR` and `probability` columns (any other columns in the same file
are ignored for the purposes of that array element's value, since there's no object to
put them in).

Example - a `tags` array of 0-4 random colour strings, with an occasional random
integer thrown in:

`<inputDir>/tagsref.csv`:
```
"tags","probability"
"@ARRAY(tags,4)",100
```

`<inputDir>/tags/tag.csv`:
```
"SCALAR","probability"
"red",25
"green",25
"blue",25
"@INTEGER(1,100)",25
```

produces documents containing e.g.:
```json
{ "tags": ["red", "blue", "blue", 57] }
```

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

As an exception to the above, a value made up entirely of digits (optionally with a
leading `-`/`+` sign) that has more than one digit and starts with `0` - e.g. `"02150"`
(a ZIP code) or `"-0123"` - is *not* coerced to an integer, since `Integer`/`Long`
parsing would silently discard the leading zero(s). Such values are written to the
output document as strings instead. This check only applies to purely-digit values, so
decimals like `"0.5"` are unaffected and still coerce to a floating point number as
normal. To opt out of numeric/boolean coercion for a whole column, write the column
header as `@STRING(fieldname)`; for a single cell, use the `@STRING(text)` special
value - both are described in the special value reference above, and either one
guarantees the value is emitted as a JSON string (e.g. a `zip` column containing
`95814` and `02108` comes out as consistently-typed strings instead of a mix of
numbers and strings).

### Limitations - manage your expectations

Users are expecting more from `@JSON` than it delivers - it is really for exceptions,
so use it sparingly:

- `@JSON()` only creates fixed JSON objects - not arrays or templates - so use it
  sparingly.
- `@JSON(...)` values are static literals - nested `@...` specials are **not** expanded
  inside them, so you can't get per-document randomized values (e.g. geo coordinates)
  via `@JSON`. Use one fixed representative value per grouping instead (e.g. one
  lat/lon per city).
- There is no string concatenation - composite strings (e.g. street addresses) must be
  complete literal values in the CSV, not built from parts. The `@ONEUP(prefix,width)`
  form above is the one exception, covering prefixed/padded ID-style strings.
- `@DATE` emits a date only, `YYYY-MM-DD` - never a time component; `@DATETIME` emits a
  date *and* time, `YYYY-MM-DDTHH:MM:SSZ` (see above). Neither has sub-minute precision.
- There are no arithmetic or derived fields - to make fields loosely correlate
  (e.g. price vs. an estimate), draw them from the same weighted CSV row with similarly
  scoped ranges, rather than computing one from the other.
