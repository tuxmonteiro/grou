/*
 * Copyright (c) 2017-2017 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.report.ts.opentsdb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.globocom.grou.entities.Test;
import com.globocom.grou.report.ts.TSClient;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Request;
import com.ning.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

public class OpenTSDBClient implements TSClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTSDBClient.class);

    private static final AsyncHttpClient HTTP_CLIENT;
    static {
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setAllowPoolingConnections(true)
                .setFollowRedirect(false)
                .setCompressionEnforced(false)
                .setConnectTimeout(2000)
                .setMaxConnectionsPerHost(100)
                .setMaxConnections(100)
                .setAcceptAnyCertificate(true).build();
        HTTP_CLIENT = new AsyncHttpClient(config);
    }

    private static final String OPENTSDB_URL = ResourceEnv.OPENTSDB_URL.getValue();

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final TypeReference<ArrayList<HashMap<String,Object>>> typeRef = new TypeReference<ArrayList<HashMap<String,Object>>>() {};

    @Override
    public ArrayList<HashMap<String, Object>> makeReport(Test test) {

        String[] metrics = {
                "grou.response.status.count",
                "grou.response.completed.median",
                "grou.response.completed.upper",
                "grou.response.completed.p95",
                "grou.response.size.sum",
                "grou.loaders.cpu.mean",
                "grou.loaders.conns.mean",
                "grou.loaders.memFree.mean",
                "grou.targets.conns.mean",
                "grou.targets.memFree.p95",
                "grou.targets.memBuffers.p95",
                "grou.targets.memCached.p95",
                "grou.targets.cpu.median",
                "grou.targets.load1m",
                "grou.targets.load5m",
                "grou.targets.load15m"
        };

        long testCreated = TimeUnit.MILLISECONDS.toSeconds(test.getCreatedDate().getTime());
        long testLastModified = TimeUnit.MILLISECONDS.toSeconds(test.getLastModifiedDate().getTime());
        List<Query> queries = Arrays.stream(metrics).map(m -> {
                                    long downsampleInterval = (testLastModified - testCreated) / 2;
                                    String downsampleSuffix = "s-p95";
                                    String aggregator = "avg";
                                    return new Query(test, m, aggregator, downsampleInterval + downsampleSuffix);
                                }).collect(Collectors.toList());

        final ArrayList<HashMap<String, Object>> listOfResult = new ArrayList<>();
        queries.forEach(q -> {
            ObjectNode jBody = JsonNodeFactory.instance.objectNode();
            jBody.set("queries", mapper.valueToTree(Collections.singleton(q)));
            jBody.put("start", testCreated);
            jBody.put("end", testLastModified);

            String bodyRequest = jBody.toString();
            Request requestQuery = HTTP_CLIENT.preparePost(OPENTSDB_URL + "/api/query")
                    .setBody(bodyRequest)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON.toString())
                    .build();

            try {
                Response response = HTTP_CLIENT.executeRequest(requestQuery).get();
                String body = response.getResponseBody();
                ArrayList<HashMap<String, Object>> resultMap = mapper.readValue(body, typeRef);
                listOfResult.add(resultMap.stream().findAny().orElse(new HashMap<>()));
            } catch (InterruptedException | ExecutionException | IOException e) {
                if (LOGGER.isDebugEnabled()) LOGGER.error(e.getMessage(), e);
            }
        });

        try {
            if (listOfResult.isEmpty()) {
                return mapper.readValue("[{\"error\":\" OpenTSDB result is EMPTY \"}]", typeRef);
            } else {
                return listOfResult;
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public class Filter implements Serializable {
        public final boolean groupBy = false;
        public final String type = "literal_or";

        public final String tagk;
        public final String filter;

        public Filter(String tag, String filter) {
            this.tagk = tag;
            this.filter = filter;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public class Query implements Serializable {
        public final String metric;
        public final String aggregator;
        public final String downsample;
        public final Filter[] filters;

        public Query(Test test, String metric, String aggregator, String downsample) {
            this.metric = metric;
            this.aggregator = aggregator;
            this.downsample = downsample;
            this.filters = new Filter[]{ new Filter("project", test.getProject()), new Filter("test", test.getName()) };
        }
    }

}
