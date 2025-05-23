/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.kogito.jitexecutor.dmn.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.kogito.jitexecutor.dmn.requests.JITDMNPayload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.kie.kogito.jitexecutor.dmn.TestingUtils.getModelFromIoUtils;

@QuarkusTest
public class JITDMNResourceTest {

    static final String EVALUATION_HIT_IDS_FIELD_NAME = "evaluationHitIds";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String invalidModel1x;
    private static String invalidModel15;
    private static String validModel15;
    private static String modelWithExtensionElements;
    private static String modelWithMultipleEvaluationHitIds;
    private static String modelWithNestedConditionalEvaluationHitIds;

    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @BeforeAll
    public static void setup() throws IOException {
        invalidModel1x = getModelFromIoUtils("invalid_models/DMNv1_x/test.dmn");
        invalidModel15 = getModelFromIoUtils("invalid_models/DMNv1_5/DMN-Invalid.dmn");
        validModel15 = getModelFromIoUtils("valid_models/DMNv1_5/RiskScore_Simple.dmn");
        modelWithExtensionElements = getModelFromIoUtils("valid_models/DMNv1_x/testWithExtensionElements.dmn");
        modelWithMultipleEvaluationHitIds = getModelFromIoUtils("valid_models/DMNv1_5/MultipleHitRules.dmn");
        modelWithNestedConditionalEvaluationHitIds = getModelFromIoUtils("valid_models/DMNv1_5/NestedConditional.dmn");
    }

    @Test
    void testjitEndpoint() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(invalidModel1x, buildContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(200)
                .body(containsString("Loan Approval"), containsString("Approved"));
    }

    @Test
    void testjitSampleEndpoint() throws IOException {
        final String ruleId0 = "_1FA12B9F-288C-42E8-B77F-BE2D3702B7B6";
        final String ruleId1 = "_C8FA33B1-AF6E-4A59-B7B9-6FDF1F495C44";
        String sampleModel = getModelFromIoUtils("valid_models/DMNv1_5/Sample.dmn");
        Map<String, Object> context = new HashMap<>();
        context.put("Credit Score", Map.of("FICO", 700));

        Map<String, Object> monthly = new HashMap<>();
        monthly.put("Income", 121233);
        monthly.put("Repayments", 33);
        monthly.put("Expenses", 123);
        monthly.put("Tax", 32);
        monthly.put("Insurance", 55);
        Map<String, Object> applicantData = new HashMap<>();
        applicantData.put("Age", 32);
        applicantData.put("Marital Status", "S");
        applicantData.put("Employment Status", "Employed");
        applicantData.put("Monthly", monthly);
        context.put("Applicant Data", applicantData);

        Map<String, Object> requestedProduct = new HashMap<>();
        requestedProduct.put("Type", "Special Loan");
        requestedProduct.put("Rate", 1);
        requestedProduct.put("Term", 2);
        requestedProduct.put("Amount", 333);
        context.put("Requested Product", requestedProduct);

        context.put("id", "_0A185BAC-7692-45FA-B722-7C86C626BD51");
        JITDMNPayload jitdmnpayload = new JITDMNPayload(sampleModel, context);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult");

        ResponseBody body = response.getBody();
        String responseString = body.asString();
        JsonNode retrieved = MAPPER.readTree(responseString);
        ArrayNode decisionResultsNode = (ArrayNode) retrieved.get("decisionResults");
        Iterable<JsonNode> iterable = () -> decisionResultsNode.elements();
        Stream<JsonNode> targetStream = StreamSupport.stream(iterable.spliterator(), false);
        ObjectNode decisionNode = (ObjectNode) targetStream.filter(node -> node.get("decisionName").asText().equals("Credit Score Rating")).findFirst().get();
        ObjectNode evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(1);
        Map<String, Integer> expectedEvaluationHitIds0 = Map.of(ruleId0, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds0).containsEntry(entry.getKey(), entry.getValue().asInt()));

        targetStream = StreamSupport.stream(iterable.spliterator(), false);
        decisionNode = (ObjectNode) targetStream.filter(node -> node.get("decisionName").asText().equals("Loan Pre-Qualification")).findFirst().get();
        evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(1);
        Map<String, Integer> expectedEvaluationHitIds1 = Map.of(ruleId1, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds1).containsEntry(entry.getKey(), entry.getValue().asInt()));
    }

    @Test
    void testjitdmnResultEndpoint() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelWithMultipleEvaluationHitIds, buildMultipleHitContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body(containsString("Statistics"));
    }

    @Test
    void testjitdmnResultEndpointWithEvaluationHitIds() throws JsonProcessingException {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelWithMultipleEvaluationHitIds, buildMultipleHitContext());
        final String rule0 = "_E5C380DA-AF7B-4401-9804-C58296EC09DD";
        final String rule1 = "_DFD65E8B-5648-4BFD-840F-8C76B8DDBD1A";
        final String rule2 = "_E80EE7F7-1C0C-4050-B560-F33611F16B05";
        String response = given().contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body(containsString("Statistics"),
                        containsString(EVALUATION_HIT_IDS_FIELD_NAME),
                        containsString(rule0),
                        containsString(rule1),
                        containsString(rule2))
                .extract()
                .asString();
        JsonNode retrieved = MAPPER.readTree(response);
        ArrayNode decisionResultsNode = (ArrayNode) retrieved.get("decisionResults");
        ObjectNode decisionNode = (ObjectNode) decisionResultsNode.get(0);
        ObjectNode evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(3);

        final Map<String, Integer> expectedEvaluationHitIds = Map.of(rule0, 3, rule1, 2, rule2, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds).containsEntry(entry.getKey(), entry.getValue().asInt()));
    }

    @Test
    void testjitdmnResultEndpointWithNestedConditionalEvaluationHitIds() throws JsonProcessingException {
        final Map<String, Object> context = new HashMap<>();
        context.put("A", 1);
        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelWithNestedConditionalEvaluationHitIds, context);
        final String thenElementId = "_C69417CB-474E-4742-9D26-8D1ADB75CAEC";
        final String elseElementId = "_0C94AE89-A771-4CD8-A62F-B7BA7F8F2359";
        final String decisionId = "_E0D45F9F-76E3-4F85-8A0D-6127965F717A";
        String response = given().contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body(containsString("New Decision"),
                        containsString(EVALUATION_HIT_IDS_FIELD_NAME),
                        containsString(decisionId))
                .extract()
                .asString();
        JsonNode retrieved = MAPPER.readTree(response);
        ArrayNode decisionResultsNode = (ArrayNode) retrieved.get("decisionResults");
        Iterator<JsonNode> decisionResultsIterator = decisionResultsNode.elements();
        Iterable<JsonNode> decisionResultsIterable = () -> decisionResultsIterator;
        ObjectNode decisionNode = StreamSupport.stream(decisionResultsIterable.spliterator(), false)
                .filter(jsonNode -> jsonNode instanceof ObjectNode &&
                        jsonNode.has("decisionId") &&
                        jsonNode.get("decisionId").asText().equals(decisionId))
                .findFirst()
                .map(ObjectNode.class::cast)
                .orElseThrow(() -> new AssertionError(String.format("Expected decisionId %s not found", decisionId)));
        ObjectNode evaluationHitIdsNode = (ObjectNode) decisionNode.get(EVALUATION_HIT_IDS_FIELD_NAME);
        Assertions.assertThat(evaluationHitIdsNode).hasSize(1);
        final Map<String, Integer> expectedEvaluationHitIds = Map.of(thenElementId, 1);
        evaluationHitIdsNode.fields().forEachRemaining(entry -> Assertions.assertThat(expectedEvaluationHitIds).containsEntry(entry.getKey(), entry.getValue().asInt()));
    }

    @Test
    void testjitExplainabilityEndpoint() {
        Map<String, Object> context = new HashMap<>();
        context.put("Credit Score", "Poor");
        context.put("DTI", 33);
        JITDMNPayload jitdmnpayload = new JITDMNPayload(validModel15, context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/evaluateAndExplain")
                .then()
                .statusCode(200)
                .body(containsString("dmnResult"), containsString("saliencies"), containsString("DMN_A77074C1-21FE-4F7E-9753-F84661569AFC"),
                        containsString("featureName"));
    }

    @Test
    void testjitdmnWithExtensionElements() {
        Map<String, Object> context = new HashMap<>();
        context.put("m", 1);
        context.put("n", 2);

        JITDMNPayload jitdmnpayload = new JITDMNPayload(modelWithExtensionElements, context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body(containsString("m"), containsString("n"), containsString("sum"));
    }

    @Test
    void testjitEndpointFailure() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(invalidModel15, buildContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn")
                .then()
                .statusCode(400)
                .body(containsString("Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age'"));
    }

    @Test
    void testjitdmnEvaluateInvalidModel() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(invalidModel15, buildInvalidModelContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(400)
                .body(containsString("Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age'"));
    }

    @Test
    void testjitdmnEvaluateAndExplainInvalidModel() {
        JITDMNPayload jitdmnpayload = new JITDMNPayload(invalidModel15, buildInvalidModelContext());
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/evaluateAndExplain")
                .then()
                .statusCode(400)
                .body(containsString("Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age'"));
    }

    @Test
    void testjitdmnRetrieveInvalidElementPath() throws IOException {
        Map<String, Object> context = new HashMap<>();
        context.put("id", "_641F6FB1-6720-425E-9045-7EB9B90E2FFF");
        context.put("New Input Data", 8888);
        JITDMNPayload jitdmnpayload = new JITDMNPayload(getModelFromIoUtils("invalid_models/DMNv1_5/InvalidElementPath.dmn"), context);
        given()
                .contentType(ContentType.JSON)
                .body(jitdmnpayload)
                .when().post("/jitdmn/dmnresult")
                .then()
                .statusCode(200)
                .body("invalidElementPaths", hasItems(List.of("_172F9901-0884-47C1-A5B4-3C09CC83D5B6", "_8577FE15-1512-4BBE-885F-C30FD73ADC6B"),
                        List.of("_4FF85EFF-B9E6-41C3-9115-DC9690E3B6F7")));
    }

    static Map<String, Object> buildMultipleHitContext() {
        final List<BigDecimal> numbers = new ArrayList<>();
        numbers.add(BigDecimal.valueOf(10));
        numbers.add(BigDecimal.valueOf(2));
        numbers.add(BigDecimal.valueOf(1));
        final Map<String, Object> context = new HashMap<>();
        context.put("Numbers", numbers);
        return context;
    }

    private Map<String, Object> buildContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("FICO Score", 800);
        context.put("DTI Ratio", .1);
        context.put("PITI Ratio", .1);
        return context;
    }

    private Map<String, Object> buildInvalidModelContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("Can Drive?", false);
        context.put("Person Age", 14);
        context.put("Id", 1);
        return context;
    }
}
