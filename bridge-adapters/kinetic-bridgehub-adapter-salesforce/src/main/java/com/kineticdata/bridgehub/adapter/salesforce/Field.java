package com.kineticdata.bridgehub.adapter.salesforce;

import java.util.LinkedHashMap;

/**
 * Field is a simple class that holds the data for a Salesforce field object.
 * It implements just simple getter methods so that the name, type, and
 * sortability of the field can be retrieved. 
 */
public class Field {
    /*---------------------------------------------------------------------------------------------
     * INSTANCE VARIABLES
     *-------------------------------------------------------------------------------------------*/
    
    /**
     * The name of the field.
     */
    private String name;
    
    /**
     *  The type of the field.
     */
    private String type;
    
    /**
     * A boolean value that stores if the field is sortable or not (true for
     * sortable).
     */
    private Boolean sortable;

    /*---------------------------------------------------------------------------------------------
     * CONSTRUCTOR
     *-------------------------------------------------------------------------------------------*/
    
    /**
     * Initialize a field object using the values given to the constructor.
     */
    public Field(String name, String type, Boolean sortable) {
        // Configuring the local variables
        this.name = name;
        this.type = type;
        this.sortable = sortable;
    }

    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public Boolean isSortable() {
        return this.sortable;
    }
    
    public LinkedHashMap<String,String> getField() {
        LinkedHashMap<String,String> fieldInfo = new LinkedHashMap();
        fieldInfo.put("name",this.name);
        fieldInfo.put("type",this.type);
        fieldInfo.put("sortable",this.sortable.toString());
        return fieldInfo;
    }
}