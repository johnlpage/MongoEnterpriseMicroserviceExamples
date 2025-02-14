// Used to generate CSV data from ensiting MongoDB collection
// Open a file for writing
const fstream = require('fs');

collection = db.getSiblingDB("vehicles").getCollection("vehicleinspection")
limit = {$limit: 100}
unwind = {$unwind: "$payload.faileditems"};

group = {
    $group: {_id: `$payload.faileditems`, probability: {$count: {}}}
}
var filename = `faileditems.csv`

const fileWriter = fstream.createWriteStream(filename);


fileWriter.write(`"faileditems","probability"\n`)
var cursor = collection.aggregate([unwind, group]);

cursor.forEach(doc => {
    var jsonString = JSON.stringify(doc._id).replace(/"/g, '""');

    var line = `"@JSON(${jsonString})",${doc.probability}\n`
    fileWriter.write(line);
}, err => {
    if (err) throw err;
    // Close the file stream properly
    fileWriter.end(']');
    print(`Data export completed. Check your file: ${filename}`);
});
