# Salesforce Bridgehub Adapter
This bridge adapter is used for setting up bridges to interact with Salesforce instances through the [Rest API](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_rest.htm).

## Configuration Values
| Name                    | Description | Example Value |
| :---------------------- | :------------------------- | :------------------------- |
| Username                | The username that will be used to access the Salesforce instance | user@acme.com |
| Password                | The password that is associated with the username | secret-password |
| Security Token          | The security token associated with the account used for record retrieval. | yhjo6HALS98uqwfrHIAUWLF3a |
| Client ID               | The client id of the account that will be used for record retrieval. | 3MASDFojlkandfs24j567afhisAEHSuut7quyteAGHqLY_Afht29ehr |
| Client Secret           | The client secret of the account that will be used for record retrieval. | 6835098327142538245 |
| Salesforce Instance     | The Salesforce instance name | na12 |

## Supported Structures
| Name | Description | Example |
| :---------------------- | :------------------------- | :------------------------- |
| ${object} | The Structure is used in a [SOQL query](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_query.htm) | `SELECT * FROM ${object}` |

## Attributes and Fields
* Only Fields configured in the Attributes mapping will be returned from the Salesforce request.

## Qualifications (Query)
* When building qualifications, it is important to realize that not all fields are sortable. If they are sortable, they will have "Sort" listed under the properties in the field documentation
* Also, make sure that you are using the field name and not the field label. (Hint: If there is a space in the field you are trying to use, you are likely using the field label.
* This adapter supports only queries to the [Salesforce SOQL endpoint](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_query.htm). 

## Notes
* To reset the Security Token value, log into Salesforce using the desired account and navigate to Setup -> My Personal Information -> Reset Security Token. A new token will be emailed to you.
* Obtaining a Security Token by navigating to setup which is located in the dropdown menu on the top right of the page, go to Reset My Security Token under My Personal Information.
Obtaining the Client Id and Client Secret:
* On the setup page again, navigate to Manage Apps and click New on the Connected Apps table.
* After giving your app a name and contact information, click the checkbox for Enable OAuth Settings.
* The callback URL will not have to be used, so you can just give it any https URL (I use "https://auth").
* Under selected OAuth scopes, add Access and manage your data (api) to your Selected OAuth Scopes and save.
* This will bring you to the page for your newly created app, where you can get the Consumer Key (Client Id) and Consumer Secret (Client Secret), which means you now have all the information to configure your bridge!