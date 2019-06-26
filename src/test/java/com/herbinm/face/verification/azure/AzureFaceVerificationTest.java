package com.herbinm.face.verification.azure;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AzureFaceVerificationTest {

    private static final String detectUri = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0/detect";
    private static final String verifyUri = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0/verify";
    private static final String subscriptionKey = "<YourSubscriptionKey>";

    private static final String BASE_PHOTO = "max_passport_photo.jpg";
    private static final String TEST_CASES = "tests.csv";

    private HttpClient httpClient = HttpClientBuilder.create().build();

    @Test
    public void verifyDataSet() throws Exception {
        List<String> baseFaceIds = detectFromLocalFile(new File(getClass().getClassLoader().getResource(BASE_PHOTO).getFile()));
        Assert.assertEquals("Base photo must contain single face",1, baseFaceIds.size());
        String baseFaceId = baseFaceIds.get(0);

        List<String> testCases = Files.readAllLines(Paths.get(getClass().getClassLoader().getResource(TEST_CASES).toURI()));

        for (String test : testCases){
            String input = test.split(",")[0];
            boolean matchesBase = Boolean.valueOf(test.split(",")[1]);
            System.out.println("Running test for photo: " + input);
            List<String> facesFromInput = detectFromLocalFile(new File(getClass().getClassLoader().getResource(input).getFile()));
            Assert.assertEquals(matchesBase, containsPersonFromBase(baseFaceId, facesFromInput));
        }

    }

    private List<String> detectFromLocalFile(File file) throws Exception {
        URIBuilder builder = new URIBuilder(detectUri);

        // Request parameters. All of them are optional.
        builder.setParameter("returnFaceId", "true");
        builder.setParameter("returnFaceLandmarks", "false");

        // Prepare the URI for the REST API call.
        URI uri = builder.build();
        HttpPost request = new HttpPost(uri);
        request.setHeader("Content-Type", "application/octet-stream");
        request.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey);

        // Convert image to bytes
        FileInputStream fileInputStreamReader = new FileInputStream(file);
        byte[] bytes = new byte[(int) file.length()];
        fileInputStreamReader.read(bytes);

        // Request body.
        ByteArrayEntity reqEntity = new ByteArrayEntity(bytes, ContentType.APPLICATION_OCTET_STREAM);
        request.setEntity(reqEntity);

        // Execute the REST API call and get the response entity.
        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();

        List<String> foundFaceIds = new ArrayList<>();

        if (entity != null) {
            System.out.println("DETECT Response:");
            String jsonString = EntityUtils.toString(entity).trim();
            System.out.println(jsonString +"\n");
            JSONArray faces = new JSONArray(jsonString);
            for(int i = 0; i < faces.length(); i++){
                JSONObject face = faces.getJSONObject(i);
                foundFaceIds.add(face.getString("faceId"));
            }
        }

        return foundFaceIds;

    }

    private boolean containsPersonFromBase(String baseFaceId, List<String> candidatesFaceIds) throws Exception{
        for (String candidate : candidatesFaceIds){
            URIBuilder builder = new URIBuilder(verifyUri);

            // Prepare the URI for the REST API call.
            URI uri = builder.build();
            HttpPost request = new HttpPost(uri);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey);

            request.setEntity(new StringEntity("{"+
                    "\"faceId1\":\""+baseFaceId +"\"," +
                    "\"faceId2\":\""+candidate +"\"" +
                    "}"));

            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                System.out.println("VERIFY Response:");
                String jsonString = EntityUtils.toString(entity).trim();
                System.out.println(jsonString +"\n");
                JSONObject object = new JSONObject(jsonString);
                return object.getBoolean("isIdentical");
            }

        }
        return false;
    }
}
