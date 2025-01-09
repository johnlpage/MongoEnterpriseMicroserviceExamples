#Enable slow query logging to logfile

db.setProfilingLevel(0,20); 

# Enable slow query logging to collection
#View latest database log entries from shell

db.setProfilingLevel(1,1); 

var log = db.adminCommand({getLog:"global"})
for( entry of log.log ) {

   let logEntry = JSON.parse(entry);
  if(entry.includes("POLO") ) {
   console.log(logEntry);
  }
 
}