package org.httpkit.server;

import java.util.Map;

@SuppressWarnings({"rawtypes"})
public interface IRubyHandler {
	Map call(Map req);
	Map call(String req);
	Map call(Object req);
}
