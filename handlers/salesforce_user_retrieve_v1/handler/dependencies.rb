# Load the ruby JSON library unless it has already been loaded.  This prevents
# multiple handlers using the same library from causing problems.
if KineticTask::VERSION.split(".").first.to_i < 4
  if not defined?(JSON)
    # Calculate the location of this file
    handler_path = File.expand_path(File.dirname(__FILE__))
    # Calculate the location of our library and add it to the Ruby load path
    library_path = File.join(handler_path, 'vendor/json-1.4.6-java/lib')
    $:.unshift library_path
    # Require the library
    require 'json'
  end

  # Validate the the loaded JSON library is the library that is expected for
  # this handler to execute properly.
  if not defined?(JSON::VERSION)
    raise "The JSON class does not define the expected VERSION constant."
  elsif JSON::VERSION != '1.4.6'
    raise "Incompatible library version #{JSON::VERSION} for JSON.  Expecting version 1.4.6."
  end

  # Load the JRuby Open SSL library unless it has already been loaded.  This
  # prevents multiple handlers using the same library from causing problems.
  if not defined?(Jopenssl)
    # Calculate the location of this file
    handler_path = File.expand_path(File.dirname(__FILE__))
    # Calculate the location of our library and add it to the Ruby load path
    library_path = File.join(handler_path, 'vendor/jruby-openssl-0.6/lib')
    $:.unshift library_path
    # Require the library
    require 'openssl'
    # Require the version constant
    require 'jopenssl/version'
  end

  # Validate the the loaded openssl library is the library that is expected for
  # this handler to execute properly.
  if not defined?(Jopenssl::Version::VERSION)
    raise "The Jopenssl class does not define the expected VERSION constant."
  elsif Jopenssl::Version::VERSION != '0.6'
    raise "Incompatible library version #{Jopenssl::Version::VERSION} for Jopenssl.  Expecting version 0.6."
  end
end

# Require the necessary standard libraries
require 'net/https'
require 'uri'
require 'rexml/document'
