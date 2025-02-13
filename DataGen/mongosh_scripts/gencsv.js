// Used to generate CSV data from ensiting MongoDB collection
// Open a file for writing
const fstream = require('fs');

var fieldname = "capacity";


collection = db.getSiblingDB("vehicles").getCollection("vehicleinspection")
limit = {$limit: 100}
group = {
    $group: {_id: `$${fieldname}`, probability: {$count: {}}}
}
var filename = `${fieldname}.csv`

const fileWriter = fstream.createWriteStream(filename);

fileWriter.write(`"${fieldname}","probability"\n`)
var cursor = collection.aggregate([group]);

cursor.forEach(doc => {
    var line = `"${doc._id}",${doc.probability}\n`
    fileWriter.write(line);
}, err => {
    if (err) throw err;
    // Close the file stream properly
    fileWriter.end(']');
    print(`Data export completed. Check your file: ${filename}`);
});
