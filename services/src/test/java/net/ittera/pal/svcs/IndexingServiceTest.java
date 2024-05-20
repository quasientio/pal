/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.svcs;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import picocli.CommandLine;

@SuppressWarnings("unused")
public class IndexingServiceTest {

  public void indexLog() {

    String[] args = new String[] {"--es-addr", "localhost:9200", "--log", "app.log0000000237"};
    int exitCode = new CommandLine(new IndexingService()).execute(args);
  }

  public void searchLog() {

    HttpResponse<JsonNode> response =
        Unirest.post("http://localhost:9200/petclinic_2/_search")
            .header("Content-Type", "application/json")
            // .body(" { \"query\": { \"match\": { \"messageUuid\":
            // \"e7674009-27c0-4e89-84bf-465409ea44b5\" } } }").asJson();
            // .body(" { \"query\": { \"match\": { \"name\": \"println\" }}}").asJson();
            .body(" { \"query\": { \"match\": { \"builderSeq\": \"4\" }}}")
            .asJson();
    System.out.printf("response status: %s%n", response.getStatusText());
    System.out.printf("response body: %s%n", response.getBody());
  }
}
