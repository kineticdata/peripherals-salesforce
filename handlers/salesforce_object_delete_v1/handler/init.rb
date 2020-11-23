# Require the dependencies file to load the vendor libraries
require File.expand_path(File.join(File.dirname(__FILE__), "dependencies"))

class SalesforceObjectDeleteV1
  def initialize(input)
    # Set the input document attribute
    @input_document = REXML::Document.new(input)

    # Store the info values in a Hash of info names to values.
    @info_values = {}
    REXML::XPath.each(@input_document, "/handler/infos/info") { |item|
      @info_values[item.attributes["name"]] = item.text
    }

    # Retrieve all of the handler parameters and store them in a hash attribute
    # named @parameters.
    @parameters = {}
    REXML::XPath.match(@input_document, "handler/parameters/parameter").each do |node|
      @parameters[node.attribute("name").value] = node.text.to_s
    end

    @enable_debug_logging = @info_values["enable_debug_logging"].downcase == "yes" ||
                            @info_values["enable_debug_logging"].downcase == "true"
    puts "Parameters: #{@parameters.inspect}" if @enable_debug_logging

    @api_version = "37.0"
  end

  def execute()
    raise "Id cannot be left blank" if @parameters["id"].to_s.empty?

    auth_info = authorize(
      @info_values["username"],
      @info_values["password"],
      @info_values["security_token"],
      @info_values["client_id"],
      @info_values["client_secret"],
      false
    )
    access_token = auth_info["access_token"]
    salesforce_instance = auth_info["instance_url"]

    ## Get the salesforce contact information associated with the contact id
    puts "Attempting to delete #{@parameters["type"]} '#{@parameters["id"]}'" if @enable_debug_logging
    begin
      resp = RestClient.delete("#{salesforce_instance}/services/data/v#{@api_version}/sobjects/#{@parameters["type"]}/#{@parameters["id"]}",
                               :content_type => "application/x-www-form-urlencoded", :accept => :json, :authorization => "OAuth #{access_token}")
    rescue RestClient::BadRequest => error
      puts "Error encountered while attempting to delete the id:\n#{error.http_body}"
      raise error.http_body
    rescue RestClient::ResourceNotFound => error
      puts "Not found exception encountered while attempting to delete id. Most likely (but not only) reason for this to happen is the #{@parameters["type"]} '#{@parameters["id"]}' doesn't exist\n#{error.http_body}"
      raise error.http_body
    rescue RestClient::Exception => error
      raise error.http_body
    end
    puts "The #{@parameters["type"]} '#{@parameters["id"]}' was deleted" if @enable_debug_logging

    # Return results which is an XML String
    "<results />"
  end

  def authorize(username, password, security_token, client_id, client_secret, sandbox)
    # Setting up the hash map that will be turned into json and passed to
    # Salesforce to authenticate
    params = {
      :username => username,
      :password => password + security_token,
      :client_id => client_id,
      :client_secret => client_secret,
      :grant_type => "password",
    }
    begin
      resp = RestClient.post(
        "https://#{sandbox ? "test" : "login"}.salesforce.com/services/oauth2/token",
        params,
        :content_type => "application/x-www-form-urlencoded",
        :accept => :json,
      )
      auth_info = JSON.parse(resp)
    rescue RestClient::BadRequest => error
      error_json = JSON.parse(error.http_body)
      # Try again if received the invalid_grant response using the sandbox URL (test.salesforce.com)
      if !sandbox && error_json["error"] == "invalid_grant"
        auth_info = authorize(username, password, security_token, client_id, client_secret, true)
      else
        puts error.inspect if @enable_debug_logging
        raise StandardError, error_json["error_description"].to_s
      end
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
  ESCAPE_CHARACTERS = { "&" => "&amp;", ">" => "&gt;", "<" => "&lt;", '"' => "&quot;" }
end
