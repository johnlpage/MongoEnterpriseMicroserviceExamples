package com.johnlpage.demo;


import com.johnlpage.mews.MewsApplication;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MewsApplication.class)
@AutoConfigureMockMvc
public class DemoApplicationTests {
  private static final Logger LOG = LoggerFactory.getLogger(DemoApplicationTests.class);
  @Autowired private MockMvc mockMvc;

  String readJsonFile(String path) throws Exception {
    return new String(Files.readAllBytes(Paths.get(path)));
  }

  @Test
  public void testLoadEndpoint() throws Exception {
    /*
    String jsonContent = readJsonFile("SAMPLE_DATA/small.txt");
    LOG.info("Testing data load");
    mockMvc
        .perform(
            post("http://localhost:8080/vehicles/inspections?updateStrategy=REPLACE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonContent))
        .andExpect(status().isOk());
        */
     
  }
}
