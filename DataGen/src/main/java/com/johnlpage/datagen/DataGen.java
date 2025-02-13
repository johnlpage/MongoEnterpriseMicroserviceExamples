package com.johnlpage.datagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class DataGen {


    public static void main(String[] args) throws IOException {

        String directoryPath;
        int numberOfJsonDocuments;
        String outputPath;

        if(args.length < 3) {
            System.out.println("Usage: java -jar DataGen.jar definitionDirectory count outputPath");
            System.exit(1);
        }
        directoryPath = args[0];
        numberOfJsonDocuments = Integer.parseInt(args[1]);
        outputPath = args[2];

        DataGenProcessor processor = new DataGenProcessor(directoryPath);
        int batchSize = 50000;
        LocalDateTime startTime = LocalDateTime.now();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            for (int docs = 0; docs < numberOfJsonDocuments; docs += batchSize) {
                int docsInBatch = batchSize;
                if (docs + batchSize > numberOfJsonDocuments) {
                    docsInBatch = numberOfJsonDocuments - docs;
                }

                List<ObjectNode> documents = processor.generateJsonDocuments(docsInBatch);

                // OR -> ObjectWriter objectWriter  = new ObjectMapper().writerWithDefaultPrettyPrinter();
                ObjectWriter objectWriter  = new ObjectMapper().writer();

                for (ObjectNode document : documents) {

                   writer.write(
                             objectWriter.writeValueAsString(document));
                   writer.newLine();
                }
                LocalDateTime now = LocalDateTime.now();
                Duration duration = Duration.between(startTime, now);

                long millis =  duration.toMillis();
                if(millis > 1) {
                    double predictedSeconds = (double) (numberOfJsonDocuments * millis) /  (docs + batchSize)  ;
                    int remaining = (int) Math.floor((predictedSeconds - millis)/1000);

                    System.out.println("Generated " + (docs + docsInBatch) + " of " + numberOfJsonDocuments
                            + " documents in " + duration.toSeconds() + " s , estimating " + remaining + " seconds remaining");
                }
            }
        }
        catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            }
    }
}
