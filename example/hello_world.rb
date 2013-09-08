class HelloWorld
  def call(request)
    body = "<html><p>Hello!</p></html>"

    # headers = ["Content-Type", "text/html"].to_java(:string)
    # headers = Java::ClojureLang::PersistentHashMap.create(headers)

    # resp = [
    #       Java::ClojureLang::Keyword.intern("status")  , 200,
    #       Java::ClojureLang::Keyword.intern("headers") , headers,
    #       Java::ClojureLang::Keyword.intern("body")    , body
    #     ].to_java
    # return Java::ClojureLang::PersistentHashMap.create(resp)

    return [200, {"Content-Type" => "text/html"}, [body]]
  end
end
