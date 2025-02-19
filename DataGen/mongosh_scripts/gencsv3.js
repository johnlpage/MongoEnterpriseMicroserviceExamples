// Used to generate CSV data from ensiting MongoDB collection
// Open a file for writing
const fstream = require('fs');

collection = db.getSiblingDB("vehicles").getCollection("inspections")
limit = {$limit: 100}


group = {
    $group: {
        _id: {
            testresult: "$testresult",
            nFailures: {$size: {$ifNull: ["$payload.faileditems", []]}}
        },
        probability: {$count: {}}
    }
}
var filename = `testresult.csv`

const fileWriter = fstream.createWriteStream(filename);


fileWriter.write(`"testresult","faileditems","probability"\n`)
var cursor = collection.aggregate([group]);

cursor.forEach(doc => {

    var line = `"${doc._id.testresult}","@ARRAY(faileditems,${doc._id.nFailures})",${doc.probability}\n`
    fileWriter.write(line);
}, err => {
    if (err) throw err;
    // Close the file stream properly
    fileWriter.end(']');
    print(`Data export completed. Check your file: ${filename}`);
});
