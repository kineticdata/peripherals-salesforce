# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class SalesforceObjectCreateV1

  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)

    # Store the info values in a Hash of info names to values.
    @info_values = {}
    REXML::XPath.each(@input_document,"/handler/infos/info") { |item|
      @info_values[item.attributes['name']] = item.text
    }

    # Retrieve all of the handler parameters and store them in a hash attribute
    # named @parameters.
    @parameters = {}
    REXML::XPath.match(@input_document, 'handler/parameters/parameter').each do |node|
      @parameters[node.attribute('name').value] = node.text.to_s
    end

    @enable_debug_logging = @info_values['enable_debug_logging'].downcase == 'yes' ||
                            @info_values['enable_debug_logging'].downcase == 'true'
    puts "Parameters: #{@parameters.inspect}" if @enable_debug_logging

    @api_version = "37.0"
  end

  def execute()
    auth_info = authorize(
      @info_values['username'],
      @info_values['password'],
      @info_values['security_token'],
      @info_values['client_id'],
      @info_values['client_secret']
    )
    access_token = auth_info['access_token']
    salesforce_instance = auth_info['instance_url']

    puts "Attempting to create a(n) #{@parameters['type']} using the JSON Object: #{@parameters['json_body']}" if @enable_debug_logging
    begin
      resp = RestClient.post("#{salesforce_instance}/services/data/v#{@api_version}/sobjects/#{@parameters['type']}",
        @parameters['json_body'], :content_type => "application/json", :accept => :json, :authorization => "OAuth #{access_token}")
    rescue RestClient::BadRequest => error
      puts "Error encountered while attempting to create a(n) #{@parameters['type']}:\n#{error.http_body}"
      raise error.http_body
    rescue RestClient::ResourceNotFound => error
      puts "404 Not Found: Check to see if '#{@parameters['type']}' is a valid Salesforce object." if @enable_debug_logging
      raise "404 Not Found: Check to see if '#{@parameters['type']}' is a valid Salesforce object. (Actual error: #{error.http_body})"
    rescue RestClient::Exception => error
      raise error.http_body
    end

    json = JSON.parse(resp)
    raise json["errors"].inspect if json["success"] == false

    id = json["id"]
    puts "#{@parameters['type']} '#{id}' successfully created" if @enable_debug_logging

    # Return results which is an XML String
    <<-RESULTS
    <results>
      <result name="Id">#{escape(id)}</result>
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
end
