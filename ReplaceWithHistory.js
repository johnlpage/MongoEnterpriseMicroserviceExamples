// Example for MongoDB Shell Javascript showing how the replace with history work
// Need this so if I set a subset of values it still works

function unwindUpdate(obj, rval, prefix) {
  if (!rval) rval = {};
  if (!prefix) prefix = "";

  for (let k in obj) {
    if (Object.prototype.toString.call(obj[k]) == "[object Object]") {
      unwindUpdate(obj[k], rval, prefix + k + ".");
    } else {
      rval[prefix + k] = obj[k];
    }
  }
  return rval;
}

function computeUpdate(obj) {


  const deltaupdate = [];

  obj = unwindUpdate(obj);

  const updateId = new ObjectId();

  //Make a copy of the existing version of delta in case we want a no-op
  const emptyDelta = {
    $set: { _delta: { _changed: false  }, _originalDelta: "$_delta" },
  };
  deltaupdate.push(emptyDelta);

  for (let field in obj) {
    let delta = {};
    // For each changed fields - record the old value in delta
    // If it's changed from non-existent make it an explicit null
    // Also increment the _changes count, because of this count
    // we need to make these separate steps in the pipeline

    let val = obj[field];

    // If field changes set it to old value with explicit null
    // If it doesn't set it to $$REMOVE but

    const valueChanged = { $ne: [val, `$${field}`] };
    delta[`_delta.${field}`] = {
      $cond: [valueChanged, { $ifNull: [`$${field}`, null] }, "$$REMOVE"],
    };
    //Set flag if this is a change
    delta[`_delta._changed`] = { $or: ["$_delta._changed", valueChanged] };
    //delta[`_delta.${field}_c`] = valueChanged;
    delta[field] = val;
    deltaupdate.push({ $set: delta });

  }

  const deltaWithUpdateID = {
    $mergeObjects: ["$_delta", { lastUpdateId: updateId  }],
  };
  //If we have not recored any changes revert - if we have then set a changed flag
  const resetIfNoChanges = {
    $set: {
      _delta: { $cond: ["$_delta._changed", deltaWithUpdateID, "$_originalDelta"] },
      _originalDelta: "$$REMOVE",
    },
  };
  deltaupdate.push(resetIfNoChanges);

  //Cleanup
    deltaupdate.push({ $set : {  "_delta._changed" : "$$REMOVE" }})

  return deltaupdate;
}

db.a.drop();
query = { _id: 1 };
original = { _id: 1, a: 1, b: 1, o: { c: 1, d: 1 } };

var v1 = computeUpdate(original);
//console.log(v1);
var r = db.a.updateOne(query, v1, { upsert: true });
console.log(`inserts: ${r.upsertedCount} updates: ${r.modifiedCount}`);
console.log(db.a.findOne());

var v1 = computeUpdate(original);

var r = db.a.updateOne(query, v1, { upsert: true });
console.log(`inserts: ${r.upsertedCount} updates: ${r.modifiedCount}`);
console.log(db.a.findOne());
