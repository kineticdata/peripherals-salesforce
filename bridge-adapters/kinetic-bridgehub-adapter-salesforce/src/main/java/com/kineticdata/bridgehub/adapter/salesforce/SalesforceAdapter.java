/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kineticdata.bridgehub.adapter.salesforce;

import com.kineticdata.bridgehub.adapter.BridgeAdapter;
import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.BridgeUtils;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import com.kineticdata.commons.v1.config.ConfigurableProperty;
import com.kineticdata.commons.v1.config.ConfigurablePropertyMap;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.LoggerFactory;


public class SalesforceAdapter implements BridgeAdapter {
    /*----------------------------------------------------------------------------------------------
     * PROPERTIES
     *--------------------------------------------------------------------------------------------*/

    /** Defines the adapter display name. */
    public static final String NAME = "Salesforce Bridge";

    /** Defines the logger */
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(SalesforceAdapter.class);

    /** Adapter version constant. */
    public static String VERSION;
    /** Load the properties version from the version.properties file. */
    static {
        try {
            java.util.Properties properties = new java.util.Properties();
            properties.load(SalesforceAdapter.class.getResourceAsStream("/"+SalesforceAdapter.class.getName()+".version"));
            VERSION = properties.getProperty("version");
        } catch (IOException e) {
            logger.warn("Unable to load "+SalesforceAdapter.class.getName()+" version properties.", e);
            VERSION = "Unknown";
        }
    }

    /** Defines the collection of property names for the adapter. */
    public static class Properties {
        public static final String PROPERTY_USERNAME = "Username";
        public static final String PROPERTY_PASSWORD = "Password";
        public static final String PROPERTY_TOKEN = "Security Token";
        public static final String PROPERTY_CLIENT_ID = "Client ID";
        public static final String PROPERTY_CLIENT_SECRET = "Client Secret";
        public static final String PROPERTY_SALESFORCE_INSTANCE = "Salesforce Instance";
    }

    private String username;
    private String password;
    private String propertyToken;
    private String clientId;
    private String clientSecret;
    private String salesforceInstance;
    private String accessToken;
    private SchemaCache schemaCache;
    private OAuth oauth;

    protected final String apiVersion = "v37.0";

    private final ConfigurablePropertyMap properties = new ConfigurablePropertyMap(
        new ConfigurableProperty(Properties.PROPERTY_USERNAME).setIsRequired(true),
        new ConfigurableProperty(Properties.PROPERTY_PASSWORD).setIsRequired(true).setIsSensitive(true),
        new ConfigurableProperty(Properties.PROPERTY_TOKEN).setIsRequired(true),
        new ConfigurableProperty(Properties.PROPERTY_CLIENT_ID).setIsRequired(true),
        new ConfigurableProperty(Properties.PROPERTY_CLIENT_SECRET).setIsRequired(true).setIsSensitive(true),
        new ConfigurableProperty(Properties.PROPERTY_SALESFORCE_INSTANCE).setIsRequired(true)
    );

    /*---------------------------------------------------------------------------------------------
     * SETUP METHODS
     *-------------------------------------------------------------------------------------------*/
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
       return VERSION;
    }

    @Override
    public ConfigurablePropertyMap getProperties() {
        return properties;
    }

    @Override
    public void setProperties(Map<String,String> parameters) {
        properties.setValues(parameters);
    }

    @Override
    public void initialize() throws BridgeError {
        // Testing the configuration values to make sure that they
        // correctly authenticate with Salesforce
//        testAuth();
        this.username = properties.getValue(Properties.PROPERTY_USERNAME);
        this.password = properties.getValue(Properties.PROPERTY_PASSWORD);
        this.propertyToken = properties.getValue(Properties.PROPERTY_TOKEN);
        this.clientId = properties.getValue(Properties.PROPERTY_CLIENT_ID);
        this.clientSecret = properties.getValue(Properties.PROPERTY_CLIENT_SECRET);
        this.salesforceInstance = properties.getValue(Properties.PROPERTY_SALESFORCE_INSTANCE);
        this.oauth = new OAuth(
            this.username,
            this.password,
            this.propertyToken,
            this.clientId,
            this.clientSecret,
            this.salesforceInstance
        );

        this.accessToken = oauth.getAccessToken();

        this.schemaCache = new SchemaCache(this.accessToken, this.oauth);
    }

    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    /**
     * This method returns a JSON String that contains just one name,value pair
     * containing the total amount of records that the query returned
     *
     * @param request
     * @return
     * @throws BridgeError
     */
    @Override
    public Count count(BridgeRequest request) throws BridgeError {
        long startTime = System.nanoTime();

        // Initialize the result data and response variables
        Map<String,Object> data = new LinkedHashMap();
        String output = "";

        // Setting up the field as Count()
        List<String> field = new ArrayList<String>();
        field.add("Count()");
        request.setFields(field);

        HttpClient client = HttpClients.createDefault();
        String query = buildSalesforceQuery(request, "count");

        // Builds and encodes the whole URL, while putting it into HTTPGet
        // form. Then adds the authorization header with the current OAuth
        // access token.
        HttpGet get = new HttpGet(String.format("https://%s.salesforce.com/services/data/%s/query/?q=",this.salesforceInstance,apiVersion) + URLEncoder.encode(query));
        get.setHeader("Authorization", "OAuth " + this.accessToken);
        HttpResponse response;

        try {
            // Executing the GET call and then retrieving Salesforce's response
            response = client.execute(get);
            HttpEntity entity = response.getEntity();
            output = EntityUtils.toString(entity);

            // Checks if the request has failed because of an invalid accessToken.
            // The message that is returned is [{"message":"Session expired or
            // invalid","errorCode":"INVALID_SESSION_ID"}], so if the JSON output
            // contains INVALID_SESSION_ID, we re-authenticate and then try the
            // GET request again (this is due to session timeout of the access
            // token)
            // Look into if old session ID's work, and if it does make sure to comment about it here!!!!!!!!!!!!!!!!!!!!
            if (output.contains("INVALID_SESSION_ID")) {
                logger.debug("Invalid access token. Calling the authentication method "
                        + "to retrieve a new token");
                this.accessToken = this.oauth.getAccessToken();
                get.setHeader("Authorization", "OAuth " + this.accessToken);

                response = client.execute(get);
                // Parsing the response that is retrieved from Salesforce
                entity = response.getEntity();
                output = EntityUtils.toString(entity);
                logger.debug("Response Recieved: " + output);
            }
        }
        catch (IOException e) {
            throw new BridgeError("Unable to make a connection to properly execute the"
                    + "query to Salesforce");
        }

        // Parsing the response to retrieve the totalSize(count) of the
        // SOQL Query
        if (response.getStatusLine().getStatusCode() != 200) {
            parseError(output);
        }
        else {
            data = parseResponse(request, output, "count");
        }
        long endTime = System.nanoTime();
        long methodTime = endTime - startTime;
        logger.trace("Build count method length: " + String.valueOf((double)methodTime / 1000000000.0) + " seconds");

        Long count = Long.parseLong(data.get("count").toString());

        //Return the response
        return new Count(count);
    }

    /**
     * This method returns a JSON String that contains a unique record and all
     * of the requested fields
     *
     * @param request
     * @return
     * @throws BridgeError
     */
    @Override
    public Record retrieve(BridgeRequest request) throws BridgeError {
        // Initialize the result data and response variables
        Map<String,Object> data = new LinkedHashMap();
        String output = "";

        // Calling a helper method to build the query string that is then used
        // so that we can make it into a HttpGet object
        HttpClient client = HttpClients.createDefault();
        String query = buildSalesforceQuery(request, "retrieve");

        // Builds and encodes the whole URL, while putting it into HTTPGet
        // form. Then adds the authorization header with the current OAuth
        // access token.
        HttpGet get = new HttpGet(String.format("https://%s.salesforce.com/services/data/%s/query/?q=",this.salesforceInstance,apiVersion) + URLEncoder.encode(query));
        get.setHeader("Authorization", "OAuth " + this.accessToken);
        HttpResponse response;

        try {
            response = client.execute(get);

            // Parsing the response that is retrieved from Salesforce
            HttpEntity entity = response.getEntity();
            output = EntityUtils.toString(entity);

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
                // Parsing the response that is retrieved from Salesforce
                entity = response.getEntity();
                output = EntityUtils.toString(entity);

                logger.debug("Recieved Response: " + output);
            }
        }
        catch (IOException e) {
            throw new BridgeError("Unable to make a connection to properly execute the"
                    + "query to Salesforce");
        }

        // Parsing the response, checking to make sure it returned only a single
        // result and then removing everything but that single record before
        // returning it
        if (response.getStatusLine().getStatusCode() != 200) {
            parseError(output);
        }
        else {
            data = parseResponse(request, output, "retrieve");
        }

        // Returning the response
        return new Record((Map)data.get("record"));
    }

    /**
     * This method returns a JSON String that contains the results of the query.
     * The JSON contains an array that holds all the field names, a nested array
     * that contains all of the records, with each individual record containing
     * just the data in the order that it was presented in the field array.
     * Lastly there is another json string containing the metadata (page size,
     * page number, offset, count, size) from this query.
     *
     * @param request
     * @return
     * @throws BridgeError
     */
    @Override
    public RecordList search(BridgeRequest request) throws BridgeError {
        Long startAllTime = System.nanoTime();

        // Initialize the result data and response variables
        Map<String,Object> data = new LinkedHashMap();
        String output = "";

        // Calling a helper method to build the query string that is then used
        // so that we can make it into a HttpGet object. Also, getting
        // the metadata so that it can be passed to buildSalesforceQuery
        HttpClient client = HttpClients.createDefault();
        Long startTopTime = System.nanoTime();
        // If no fields were passed in, created a comma separated list of all
        // the fields in the current structure to return all the fields
        if (request.getFields() == null || request.getFields().isEmpty()) {
            // Get all the fields the schema, sort them, and then add them as the request fields
            List<String> fields = new ArrayList<String>(this.schemaCache.getSchema(request.getStructure()).getSchema().keySet());
            Collections.sort(fields);
            request.setFields(fields);
        }
        String query = buildSalesforceQuery(request, "search", null);
        long endTopTime = System.nanoTime();
        long topTime = endTopTime - startTopTime;
        logger.trace("Top time call length: " + String.valueOf((double)topTime / 1000000000.0) + " seconds");

        // Builds and encodes the whole URL, while putting it into HTTPGet
        // form. Then adds the authorization header with the current OAuth
        // access token.
        HttpGet get = new HttpGet(String.format("https://%s.salesforce.com/services/data/%s/query/?q=",this.salesforceInstance,apiVersion) + URLEncoder.encode(query));
        get.setHeader("Authorization", "OAuth " + this.accessToken);
        HttpResponse response;

        Long startBottomTime;
        try {
            long startTime = System.nanoTime();
            response = client.execute(get);
            long endTime = System.nanoTime();
            long methodTime = endTime - startTime;
            logger.trace("Search call length: " + String.valueOf((double)methodTime / 1000000000.0) + " seconds");

            startBottomTime = System.nanoTime();

            // Parsing the response that is retrieved from Salesforce
            HttpEntity entity = response.getEntity();
            output = EntityUtils.toString(entity);

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
                entity = response.getEntity();
                output = EntityUtils.toString(entity);
            }
            logger.debug("Response Retrieved: " + output);
        }
        catch (IOException e) {
            throw new BridgeError("Unable to make a connection to properly execute the"
                    + "query to Salesforce");
        }

        // Building the output metadata
        Map<String,Long> outputMetadata = new LinkedHashMap();
        outputMetadata.put("pageSize", Long.valueOf(request.getMetadata("pageSize")));
        outputMetadata.put("pageNumber", Long.valueOf(request.getMetadata("pageNumber")));
        outputMetadata.put("offset", Long.valueOf(request.getMetadata("offset")));
        // Size is left blank for now, but will be inserted when parseResponse
        // is called
        outputMetadata.put("size", null);

        // Parsing the output string into a JSONObject
        if (response.getStatusLine().getStatusCode() != 200) {
            parseError(output);
        }
        else {
            data = parseResponse(request, output, "search", outputMetadata);
        }

        long endBottomTime = System.nanoTime();
        long bottomTime = endBottomTime - startBottomTime;
        logger.trace("Bottom time call length: " + String.valueOf((double)bottomTime / 1000000000.0) + " seconds");

        // Lastly, getting the overall count for this call (if necessary). If
        // possible though, the count will be computed without making the method
        // call to try and save computing time
        if (outputMetadata.get("pageSize") == 0L) {
            outputMetadata.put("count", outputMetadata.get("size"));
        }
        else if (outputMetadata.get("size") < outputMetadata.get("pageSize")) {
            // Calculates the count for all the previous pages
            Long prevCount = (outputMetadata.get("page")-1L) * outputMetadata.get("pageSize");
            outputMetadata.put("count",prevCount + outputMetadata.get("size"));
        }
        else {
            // If no other options are available, make the method call to count
            Long count = Long.valueOf(count(request).getValue());
            outputMetadata.put("count", count);
        }

        long endAllTime = System.nanoTime();
        long methodTime = endAllTime - startAllTime;
        logger.trace("Search method call length: " + String.valueOf((double)methodTime / 1000000000.0) + " seconds");

        JSONArray jsonArray;
        JSONObject jsonOutput = (JSONObject)JSONValue.parse(output);
        jsonArray = (JSONArray)jsonOutput.get("records");

        List<Record> records = new ArrayList<Record>();

        for (int i=0; i < jsonArray.size(); i++) {
            JSONObject recordObject = (JSONObject)jsonArray.get(i);
            Map<String,Object> record = new HashMap<String,Object>();
            for (Map.Entry<String,Object> entry : ((Map<String,Object>)recordObject).entrySet()) {
                record.put(entry.getKey(),toString(entry.getValue()));
            }
            records.add(new Record(record));
//            records.add(new Record((Map<String,Object>)recordObject));
        }

        // Returning the response
        return new RecordList(request.getFields(), records);
    }

    /*----------------------------------------------------------------------------------------------
     * PRIVATE HELPER METHODS
     *--------------------------------------------------------------------------------------------*/

    /**
     * This method will be called when the result does not have to worry about
     * ordering its response (and thus doesn't have to worry about metadata)
     *
     * @param request
     * @param method
     * @return
     * @throws BridgeError
     */
    private String buildSalesforceQuery(BridgeRequest request, String method) throws BridgeError {
        return buildSalesforceQuery(request, method, null);
    }

    /**
     * Builds a SOQL request so that it can be used to retrieve the needed
     * information from Salesforce. Returns an HttpGet request that can then
     * be executed after it has been returned.
     *
     * @param request
     * @param method
     * @param metadata
     * @return
     * @throws BridgeError
     */
    private String buildSalesforceQuery(BridgeRequest request, String method, Map<String,String> metadata) throws BridgeError {
        logger.trace("Building the Salesforce SOQL Query");

        // Builds to SOQL request from the given BridgeRequest parameters
        // --Example Situation--
        // If Structure=Account&Fields=[Name]&Query=* the string that will
        // be added onto httpURL is "SELECT Name from Account". Because
        // Query is selecting everything, we don't need to append that to
        // the URL
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT ").append(request.getFieldString());
        queryBuilder.append(" from ").append(request.getStructure());

//        if (request.getFieldString() == null) {
//            throw new BridgeError("Invalid Request: Missing the 'Fields' parameter");
//        }

        // Parsing the query to include parameters if they have been used.
        SalesforceQualificationParser parser = new SalesforceQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());

        // Builds the query string so that it is in the proper format for
        // the SOQL request. To do this, the single and double quotes are
        // stripped and the whitespace is trimmed so that the final request
        // can be put into the final form of WHERE param='value'
        // If the query entered is "*", it is assumed that everything in the
        // table is being queried, so the WHERE parameter is omitted from
        // the final call. The regular expression matches anything that is a
        // word character (letter, number, underscore) that is then followed
        // by the string '*', which is meant to catch all queries that are made
        // such as Name='*'
        if (!query.equals("*") && !query.matches("\\w+='\\*'")) {
            // Building the query using Salesforces language
            queryBuilder.append(" WHERE ").append(query);
        }

        Long startTopTime = System.nanoTime();
        if (method.equals("search")) {
            logger.trace("Building ORDER BY data for the query");
            // Build the order of the sorting fields when method = search. First
            // priority is to use the metadata if it has been inputted by the
            // user. If there is no order metadata though, the order will be
            // sorted in the order that the fields were entered (in ascending
            // order)
            // --Example Situation--
            // SELECT 1,2,3,4,5 from Test is expanded to...
            long startSchemaTime = System.nanoTime();
            Schema schema = this.schemaCache.getSchema(request.getStructure());
            long endSchemaTime = System.nanoTime();
            long schemaTime = endSchemaTime - startSchemaTime;
            logger.trace("Schema call length: " + String.valueOf((double)schemaTime / 1000000000.0) + " seconds");
            List<String> sortableFields = schema.buildSortableFields(request.getFields());
            if (request.getMetadata("order") == null) {
                // SELECT 1,2,3,4,5 from Test ORDER BY 1,2,3,4,5 ASC2
                if (request.getFields() != null) {
                    queryBuilder.append(" ORDER BY ");
                    // Sublist the first 32 sortable fields, because that is the max number
                    // of fields that ORDER BY allows
                    int endOfListOr32 = sortableFields.size() > 32 ? 32 : sortableFields.size();
                    queryBuilder.append(StringUtils.join(sortableFields.subList(0, endOfListOr32),","));
                    queryBuilder.append(" ASC");
                }
            }
            // If the metadata passed is <%=field['1']%>:ASC,<%field['3']:DESC...
            // SELECT 1,2,3,4,5 from Test ORDER BY 1 ASC, 3 DESC, 2,4,5 ASC
            else {
                //Input what to do when the user inputs the metadata order
                String order = request.getMetadata("order");
                List<String> allFields = new ArrayList<String>(request.getFields());
                queryBuilder.append(" ORDER BY ");
                for (Map.Entry<String,String> entry : BridgeUtils.parseOrder(order).entrySet()) {
                    String key = entry.getKey();
                    allFields.remove(key);
                    queryBuilder.append(key + " " + entry.getValue());
                    queryBuilder.append(",");
                }
                ArrayList<String> remainingSortable = schema.buildSortableFields(allFields);
                if (!remainingSortable.isEmpty()) {
                    // Sublist the remaining sortable fields by 10 to stay safely
                    // under the max 32 ORDER BY fields
                    int endOfListOr10 = remainingSortable.size() > 10 ? 10 : remainingSortable.size();
                    queryBuilder.append(StringUtils.join(remainingSortable.subList(0, endOfListOr10),","));
                    queryBuilder.append(" ASC");
                }
                else {
                    queryBuilder.deleteCharAt(queryBuilder.length()-1);
                }
            }

            // Setting the default metadata so that LIMIT and OFFSET can be
            // properly applied
            setDefaultMetadata(request);

            // Uses metadata to add the appropriate parameters to the SOQL statement
            if (Long.parseLong(request.getMetadata("pageSize")) > 0L) {
                queryBuilder.append(" LIMIT ").append(request.getMetadata("pageSize"));
            }
            if (Long.parseLong(request.getMetadata("offset")) > 0L) {
                queryBuilder.append(" OFFSET ").append(request.getMetadata("offset"));
            }
        }
        long endTopTime = System.nanoTime();
        long topTime = endTopTime - startTopTime;
        logger.trace("Sorting time length: " + String.valueOf((double)topTime / 1000000000.0) + " seconds");

        logger.trace("SOQL Query: " + queryBuilder);

        // Returns the query that will then be used to make a HttpGet object in
        // the class that called it
        return queryBuilder.toString();
    }

    /**
     * This method will be called when the result does not have to worry about
     * configuring outputMetadata.
     *
     * @param request
     * @param method
     * @return
     * @throws BridgeError
     */
    private Map<String,Object> parseResponse(BridgeRequest request, String responseJSON, String method) throws BridgeError {
        return parseResponse(request, responseJSON, method, null);
    }

    /**
     * A helper method used to take the JSON that has been retrieved from
     * Salesforce's HTTP Response and convert it into the JSON that will be
     * outputted from this bridge.
     *
     * @param responseJSON
     * @param method
     * @param metadata
     * @return
     * @throws BridgeError
     */
    private Map<String,Object> parseResponse(BridgeRequest request, String responseJSON, String method, Map<String,Long> metadata) throws BridgeError {
        Map<String,Object> data = new LinkedHashMap<String,Object>();
        JSONObject json;
        try {
            json = (JSONObject)JSONValue.parse(responseJSON);
        }
        catch (Exception e) {
            throw new BridgeError("Error: Unable to parse JSON");
        }

        if (method.equals("count")) {
            data.put("count", (Long)json.get("totalSize"));
        }

        else if (method.equals("retrieve")) {
            // Checking to make sure the response only contains one record
            Long recordCount = (Long)json.get("totalSize");
            data.put("record",null);
            if (recordCount > 1L) {
                throw new BridgeError("Multiple results matched an expected single match query");
            }
            else if (recordCount != 0L) {
                // Parsing out the record (and removing the attributes field)
                ArrayList records = (ArrayList)json.get("records");
                JSONObject singleRecord = (JSONObject)JSONValue.parse(records.get(0).toString());
                singleRecord.remove("attributes");
                // Building up the response data
                data.put("record",singleRecord);
            }
        }

        else if (method.equals("search")) {
            // Building the field and record metadata values
            Map<String,Object> splitMetadata = buildSearchOutput(request, json);

            Long size = (Long)json.get("totalSize");
            if (size >= 2000L) {
                throw new BridgeError("Total records greater than 2000. SOQL limit reached.");
            }
            metadata.put("size",size);

            // Building up the response data
            data.put("metadata", metadata);
            data.put("fields", splitMetadata.get("fields"));
            data.put("records", splitMetadata.get("records"));
        }

        return data;
    }

    /**
     * A helper method used to take the JSON that has been retrieved from
     * Salesforce's HTTP Response and convert it into the JSON that will be
     * outputted from this bridge.
     *
     * @param responseJSON
     * @param method
     * @param metadata
     * @return
     * @throws BridgeError
     */
    private void parseError(String responseJSON) throws BridgeError {
        JSONArray jsonArray = (JSONArray)JSONValue.parse(responseJSON);
        JSONObject json = (JSONObject)JSONValue.parse(jsonArray.get(0).toString());

        if (json.get("errorCode").equals("INVALID_FIELD")) {
            logger.debug("Original Error Message: " + json.toString());
            throw new BridgeError("This query contains an invalid field. If you are trying to use a custom field, make sure to append __c to it.");
        }
        else {
            logger.debug("Original Error Message: " + json.toString());
            throw new BridgeError(String.format("%s : %s",json.get("errorCode"), json.get("message")).replace("\n",""));
        }
    }

    /**
     * A helper method used to build up the data output that will be used for
     * the search response. It takes the output that Salesforce returns and
     * separates the fields into an array and the records (without the fields)
     * into an array of arrays. Returns a LinkedHashMap containing the fields
     * and records data.
     *
     * @param jsonOutput
     * @return
     * @throws BridgeError
     */
    private Map<String,Object> buildSearchOutput(BridgeRequest request, JSONObject jsonOutput) {
        logger.trace("Building the fields and records output");

        // Parsing the output JSON
        JSONArray jsonArray = (JSONArray)jsonOutput.get("records");

        List<String> keys;
        keys = request.getFields();

        // Iterating through and creating a Map object containing the records
        List records = new ArrayList();
        for (int i = 0; i<jsonArray.size(); i++) {
            List singleRecord = new ArrayList();
            JSONObject singleRecordJSON = (JSONObject)JSONValue.parse(jsonArray.get(i).toString());
            for (int j = 0; j<keys.size(); j++) {
                singleRecord.add(singleRecordJSON.get(keys.get(j)));
            }
            records.add(singleRecord);
        }

        // Building the metadata containing the fields and records
        Map<String,Object> splitData = new LinkedHashMap<String,Object>();
        splitData.put("fields",keys);
        splitData.put("records",records);

        // Returning the metadata
        return splitData;
    }

    /**
     * A helper method used to set the default metadata values that will be used
     * for the call to Salesforce. If no values have been inputted by the user,
     * the method will set the values at logical default values.
     *
     * @param request
     * @return
     * @throws BridgeError
     */
    private void setDefaultMetadata(BridgeRequest request) throws BridgeError{
        // Intialize the Long variables that will eventually be returned by
        // the metadata
        Long pageSize;
        Long pageNumber;
        Long offset;

        // Converting the inputted metadata from a string to a Long, and if
        // there is no inputted value a logical default value is chosen
        if (request.getMetadata("pageSize") == null) {
            pageSize = 0L;
        } else {
            pageSize = Long.valueOf(request.getMetadata("pageSize"));
        }

        if (request.getMetadata("pageNumber") == null) {
            pageNumber = 1L;
        } else {
            pageNumber = Long.valueOf(request.getMetadata("pageNumber"));
        }

        if (request.getMetadata("offset") == null) {
            offset = ((pageNumber-1)*pageSize);
        } else {
            offset = Long.valueOf(request.getMetadata("offset"));
        }

        // Validate that the inputted metadata is logically usable
        if (request.getMetadata("pageNumber") != null && request.getMetadata("pageSize") == null) {
            throw new BridgeError("Illegal search, the pageNumber metadata value was passed without specifying a pageSize.");
        }
        if (request.getMetadata("pageNumber") == null && request.getMetadata("pageSize") != null) {
            throw new BridgeError("Illegal search, the pageSize metadata value was passed without specifying a pageNumber.");
        }
        if(request.getMetadata("pageSize") != null && request.getMetadata("offset") != null && offset != (pageNumber-1L)*pageSize) {
            throw new BridgeError("Illegal search, the offset does not match the specified pageSize and pageNumber.");
        }
        // Validate that the offset is not over 2000, which is the limit for
        // SOQL queries
        if (offset >= 2000L) {
            throw new BridgeError("Offset is calculated to be greater than or equal to 2000. SOQL Query Limit reached.");
        }

        Map<String,String> metadata;
        // Building up and returning the metadata
        if (request.getMetadata() != null) {
            metadata = request.getMetadata();
        }
        else {
            metadata = new LinkedHashMap();
        }
        metadata.put("pageNumber",pageNumber.toString());
        metadata.put("pageSize",pageSize.toString());
        metadata.put("offset",offset.toString());
        request.setMetadata(metadata);
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

           /**
       * Returns the string value of the object.
       * <p>
       * If the value is not a String, a JSON representation of the object will be returned.
       *
       * @param value
       * @return
       */
    private String toString(Object value) {
        String result = null;
        if (value != null) {
            if (String.class.isInstance(value)) {
                result = (String)value;
            } else {
                result = JSONValue.toJSONString(value);
            }
        }
        return result;
     }

    public String getApiVersion() {
        return this.apiVersion;
    }

}
