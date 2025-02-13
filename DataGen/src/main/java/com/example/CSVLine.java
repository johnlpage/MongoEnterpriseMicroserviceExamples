package com.example;


import org.apache.commons.csv.CSVRecord;



public class CSVLine {


        private int cumulativeProbability;
        private CSVRecord csvRecord;

        CSVLine(int cumulativeProbability, CSVRecord csvRecord) {
            this.cumulativeProbability = cumulativeProbability;
            this.csvRecord = csvRecord;
        }

        public int getCumulativeProbability() {
            return cumulativeProbability;
        }

        public CSVRecord getCsvRecord() {
            return csvRecord;
        }
    }



