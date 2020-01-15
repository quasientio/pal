package net.ittera.pal.svcs;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.junit.Ignore;
import org.junit.Test;
import picocli.CommandLine;

public class IndexingServiceTest {

  @Ignore
  @Test
  public void indexLog() {

    String[] args = new String[] {"--es-addr", "localhost:9200", "--log", "app.log0000000237"};
    int exitCode = new CommandLine(new IndexingService()).execute(args);
  }

  @Ignore
  @Test
  public void searchLog() {

    HttpResponse<JsonNode> response =
        Unirest.post("http://localhost:9200/petclinic_2/_search")
            .header("Content-Type", "application/json")
            //			.body(" { \"query\": { \"match\": { \"messageUuid\":
            // \"e7674009-27c0-4e89-84bf-465409ea44b5\" } } }").asJson();
            //			.body(" { \"query\": { \"match\": { \"name\": \"println\" } } }").asJson();
            .body(" { \"query\": { \"match\": { \"builderSeq\": \"4\" } } }")
            .asJson();
    System.out.printf("response status: %s%n", response.getStatusText());
    System.out.printf("response body: %s%n", response.getBody());
  }
}
