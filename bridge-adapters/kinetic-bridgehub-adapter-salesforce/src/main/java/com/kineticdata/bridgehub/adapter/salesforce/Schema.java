package com.kineticdata.bridgehub.adapter.salesforce;

import com.kineticdata.bridgehub.adapter.BridgeError;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
/**
 * Field is a simple class that holds the data for a Salesforce field object.
 * It implements just simple getter methods so that the name, type, and
 * sortability of the field can be retrieved. 
 */
public class Schema {
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(SalesforceAdapter.class);
    /*---------------------------------------------------------------------------------------------
     * INSTANCE VARIABLES
     *-------------------------------------------------------------------------------------------*/
    
    /**
     * The name of the schema.
     */
    private String name;
    
    /**
     *  A list of fields that are contained within the schema.
     */
    private ConcurrentHashMap<String,Field> fieldMap;

    /*---------------------------------------------------------------------------------------------
     * CONSTRUCTOR
     *-------------------------------------------------------------------------------------------*/
    
    /**
     * Initialize a field object using the values given to the constructor.
     */
    public Schema(String name) {
        // Configuring the local variables
        this.name = name;
        this.fieldMap = new ConcurrentHashMap();
    }

    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    /**
     * Adds a precreated field object to 
     */
    public void addField(String name, Field field) {
        this.fieldMap.put(name, field);
    }

    /**
     * Iterates through the imported arraylist and then returns a list of only
     * the sortable fields
     */
    public ArrayList<String> buildSortableFields(List<String> fields) {
        ArrayList<String> sortableFields = new ArrayList();
        for (String name : fields) {
            if (this.fieldMap.get(name) != null) {
                if (this.fieldMap.get(name).isSortable() == true) {
                    sortableFields.add(name);
                }
            }
        }
        return sortableFields;
    }

    /**
     * Returns the field information for debugging purposes. In this case the
     * sortability boolean will be changed to a string for simplicities sake
     */
    public HashMap<String,String> getField(String name) throws BridgeError{
        HashMap<String,String> fieldInfo = new HashMap<String,String>();
        Field field = this.fieldMap.get(name);
        try {
            fieldInfo.put("Name",field.getName());
            fieldInfo.put("Type",field.getType());
            fieldInfo.put("Sortable",field.isSortable().toString());
        }
        catch (Exception e) {
            throw new BridgeError("Was not able to retrieve information from Field class");
        }
        return fieldInfo;
    }

    /**
     * Get the schema map for debugging reasons.
     */
    public ConcurrentHashMap<String,Field> getSchema() {
        return this.fieldMap;
    }
    
    public String getName() {
        return this.name;
    }
}