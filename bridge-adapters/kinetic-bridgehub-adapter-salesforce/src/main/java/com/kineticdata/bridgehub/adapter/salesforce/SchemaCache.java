/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kineticdata.bridgehub.adapter.salesforce;

import java.util.concurrent.ConcurrentHashMap;
import com.kineticdata.bridgehub.adapter.BridgeError;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.LoggerFactory;

/**
 * SchemaCache is a class that manages the caching of Schema (and Field) objects
 * to help minimize API calls.
 */
public class SchemaCache {
    // Initialize the apache log
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(SalesforceAdapter.class);

    // Initializing the Map that will store the schemas
    private ConcurrentHashMap<String,Schema> cacheMap = new ConcurrentHashMap();

    /** The OAuth object that will use the users inputted configuration values
     * to authenticate with Salesforce to obtain an access token.
     */
    private OAuth oauth;

    /**
     * The access token that will be used to authenticate if retrieveSchema
     * has to be called.
     */
    private String accessToken;

    public SchemaCache(String accessToken, OAuth oauth) {
        this.accessToken = accessToken;
        this.oauth = oauth;
    }

    /**
     * A simple method meant to just purge the cache of all its contents.
     */
    public void clear() {
        cacheMap.clear();
    }

    /**
     * This method just gets a schema. If the schema already exists in the cache
     * it retrieves it from there. If not, it calls retrieveSchema so that the
     * schema can be retrieved from Salesforce.
     */
    public Schema getSchema(String schemaName) throws BridgeError {
        // Attempt to retrieve the schema from the local cache
        Schema schema = cacheMap.get(schemaName);

        // If the form doesn't exist in the cache, make a call to retrieve it
        if (schema == null) {
            schema = retrieveSchema(schemaName);
            // Cache the form if it was located
            if (schema != null) {
                cacheMap.put(schemaName, schema);
            }
        }

        // Return the schema
        return schema;
    }

    /**
     * Makes a call to Salesforce to retrieve the description of a given schema.
     */
    public Schema retrieveSchema(String schemaName) throws BridgeError {
        // Initialize the result object
        Schema result;
        String output = "";

        // Build the call to get the descrtption of the Structure
        HttpClient client = HttpClients.createDefault();
        String httpURL = String.format("https://%s.salesforce.com/services/data"
                + "/v37.0/sobjects/%s/describe",this.oauth.getSalesforceInstance(),schemaName.trim());
        logger.debug(httpURL);
        HttpGet get = new HttpGet(httpURL);
        get.setHeader("Authorization", "OAuth " + this.accessToken);
        HttpResponse response;
        try {
            // Executing the GET call and then retrieving Salesforce's response
            response = client.execute(get);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line;
            while ((line = rd.readLine()) != null) {
                output = output + line;
            }
            // Checks if the request has failed because of an invalid accessToken.
            // The message that is returned is [{"message":"Session expired or
            // invalid","errorCode":"INVALID_SESSION_ID"}], so if the JSON output
            // contains INVALID_SESSION_ID, we re-authenticate and then try the
            // GET request again
         if (output.contains("INVALID_SESSION_ID")) {
                logger.debug("Invalid access token. Calling the authentication method "
                        + "to retrieve a new token");
                this.accessToken = this.oauth.getAccessToken();
                get.setHeader("Authorization", "OAuth " + this.accessToken);

                response = client.execute(get);
                rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                output = "";
                while ((line = rd.readLine()) != null) {
                    output = output + line;
                }
                logger.debug("Response Recieved: " + output);
            }
        }
        catch (IOException e) {
            throw new BridgeError("Unable to make a connection to properly execute the"
                    + "query to Salesforce");
        }

        // Parsing the output string into a JSONObject (as well as checking for
        // a bad structure)
        JSONObject outputJSON;
        if (response.getStatusLine().getStatusCode() != 200) {
            JSONArray jsonArray = (JSONArray)JSONValue.parse(output);
            outputJSON = (JSONObject)JSONValue.parse(jsonArray.get(0).toString());
            if (outputJSON.get("errorCode").equals("NOT_FOUND")) {
                logger.debug("Original Error Message: " + outputJSON.toString());
                throw new BridgeError("This query contains an invalid structure. If you are trying to use a custom structure, make sure to append __c to it.");
            }
        }
        else {
            outputJSON = (JSONObject)JSONValue.parse(output);
        }

        ArrayList fields = (ArrayList)outputJSON.get("fields");
        Schema schema = new Schema((String)outputJSON.get("name"));

        for (int i = 0; i<fields.size(); i++) {
            JSONObject fieldJSON = (JSONObject)JSONValue.parse(fields.get(i).toString());
            String name = fieldJSON.get("name").toString();
            String type = fieldJSON.get("type").toString();
            Boolean sortable = (Boolean)fieldJSON.get("sortable");
            Field newField = new Field(name,type,sortable);
            schema.addField(name,newField);
        }
        return schema;
    }

    /**
     * Returns a list of all the Schemas currently in the cache by returning
     * the keys of the current cacheMap.
     */
    public Set<String> listSchemas() {
        return cacheMap.keySet();
    }

    /**
     * DEBUGGING METHOD: Adds a value to the cacheMap.
     */
    public void addSchema(String name, Schema schema) {
        cacheMap.put(name,schema);
    }

}