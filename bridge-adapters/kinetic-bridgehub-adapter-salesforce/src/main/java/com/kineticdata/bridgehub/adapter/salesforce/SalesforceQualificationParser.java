package com.kineticdata.bridgehub.adapter.salesforce;

import com.kineticdata.bridgehub.adapter.QualificationParser;

/**
 *
 */
public class SalesforceQualificationParser extends QualificationParser {
    public String encodeParameter(String name, String value) {
        return value;
    }
}
