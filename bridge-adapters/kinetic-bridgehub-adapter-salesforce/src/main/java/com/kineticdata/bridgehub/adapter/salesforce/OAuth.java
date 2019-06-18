/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kineticdata.bridgehub.adapter.salesforce;

import com.kineticdata.bridgehub.adapter.BridgeError;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;
import org.slf4j.LoggerFactory;

/**
 * OAuth is a class that manages authentication for the bridge with Salesforce
 * by returning the access token that bridge will then use to authenticate its
 * requests.
 */
public class OAuth {
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(SalesforceAdapter.class);

     /*---------------------------------------------------------------------------------------------
     * INSTANCE VARIABLES
     *-------------------------------------------------------------------------------------------*/

     /**
     * The security token associated with the account used for record
     * retrieval. To reset this value, log into Saleforce using the desired
     * account and navigate to Setup -> My Personal Information -> Reset
     * Security Token. A new token will be emailed to you.
     */
    private String token;

    /**
     *  The username of the account that will be used for record retrieval.
     */
    private String username;

    /**
     * The password of the account that will be used for record retrieval.
     */
    private String password;

    /**
     * The client id of the account that will be used for record retrieval.
     * Found by creating a connected App.
     */
    private String clientId;

    /**
     * The client secret of the account that will be used for record retrieval.
     * Found by creating a connected App.
     */
    private String clientSecret;

    /**
     * The salesforce instance that your account is tied to. Can be found to the
     * left of the salesforce url when you are logged into their website. For
     * example, https://na15.salesforce.com, the instance is na15
     */
    private String salesforceInstance;

    /*---------------------------------------------------------------------------------------------
     * CONSTRUCTOR
     *-------------------------------------------------------------------------------------------*/

    /**
     * Initialize a new OAuth object that takes the information of the user so
     * that a new access token can be retrieved when the old one expires.
     */

    public OAuth(String username, String password, String token,
        String clientId, String clientSecret, String salesforceInstance) {
        this.username = username;
        this.password = password;
        this.token = token;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.salesforceInstance = salesforceInstance;
    }

    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    /**
     * Queries Salesforce to authenticate using the information given to the
     * bridge upon initialization. If successful, the access token recieved from
     * Salesforce will be placed in the class variable accessToken so that it
     * can be accessed by the various methods.
     *
     * @return
     * @throws BridgeError
     */
    public String getAccessToken() throws BridgeError {
        logger.trace("Authenticating with Salesforce to get new Access Token");

        //Initialize result data
        String accessToken;

        // Making a call to Salesforce using HttpClient to get the access
        // token from Salesforce
        HttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(String.format("https://%s.salesforce.com/services/oauth2/token",this.salesforceInstance));
        try {
            // Setting up the parameters that will be passed with the HTTP
            // POST request
            List<NameValuePair> parameters = new ArrayList<NameValuePair>(1);
            parameters.add(new BasicNameValuePair("grant_type","password"));
            parameters.add(new BasicNameValuePair("client_id",this.clientId));
            parameters.add(new BasicNameValuePair("client_secret",this.clientSecret));
            parameters.add(new BasicNameValuePair("username",this.username));
            parameters.add(new BasicNameValuePair("password",this.password + this.token));
            post.setEntity(new UrlEncodedFormEntity(parameters));

            // Executing the POST call and then retrieving Salesforce's response
            HttpResponse response = client.execute(post);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line;
            String output = "";
            while ((line = rd.readLine()) != null) {
                output = output + line;
            }

            ContainerFactory containerFactory = new ContainerFactory(){
                public List creatArrayContainer() {return new LinkedList();}
                public Map createObjectContainer() {return new LinkedHashMap();}
            };

            // Attempt to parse the result data
            Map<String,Object> data;
            try {
                JSONParser parser = new JSONParser();
                data = (Map)parser.parse(output, containerFactory);
                logger.debug("Received authentication response:\n"+output);
                accessToken = (String)data.get("access_token");
            }
            catch (Exception e) {
                logger.error("Unable to parse response:\n"+output);
                throw new BridgeError("Unable to parse JSON response");
            }
        }
        catch (IOException e) {
            throw new BridgeError("Unable to make a connection to properly execute the"
                    + "query to Salesforce");
        }
        if (accessToken == null) {
            throw new BridgeError("Authentication Failed: Unable to retrieve access token");
        }
        return accessToken;
    }

    /**
     * An accessor method that returns the current salesforce instance of the
     * oauth object
     */
    public String getSalesforceInstance() {
        return this.salesforceInstance;
    }
}