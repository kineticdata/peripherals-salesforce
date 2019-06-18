# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class SalesforceCaseCreateV2
  # Prepare for execution by instantiating the proper instance variables.
  # This method sets the following instance variables:
  # * @input_document - A REXML::Document object that represents the input XML.
  # * @instance - A String that is necessary for building REST API calls, it is
  #   a result returned by the login() method.  The instance is used to build
  #   the URL for the REST API calls.
  # * @session_id - A String that is necessary for building REST API calls, it
  #   is a result returned by the login() method.  The session id must be passed
  #   as a header to REST API calls.
  #
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  #
  # ==== Parameters
  # * +input+ - The String of Xml that was built by evaluating the node.xml
  #   handler template.
  def initialize(input)
    # Set the @input_document attribute
    @input_document = REXML::Document.new(input)

    # Store the info values in a Hash of info names to values.
    @info_values = {}
    REXML::XPath.each(@input_document,"/handler/infos/info") { |item|
      @info_values[item.attributes['name']] = item.text
    }

    # Store the parameters from the node.xml in a Hash attribute named @parameters
    @parameters = {}
    REXML::XPath.match(@input_document, '/handler/parameters/parameter').each do |node|
      @parameters[node.attribute('name').value] = node.text.to_s
    end
    @api_version = "37.0"
  end

  # Creates a Salesforce Case record with the given parameters.
  #
  # This is a required method that is automatically called by the Kinetic Task
  # Engine.
  #
  # ==== Returns
  # An XML formatted String representing the return variable results.
  def execute
    #  Get auth token
    auth_info = authorize(
      @info_values['username'],
      @info_values['password'],
      @info_values['security_token'],
      @info_values['client_id'],
      @info_values['client_secret']
    )
    access_token = auth_info['access_token']
    salesforce_instance = auth_info['instance_url']

    puts "Building the case JSON Object" if @enable_debug_logging
    case_object = {
      "accountid"         => @parameters['accountid'],
      "contactid"         => @parameters['contactid'],
      "type"              => @parameters['type'],
      "reason"            => @parameters['reason'],
      "status"            => @parameters['status'],
      "priority"          => @parameters['priority'],
      "origin"            => @parameters['origin'],
      "subject"           => @parameters['subject'],
      "description"       => @parameters['description']
    }

    puts "Attempting to create a Task using the built Task JSON Object: #{case_object.to_json}" if @enable_debug_logging
    begin
      resp = RestClient.post("#{salesforce_instance}/services/data/v#{@api_version}/sobjects/Case", case_object.to_json,
        :content_type => "application/json", :accept => :json, :authorization => "OAuth #{access_token}")
    rescue RestClient::BadRequest => error
      puts "Error encountered while attempting to creat the Case: \n#{error.http_body}"
      error_json = JSON.parse(error.http_body)
      raise JSON.parse(error.http_body)
    end

    json = JSON.parse(resp)
    raise json["errors"].inspect if json["success"] == false

    case_id = json["id"]

    <<-RESULTS
    <results>
      <result name="Case Id">#{escape(case_id)}</result>
    </results>
    RESULTS

  end

  def authorize(username,password,security_token,client_id,client_secret)
    # Setting up the hash map that will be turned into json and passed to
    # Salesforce to authenticate
    params = {
      :username => username,
      :password => password+security_token,
      :client_id => client_id,
      :client_secret => client_secret,
      :grant_type => "password"
    }
    begin
      resp = RestClient.post(
        "https://login.salesforce.com/services/oauth2/token",
        params,
        :content_type => "application/x-www-form-urlencoded",
        :accept => :json
      )
      auth_info = JSON.parse(resp)
    rescue RestClient::BadRequest => error
      error_json = JSON.parse(error.http_body)
      raise StandardError, error_json['error_description'].to_s
    end

    return auth_info
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
