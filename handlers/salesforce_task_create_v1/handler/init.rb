require File.expand_path(File.join(File.dirname(__FILE__), 'dependencies'))

class SalesforceTaskCreateV1
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
    # Validate that opportunity id and account id are not both included
    if !@parameters['account_id'].empty? && !@parameters['opportunity_id'].empty?
      raise "'Opportunity Id' and 'Account Id' cannot be included at the same time."
    end

    auth_info = authorize(
      @info_values['username'],
      @info_values['password'],
      @info_values['security_token'],
      @info_values['client_id'],
      @info_values['client_secret']
    )
    access_token = auth_info['access_token']
    salesforce_instance = auth_info['instance_url']

    ## Get the salesforce user id associated with the requester email
    puts "Attempting to retrieve the User Id for '#{@parameters['requester_email']}'" if @enable_debug_logging
    query = "SELECT Id from User WHERE Email='#{@parameters['requester_email']}'"
    begin
      resp = RestClient.get("#{salesforce_instance}/services/data/v#{@api_version}/query?q=#{URI.encode(query)}",
        :content_type => "application/x-www-form-urlencoded", :accept => :json, :authorization => "OAuth #{access_token}")
      json = JSON.parse(resp)
    rescue RestClient::BadRequest => error
      puts "Error encountered while attempting to retrieve a user id:\n#{error.http_body}"
      raise JSON.parse(error.http_body)[0]["message"].to_s
    end

    records = json["records"]
    raise "A user with the email '#{@parameters['requester_email']}' could not be found." if records.empty?
    user_id = json["records"][0]["Id"]

    puts "User Id '#{user_id}' successfully retrieved for '#{@parameters['requester_email']}'" if @enable_debug_logging
    puts "Building the Task JSON Object" if @enable_debug_logging
    # building up the task object
    task_object = {
      "OwnerId"      => user_id,
      "Subject"      => @parameters['subject'],
      "Description"  => @parameters['comment'],
      "WhoId"        => @parameters['contact_id'],
      "WhatId"       => !@parameters['account_id'].empty? ? @parameters['account_id'] : @parameters['opportunity_id'],
      "ActivityDate" => "#{DateTime.now.strftime('%Y-%m-%d')}",
      "Status"       => "Completed",
    }

    puts "Attempting to create a Task using the built Task JSON Object: #{task_object.to_json}" if @enable_debug_logging
    begin
      resp = RestClient.post("#{salesforce_instance}/services/data/v#{@api_version}/sobjects/Task", task_object.to_json,
        :content_type => "application/json", :accept => :json, :authorization => "OAuth #{access_token}")
    rescue RestClient::BadRequest => error
      puts "Error encountered while attempting to create the Task:\n#{error.http_body}"
      error_json = JSON.parse(error.http_body)
      raise JSON.parse(error.http_body)[0]["message"].to_s
    end

    json = JSON.parse(resp)
    raise json["errors"].inspect if json["success"] == false

    task_id = json["id"]
    puts "Task '#{task_id}' successfully created" if @enable_debug_logging

    <<-RESULTS
    <results>
      <result name="Task Id">#{escape(task_id)}</result>
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
    string.to_s.gsub(/[&"><]/) { |special| ESCAPE_CHARACTERS[special] } if string
  end
  # This is a ruby constant that is used by the escape method
  ESCAPE_CHARACTERS = {'&'=>'&amp;', '>'=>'&gt;', '<'=>'&lt;', '"' => '&quot;'}
end
