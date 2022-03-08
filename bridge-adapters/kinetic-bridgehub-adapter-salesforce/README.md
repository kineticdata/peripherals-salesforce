# Salesforce Bridgehub Adapter
This bridge adapter is used for setting up bridges to interact with Salesforce instances through the [Rest API](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_rest.htm).

## Configuration Values
| Name                    | Description | Example Value |
| :---------------------- | :------------------------- | :------------------------- |
| Username                | The username that will be used to access the Salesforce instance | user@acme.com |
| Password                | The password that is associated with the username | secret-password |
| Security Token          | The security token associated with the account used for record retrieval. |  |
| Client ID               | The client id of the account that will be used for record retrieval. |  |
| Client Secret           | The client secret of the account that will be used for record retrieval. |  |
| Salesforce Instance     | The Salesforce instance name | https://MyDomainName.my.salesforce.com |

## Supported Structures
| Name | Description | Example |
| :---------------------- | :------------------------- | :------------------------- |
| ${object} | The Structure is used in a [SOQL query](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_query.htm) | `SELECT * FROM ${object}` |

## Attributes and Fields
* Only Fields configured in the Attributes mapping will be returned from the Salesforce request.

## Qualifications (Query)
* This adapter supports only queries to the [Salesforce SOQL endpoint](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_query.htm). 

## Notes
* To reset the Security Token value, log into Salesforce using the desired account and navigate to Setup -> My Personal Information -> Reset Security Token. A new token will be emailed to you.