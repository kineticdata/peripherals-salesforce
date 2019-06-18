# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class SalesforceContactRetrieveV1
  # Prepare for execution by instantiating the proper instance variables.
  # This method sets the following instance variables:
  # * @input_document - A REXML::Document object that represents the input XML.
  # * @parameters - A Hash that contains the parameters that will be used to retrieve
  #   the Salesforce record.
  #
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  #
  # ==== Parameters
  # * +input+ - The String of XML that was built by evaluating the node.xml
  #   handler template.
  def initialize(input)
    # Set the @input_document attribute
    @input_document = REXML::Document.new(input)

    # Store the parameters from the node.xml in a Hash attribute named @parameters
    @parameters = {}
    REXML::XPath.match(@input_document, '/handler/parameters/parameter').each do |node|
      @parameters[node.attribute('name').value] = node.text
    end
  end

  # Retrieves a Salesforce Contact record with the given parameters.
  # This method sets the following instance variables:
  # * @instance - A String that is necessary for building REST API calls, it is
  #   a result returned by the login() method.  The instance is used to build the
  #   URL for the REST API calls.
  # * @session_id - A String that is necessary for building REST API calls, it is
  #   a result returned by the login() method.  The session id must be passed as a
  #   header to REST API calls.
  #
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  #
  # ==== Returns
  # An XML formatted String representing the return variable results.
  def execute()
    # Retrieve the @instance and @session_id attributes that are necessary for making
    # the REST API calls
    @instance, @session_id = login(
      get_info_value(@input_document, 'username'),
      get_info_value(@input_document, 'password'),
      get_info_value(@input_document, 'token')
    )
    
    # Build the headers that will be included in the REST API request.  Here we
    # use the @session_id instance variable for Authorization.  And Content-Type
    # is set to "application/json" so the response will be a JSON string.
    headers = {
      "Authorization" => "OAuth #{@session_id}",
      "Content-Type"  => "application/json"
    }
    
    # Build and send the REST API request.  The @instance instance variable is used
    # to construct the URL.  The request is also sent as GET and the headers Hash
    # is attached to the request.
    uri = URI.parse("https://#{@instance}.salesforce.com/services/data/v20.0/sobjects/Contact/#{@parameters['contact_id']}")
    http = Net::HTTP.new(uri.host, uri.port)
    http.use_ssl = true
    http.verify_mode = OpenSSL::SSL::VERIFY_PEER
    http.ca_file = File.join(File.dirname(__FILE__), 'resources', 'cacert.pem')
    response = http.send_request('GET', uri.path, nil, headers)

    # Convert the response.body (a JSON String) to a Hash with the JSON.parse()
    # method.
    result = JSON.parse(response.body)

    # If success is not true or the result is not even a Hash, we raise an exception.
    if result.is_a?(Array) && result.first.has_key?('errorCode')
      raise "An error was received from the REST API request #{result.inspect}"
    end

    # Return results which is an XML String
    <<-RESULTS
    <results>
        <result name="Salutation">#{result['Salutation']}</result>
        <result name="First Name">#{result['FirstName']}</result>
        <result name="Last Name">#{result['LastName']}</result>
        <result name="Title">#{result['Title']}</result>
        <result name="Department">#{result['Department']}</result>
        <result name="Mailing Street">#{result['MailingStreet']}</result>
        <result name="Mailing City">#{result['MailingCity']}</result>
        <result name="Mailing State/Province">#{result['MailingState']}</result>
        <result name="Mailing Zip/Postal Code">#{result['MailingPostalCode']}</result>
        <result name="Mailing Country">#{result['MailingCountry']}</result>
        <result name="Phone">#{result['Phone']}</result>
        <result name="Home Phone">#{result['HomePhone']}</result>
        <result name="Mobile">#{result['MobilePhone']}</result>
        <result name="Fax">#{result['Fax']}</result>
        <result name="Email">#{result['Email']}</result>
        <result name="Description">#{result['Description']}</result>
    </results>
    RESULTS
  end


  # Returns the instance and session_id which should be set to the @instance and
  # @session_id instance variables of this handler in the initialize() method.
  #
  # This method uses the Salesforce SOAP API to login and retrieve the values that
  # are necessary for making REST API requests.  A Hash of headers for the SOAP
  # request is built.  A String of XML for the SOAP request is also built, this
  # string includes the username, password, and token parameters passed to this
  # function.  Then the SOAP API request is built and sent via the POST method
  # along with the headers Hash and data XML String.
  #
  # The response is an XML String that we parse for the instance (which is part
  # of the server URL) and the session id.  These two values are returned by this
  # method
  #
  # ==== Parameters
  # * username (String) - The username of the Salesforce account that this handler
  # will use for authentication.
  # * password (String) - The password of the Salesforce account that this handler
  # will use for authentication.
  # * token (String) - The security token of the Salesforce account that this handler
  # will use for authentication.
  #
  def login(username, password, token)
    # Build the headers that will be included in the SOAP API login request.
    headers = {
      "Content-Type" => "text/xml;charset=UTF-8",
      "SOAPAction"   => "login"
    }

    # Build the XML String that will be included in the SOAP API request.  This
    # String should not be altered as it will cause the SOAP API login request to
    # fail.
    data = <<-DATA
<?xml version="1.0" encoding="utf-8" ?>
<env:Envelope xmlns:xsd="http://www.w3.org/2001/XMLSchema"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:env="http://schemas.xmlsoap.org/soap/envelope/">
  <env:Body>
    <n1:login xmlns:n1="urn:partner.soap.sforce.com">
      <n1:username>#{username}</n1:username>
      <n1:password>#{password}#{token}</n1:password>
    </n1:login>
  </env:Body>
</env:Envelope>
    DATA

    # Build and send the SOAP API request.  The request is sent as a POST and the
    # the headers and data are sent with the request.
    uri = URI.parse("https://login.salesforce.com/services/Soap/u/20.0")
    http = Net::HTTP.new(uri.host, uri.port)
    http.use_ssl = true
    http.verify_mode = OpenSSL::SSL::VERIFY_PEER
    http.ca_file = File.join(File.dirname(__FILE__), 'resources', 'cacert.pem')
    response = http.send_request('POST', uri.path, data, headers)

    # Parse the response body for the session id and the instance values.
    xml_doc = REXML::Document.new(response.body)
    server_url = REXML::XPath.first(xml_doc, "//result/serverUrl").text
    session_id = REXML::XPath.first(xml_doc, "//result/sessionId").text
    instance = server_url.gsub(/.*:\/\/(.*?)\..*/, '\1')

    # Return the instance and session_id variables that will be used in calls to
    # the REST API.
    return instance, session_id
  end

  # This is a template method that is used to escape results values (returned in
  # execute) that would cause the XML to be invalid.  This method is not
  # necessary if values do not contain character that have special meaning in
  # XML (&, ", <, and >), however it is a good practice to use it for all return
  # variable results in case the value could include one of those characters in
  # the future.  This method can be copied and reused between handlers.
  def escape(string)
    # Globally replace characters based on the ESCAPE_CHARACTERS constant
    string.to_s.gsub(/[&"><]/) { |special| ESCAPE_CHARACTERS[special] }
  end
  # This is a ruby constant that is used by the escape method
  ESCAPE_CHARACTERS = {'&'=>'&amp;', '>'=>'&gt;', '<'=>'&lt;', '"' => '&quot;'}

  # This is a sample helper method that illustrates one method for retrieving
  # values from the input document.  As long as your node.xml document follows
  # a consistent format, these type of methods can be copied and reused between
  # handlers.
  def get_info_value(document, name)
    # Retrieve the XML node representing the desird info value
    info_element = REXML::XPath.first(document, "/handler/infos/info[@name='#{name}']")
    # If the desired element is nil, return nil; otherwise return the text value of the element
    info_element.nil? ? nil : info_element.text
  end
end