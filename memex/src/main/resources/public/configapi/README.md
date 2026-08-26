# configapi

These JSON files configure the example web UI. In a real application they would
be served by a dynamic API, but here they are static so the UI can be repointed
at a different service without editing any JavaScript.

To repoint the UI at a new service, overwrite the three files below. There are
no code changes required in `main.js`.

---

## `apiEndpoint.json`

A single JSON **string** (not an object) holding the base path of the Spring
`@RestController` the UI should talk to. The UI appends `/query` and `/search`
to this value, so it must be the base path only — no trailing slash.

Example:

```json
"/api/inspections"
```

## `gridFields.json`

A JSON object mapping a **display label** (shown as the column header in the UI)
to a **MongoDB storage path** (the field path used in the projection sent to
MongoDB).

Rules:

- The **first entry must be the document's `@ID` field**. The UI uses it as the
  key when fetching a single document.
- Fields captured by the `@JsonAnySetter` fallback into the `payload` sub-object
  (i.e. fields **not** explicitly declared on the model class) must be prefixed
  with `payload.`. The UI strips the `payload.` segment at display time, so the
  same path works for both projection and response navigation.
- Fields explicitly declared on the model class (e.g. `testid`,
  `testmileage`) have **no** `payload.` prefix — their storage path and response
  path are identical.

Example (vehicle inspections):

```json
{
  "Test Number": "testid",
  "VehicleID": "vehicle.vehicleid",
  "Make": "vehicle.make",
  "Model": "vehicle.model",
  "Colour": "vehicle.colour",
  "Mileage": "testmileage",
  "Result": "testresult",
  "FirstUsed": "firstusedate"
}
```

## `queryableFields.json`

A JSON object describing which fields the UI offers as query inputs, and what
type each field is.

- Each **key** is a storage path, or `displayLabel=storagePath` if you want a
  custom label shown in the UI (the part before `=` is the label, the part after
  is the path).
- Each **value** is either:
  - `1` (a number) → a numeric field. The UI renders a free-text input, coerces
    the typed value to a number, and enables `<` and `>` comparison operators.
    Do **not** use `1` for date fields — a date string fails numeric coercion
    (`Number("2001-01-01")` is `NaN`), which serializes to `null` in the query.
  - An **array of sample string values** → a categorical field. The UI offers
    these values in a dropdown.
  - Any other non-array, non-number value (e.g. `""` or `"text"`) → a free-text
    string field with no dropdown and no numeric coercion. `<` and `>` are still
    accepted syntactically; values longer than 9 characters are parsed as dates
    if possible (rendered as `{"$date": "..."}` in the query). **Use this value
    for date fields**, since the UI only runs its date-parsing branch for
    non-numeric-typed fields.
- The same `payload.` prefix rule applies: use it for `@JsonAnySetter`
  fallback fields, omit it for explicitly-declared model fields.

Example (vehicle inspections):

```json
{
  "vehicle.vehicleid": 1,
  "vehicle.make": ["FORD", "VOLKSWAGEN", "AUDI"],
  "vehicle.model": ["ESCORT", "GOLF"],
  "vehicle.colour": ["BLACK", "WHITE", "SILVER", "RED", "BLUE", "GREEN", "YELLOW"],
  "testmileage": 1,
  "vehicle.vin": ""
}
```

---

## Worked example: "companies"

For a companies service whose controller is `@RequestMapping("/api/companies")`,
with `companyNumber` as the `@ID`, and where `companyName` and `nationality` are
stored in the `payload` fallback sub-object while `companyNumber`,
`incorporationDate`, and `regAddress.postTown` are explicitly declared on the
model:

**`apiEndpoint.json`**

```json
"/api/companies"
```

**`gridFields.json`**

```json
{
  "Company Number": "companyNumber",
  "Name": "payload.companyName",
  "Nationality Queried": "people.nationality",
  "Person Matched": "people.name",
  "Incorp. Date": "incorporationDate",
  "Town": "regAddress.postTown"
}
```

**`queryableFields.json`**

```json
{
  "companyNumber": 1,
  "incorporationDate": "",
  "Nationality=payload.nationality": ["BRITISH", "IRISH", "AMERICAN"],
  "Town=regAddress.postTown": ["LONDON", "MANCHESTER", "EDINBURGH"],
  "Company Name=payload.companyName": ""
}
```

---

## For AI agents

To reconfigure this UI for a new entity, inspect the Spring `@RestController`
and entity model in this repository to determine:

1. The controller's base `@RequestMapping` path → write it to `apiEndpoint.json`.
2. The `@ID` field → make it the **first** entry in `gridFields.json`.
3. Which fields are explicitly declared on the model vs. captured by the
   `@JsonAnySetter` `payload` fallback → prefix only the latter with `payload.`
   in both `gridFields.json` and `queryableFields.json`.
4. Which fields are numeric (value `1`), categorical (value: array of
   sample strings), or free-text strings/dates (value: any other non-array,
   non-number such as `""`) → set the values in `queryableFields.json`
   accordingly. **Date fields must use `""`, not `1`** — the UI only parses
   dates for non-numeric-typed fields, and `Number("2001-01-01")` is `NaN`
   (which serializes to `null`).

Then overwrite the three files above following the schemas. Do **not** edit
`main.js`.
