# Configuring Shard Key Support

memex's generic write path (`OptimizedMongoLoadRepositoryImpl`) can automatically
include a collection's shard key field(s) in the query used for
updates/replaces/deletes, so that a `mongos` can route the operation directly to
the correct shard instead of broadcasting it to every shard in the cluster.

This is entirely opt-in and configured per collection via
`CollectionPreflightConfig`.

## How to configure it

Override `getShardKeyFields()` in your collection's `CollectionPreflightConfig`
implementation, returning the shard key field(s) as they appear in the stored
document (dot-notation for nested/embedded fields):

```java
@Override
public List<String> getShardKeyFields() {
    // Shard key is { city, _id }. _id is always added to write queries
    // automatically, so only the other shard key field(s) need to be listed here.
    return List.of("city");
}
```

For a compound shard key, list every field except `_id`:

```java
@Override
public List<String> getShardKeyFields() {
    // Shard key is { tenantId, region, _id }
    return List.of("tenantId", "region");
}
```

On write, `OptimizedMongoLoadRepositoryImpl` adds each configured field to the
query *when the value is present on the item being written* - if an item is only
partially populated (some update flows only set `_id` plus a subset of fields),
any shard key field that isn't present is simply left off the query rather than
causing an error.

If you don't override `getShardKeyFields()`, it defaults to an empty list -
existing unsharded collections (or sharded collections where you don't need/want
explicit shard-key targeting) are unaffected.

## The `@Id` gotcha - and why you can basically ignore it if you shard on `_id`

**Spring Data MongoDB always maps a class's `@Id`-annotated field to the literal
document key `"_id"`, regardless of what the Java field is actually called.**
For example:

```java
public class Listing {
    @Id
    private String listingId;   // <-- stored in MongoDB as "_id", NOT "listingId"
    ...
}
```

This matters here because `getShardKeyFields()` must return **document field
names** (what's actually stored, i.e. `"_id"`), not **Java field names** (what
the class calls it, i.e. `"listingId"`). If you mistakenly list the Java field
name of your `@Id` field, the code would:
1. Successfully resolve a value from the Java object (since reflection finds the
   Java field fine), but
2. Query on a document key (`"listingId"`) that doesn't actually exist in the
   stored document (it's stored as `"_id"`), so the query criteria added is
   silently wrong and the write would match zero documents.

**`MongoDbPreflightCheckService` guards against this automatically at startup:**
- If you list `"_id"` itself in `getShardKeyFields()`, it logs a harmless warning
  and ignores it - `"_id"` is always included in every write query automatically,
  so this is unnecessary but not dangerous.
- If you list the **Java field name** of your `@Id` field (e.g. `"listingId"`),
  it throws `IllegalStateException` at startup with a clear message telling you
  to remove it, rather than letting it silently corrupt writes at runtime.

### The practical takeaway

**If your shard key is (or includes) the document's `_id` field, you don't need
to do anything special for it at all.** Just:
- Don't list `"_id"` in `getShardKeyFields()` (it's redundant - it's always
  included).
- Don't list your `@Id` field's Java name either (it's wrong - and startup
  validation will catch it and fail loudly if you do).
- Only list the *other* shard key field(s), if any (e.g. `"city"` for a
  compound `{ city: 1, _id: 1 }` shard key).

If your collection is sharded purely on `_id` (or a hashed `_id`), you can
usually leave `getShardKeyFields()` unimplemented (returning the default empty
list) entirely - `_id` is already part of every write query by default via the
existing `where("_id").is(idValue)` criteria, so there's nothing extra to
configure.
