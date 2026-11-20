/* ***************************************************************
JSONActionRunner.java
Copyright (C) 2026  Brendon Upson
http://www.wnc.net.au info@wnc.net.au

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

 *************************************************************** */

package puakma.addin.http.action;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import puakma.system.ActionRunner;
import puakma.system.Document;
import puakma.system.DocumentFileItem;
import puakma.system.DocumentItem;
import puakma.system.FileData;
import puakma.util.Util;

/**
 * This class is overridden by a Tornado action to make developing REST apis simpler.
 * Loosely based on the ASP.NET Core Minimal APIs, but using plain Java rather than annotations.
 * <p>
 * The developer subclasses this class, maps http verbs and URI templates to methods in
 * {@link #buildMap()}, then writes those methods. The server uses reflection to call the method
 * matching the incoming request. eg for an action design element named "StaffAPI":
 * <pre>
 * public class StaffAPI extends JSONActionRunner
 * {
 *   protected void buildMap()
 *   {
 *     addToMap(HTTP_VERB_GET,    "/staff/{employeeid}/training", "getEmployeeTraining");
 *     addToMap(HTTP_VERB_PUT,    "/staff/{employeeid}",          "updateEmployee");
 *     addToMap(HTTP_VERB_DELETE, "/staff/{employeeid}",          "deleteEmployee");
 *   }
 *
 *   //GET /group/app.pma/StaffAPI/staff/42/training
 *   public JSONActionRunnerReply getEmployeeTraining(long lEmployeeID)
 *   {
 *     JSONArray arr = ...;
 *     return JSONActionRunnerReply.ok(arr);
 *   }
 *
 *   public JSONActionRunnerReply updateEmployee(long lEmployeeID, JSONObject jsonBody) { ... }
 *
 *   public JSONActionRunnerReply deleteEmployee(long lEmployeeID)
 *   {
 *     if(!exists(lEmployeeID)) return JSONActionRunnerReply.notFound("No employee " + lEmployeeID);
 *     ...
 *     return JSONActionRunnerReply.noContent();
 *   }
 * }
 * </pre>
 * <b>Binding method parameters:</b> Java does not reliably keep parameter names at runtime, so
 * parameters are bound by position and type, not by name:
 * <ul>
 * <li>Parameters of type JSONObject, JSONArray or byte[] receive the request body (at most one per method).</li>
 * <li>All other parameters receive the URI {placeholders} in the order they appear in the template.
 * Supported types: String, int, long, double, boolean (and their boxed forms). A value that can't be
 * converted results in a 400 reply.</li>
 * </ul>
 * Values are also available by name via {@link #getPathParameter(String)}, query string values
 * via {@link #getQueryParameter(String)} and the raw body via {@link #getRequestBodyString()} etc.
 * <p>
 * <b>Return values:</b> a JSONActionRunnerReply is sent as-is. null (or a void method) sends 204 No Content.
 * Anything else (JSONObject, JSONArray, String, Map, Collection, bean) is sent as a 200 JSON reply.
 * A byte[] is sent as 200 application/octet-stream; use JSONActionRunnerReply.file(bytes, "application/pdf", "report.pdf")
 * to set the type and download file name.
 * <p>
 * <b>Binary uploads:</b> a byte[] parameter (or getRequestBodyBytes()/getRequestBodyInputStream()) receives any
 * request body that is not form encoded, eg a zip or image sent with "Content-Type: application/zip".
 * Bodies sent as multipart/form-data or application/x-www-form-urlencoded are parsed into document items by
 * the server instead, so they do not arrive here.
 * Throw a {@link JSONActionRunnerException} to abort with a particular status and message.
 * <p>
 * <b>Notes:</b>
 * <ul>
 * <li>Literal segments are matched case-insensitively and trailing slashes are ignored.
 * A literal segment beats a placeholder, so "/staff/new" is preferred over "/staff/{id}".</li>
 * <li>A path that matches but with the wrong verb gets 405 with an Allow header; otherwise 404.</li>
 * <li>HEAD is routed as GET. OPTIONS (CORS preflight) is answered by the server and never reaches the action.</li>
 * <li>The server URL-decodes the path before routing, so an encoded "/" (%2F) inside a value is treated
 * as a path separator.</li>
 * </ul>
 */
public class JSONActionRunner extends ActionRunner
{
	public static final String HTTP_VERB_GET = "GET";
	public static final String HTTP_VERB_POST = "POST";
	public static final String HTTP_VERB_PUT = "PUT";
	public static final String HTTP_VERB_DELETE = "DELETE";
	public static final String HTTP_VERB_PATCH = "PATCH";
	public static final String HTTP_VERB_HEAD = "HEAD";

	public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
	public static final String CONTENT_TYPE_BINARY = "application/octet-stream";

	public static final String SHOW_USAGE_PARAMETER = "showusage";

	private static final int STACKTRACE_DEPTH = 10;

	/**
	 * Set to true (eg in buildMap() or as a field initialiser) to allow "GET /group/app.pma/MyApi?showusage"
	 * to return a Swagger style html page describing every endpoint, with a "Try it out" form for each.
	 * Off by default because it reveals the API's structure. The page is served before preProcessRequest(),
	 * so to restrict it set this conditionally, eg ShowUsage = pSession.hasUserRole("APIAdmin");
	 */
	protected boolean ShowUsage = false;

	private final ArrayList<RouteEntry> m_arrRoutes = new ArrayList<RouteEntry>();
	private LinkedHashMap<String, String> m_mapPathParams = new LinkedHashMap<String, String>();
	private String m_sHttpVerb = HTTP_VERB_GET;
	private String m_sApiPath = "/";
	private byte[] m_bufRequestBody = null;
	private boolean m_bRequestBodyRead = false;


	/**
	 * The object returned by the developer's methods, describing the http reply to send.
	 * Use the static factory methods for the common cases, eg JSONActionRunnerReply.ok(json)
	 */
	public static class JSONActionRunnerReply
	{
		private int m_iStatusCode = 200;
		private String m_sStatusText = null;
		private String m_sContentType = CONTENT_TYPE_JSON;
		private Object m_objBody = null;
		private final LinkedHashMap<String, String> m_mapHeaders = new LinkedHashMap<String, String>();

		public JSONActionRunnerReply() {}

		/**
		 * @param iStatusCode eg 200
		 * @param objBody a JSONObject, JSONArray, String, byte[], Map, Collection or bean. May be null.
		 */
		public JSONActionRunnerReply(int iStatusCode, Object objBody)
		{
			m_iStatusCode = iStatusCode;
			m_objBody = objBody;
		}

		public static JSONActionRunnerReply ok(Object objBody) { return new JSONActionRunnerReply(200, objBody); }

		/**
		 * 201 Created. sLocation is the URI of the new resource and may be null
		 */
		public static JSONActionRunnerReply created(String sLocation, Object objBody)
		{
			JSONActionRunnerReply reply = new JSONActionRunnerReply(201, objBody);
			if(sLocation!=null && sLocation.length()>0) reply.addHeader("Location", sLocation);
			return reply;
		}

		public static JSONActionRunnerReply noContent() { return new JSONActionRunnerReply(204, null); }

		/**
		 * 200 OK with a binary body, eg a zip, pdf or image.
		 * @param bufData the bytes to send
		 * @param sContentType eg "application/zip". null sends "application/octet-stream"
		 * @param sFileName if not null, adds a Content-Disposition header so browsers download it with this
		 * name (attachment). Pass null to let the browser display it inline, eg an image.
		 */
		public static JSONActionRunnerReply file(byte[] bufData, String sContentType, String sFileName)
		{
			JSONActionRunnerReply reply = new JSONActionRunnerReply(200, bufData);
			reply.setContentType(sContentType==null || sContentType.trim().length()==0 ? CONTENT_TYPE_BINARY : sContentType.trim());
			if(sFileName!=null && sFileName.trim().length()>0) reply.addHeader("Content-Disposition", getContentDisposition(sFileName));
			return reply;
		}

		/**
		 * Build an "attachment" Content-Disposition value, safe for any file name. The plain filename is ASCII only
		 * (for old clients), filename* carries the exact UTF-8 name (RFC 6266/5987).
		 */
		static String getContentDisposition(String sFileName)
		{
			String sName = sFileName.replaceAll("[\\r\\n\\x00-\\x1f\\x7f]", "").trim(); //no header injection
			StringBuilder sbAscii = new StringBuilder();
			StringBuilder sbEncoded = new StringBuilder();
			for(char c : sName.toCharArray())
			{
				sbAscii.append((c<0x20 || c>0x7e || c=='"' || c=='\\') ? '_' : c);
			}
			for(byte b : Util.utf8FromString(sName))
			{
				int i = b & 0xFF;
				if((i>='a' && i<='z') || (i>='A' && i<='Z') || (i>='0' && i<='9') || "!#$&+-.^_`|~".indexOf(i)>=0)
					sbEncoded.append((char)i);
				else
					sbEncoded.append('%').append(Character.toUpperCase(Character.forDigit(i>>4, 16))).append(Character.toUpperCase(Character.forDigit(i&0xF, 16)));
			}
			return "attachment; filename=\"" + sbAscii + "\"; filename*=UTF-8''" + sbEncoded;
		}
		public static JSONActionRunnerReply badRequest(String sMessage) { return error(400, sMessage); }
		public static JSONActionRunnerReply unauthorized(String sMessage) { return error(401, sMessage); }
		public static JSONActionRunnerReply forbidden(String sMessage) { return error(403, sMessage); }
		public static JSONActionRunnerReply notFound(String sMessage) { return error(404, sMessage); }
		public static JSONActionRunnerReply conflict(String sMessage) { return error(409, sMessage); }

		/**
		 * A standard error reply with body: {"status":404,"error":"Not Found","message":"..."}
		 */
		public static JSONActionRunnerReply error(int iStatusCode, String sMessage)
		{
			JSONObject json = new JSONObject();
			json.put("status", iStatusCode);
			json.put("error", getReasonPhrase(iStatusCode));
			if(sMessage!=null) json.put("message", sMessage);
			return new JSONActionRunnerReply(iStatusCode, json);
		}

		public int getStatusCode() { return m_iStatusCode; }
		public JSONActionRunnerReply setStatusCode(int iStatusCode) { m_iStatusCode = iStatusCode; return this; }

		/**
		 * @return the reason phrase sent on the status line. Defaults to the standard text, eg "Not Found"
		 */
		public String getStatusText()
		{
			if(m_sStatusText==null || m_sStatusText.length()==0) return getReasonPhrase(m_iStatusCode);
			return m_sStatusText;
		}
		public JSONActionRunnerReply setStatusText(String sStatusText) { m_sStatusText = sStatusText; return this; }

		public String getContentType() { return m_sContentType; }
		public JSONActionRunnerReply setContentType(String sContentType) { m_sContentType = sContentType; return this; }

		public Object getBody() { return m_objBody; }
		public JSONActionRunnerReply setBody(Object objBody) { m_objBody = objBody; return this; }

		/**
		 * Add an extra http header to the reply, eg addHeader("X-Total-Count", "42")
		 */
		public JSONActionRunnerReply addHeader(String sName, String sValue)
		{
			if(sName!=null && sValue!=null) m_mapHeaders.put(sName, sValue);
			return this;
		}
		public Map<String, String> getHeaders() { return m_mapHeaders; }

		/**
		 * @return the body serialised to bytes, or null if there is no body. A String body is sent
		 * as-is (so it can hold pre-built JSON), it is not quoted as a JSON string.
		 */
		public byte[] getReplyBytes()
		{
			if(m_objBody==null) return null;
			if(m_objBody instanceof byte[]) return (byte[])m_objBody;
			if(m_objBody instanceof String) return Util.utf8FromString((String)m_objBody);
			//JSONObject and JSONArray are returned as-is, Maps/Collections/beans are converted
			Object objJSON = JSONObject.wrap(m_objBody);
			if(objJSON==null) return null;
			return Util.utf8FromString(String.valueOf(objJSON));
		}

		public static String getReasonPhrase(int iStatusCode)
		{
			switch(iStatusCode)
			{
			case 200: return "OK";
			case 201: return "Created";
			case 202: return "Accepted";
			case 204: return "No Content";
			case 301: return "Moved Permanently";
			case 302: return "Found";
			case 304: return "Not Modified";
			case 400: return "Bad Request";
			case 401: return "Unauthorized";
			case 403: return "Forbidden";
			case 404: return "Not Found";
			case 405: return "Method Not Allowed";
			case 409: return "Conflict";
			case 410: return "Gone";
			case 415: return "Unsupported Media Type";
			case 422: return "Unprocessable Content";
			case 429: return "Too Many Requests";
			case 500: return "Internal Server Error";
			case 501: return "Not Implemented";
			case 503: return "Service Unavailable";
			}
			if(iStatusCode>=200 && iStatusCode<300) return "OK";
			if(iStatusCode>=400 && iStatusCode<500) return "Bad Request";
			return "Error";
		}
	}//JSONActionRunnerReply


	/**
	 * Throw this from a developer method (or helper it calls) to abort the request and send a
	 * standard JSON error reply, eg throw new JSONActionRunnerException(404, "No such employee");
	 */
	public static class JSONActionRunnerException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
		private final int m_iStatusCode;

		public JSONActionRunnerException(int iStatusCode, String sMessage)
		{
			super(sMessage);
			m_iStatusCode = iStatusCode;
		}

		public JSONActionRunnerException(int iStatusCode, String sMessage, Throwable cause)
		{
			super(sMessage, cause);
			m_iStatusCode = iStatusCode;
		}

		public int getStatusCode() { return m_iStatusCode; }
	}


	/**
	 * One entry in the command map. Kept free of any server objects so the matching can be tested standalone.
	 */
	static final class RouteEntry
	{
		final String Verb;
		final String Template;
		final String MethodName;
		final String[] Segments;
		final int LiteralCount;
		final int PlaceholderCount;
		final ArrayList<String> PlaceholderNames = new ArrayList<String>(); //in template order, original case
		String Description;
		Method TargetMethod;

		RouteEntry(String sVerb, String sTemplate, String sMethodName)
		{
			Verb = sVerb.trim().toUpperCase();
			Template = sTemplate;
			MethodName = sMethodName;
			Segments = splitPath(sTemplate);
			int iLiterals = 0;
			int iPlaceholders = 0;
			Set<String> setNames = new LinkedHashSet<String>();
			for(int i=0; i<Segments.length; i++)
			{
				if(isPlaceholder(Segments[i]))
				{
					iPlaceholders++;
					PlaceholderNames.add(Segments[i].substring(1, Segments[i].length()-1).trim());
					String sName = Segments[i].substring(1, Segments[i].length()-1).trim().toLowerCase();
					if(sName.length()==0) throw new IllegalArgumentException("Empty {} placeholder in '" + sTemplate + "'");
					if(!setNames.add(sName)) throw new IllegalArgumentException("Duplicate placeholder {" + sName + "} in '" + sTemplate + "'");
				}
				else
					iLiterals++;
			}
			LiteralCount = iLiterals;
			PlaceholderCount = iPlaceholders;
		}

		/**
		 * @return the placeholder values keyed by lowercase name, or null if the path does not match
		 */
		LinkedHashMap<String, String> match(String[] sPathSegments)
		{
			if(sPathSegments.length!=Segments.length) return null;
			LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
			for(int i=0; i<Segments.length; i++)
			{
				String sSeg = Segments[i];
				if(isPlaceholder(sSeg))
					map.put(sSeg.substring(1, sSeg.length()-1).trim().toLowerCase(), sPathSegments[i]);
				else if(!sSeg.equalsIgnoreCase(sPathSegments[i]))
					return null;
			}
			return map;
		}

		static boolean isPlaceholder(String sSegment)
		{
			return sSegment.length()>=2 && sSegment.charAt(0)=='{' && sSegment.charAt(sSegment.length()-1)=='}';
		}

		/**
		 * "/staff//42/" -> ["staff", "42"]
		 */
		static String[] splitPath(String sPath)
		{
			ArrayList<String> arr = new ArrayList<String>();
			if(sPath!=null)
			{
				for(String sPart : sPath.split("/"))
				{
					if(sPart.length()>0) arr.add(sPart);
				}
			}
			return arr.toArray(new String[arr.size()]);
		}

		public String toString()
		{
			return Verb + " " + Template + " -> " + MethodName + "()";
		}
	}//RouteEntry


	/**
	 * The outcome of looking up a request in the command map
	 */
	static final class RouteMatch
	{
		RouteEntry Entry; //null if no match
		LinkedHashMap<String, String> PathParams;
		Set<String> AllowedVerbs = new LinkedHashSet<String>(); //verbs that matched the path, used for 405
	}

	/**
	 * Find the most specific entry matching the verb and path. HEAD is treated as GET.
	 */
	static RouteMatch findRoute(ArrayList<RouteEntry> arrRoutes, String sHttpVerb, String sApiPath)
	{
		RouteMatch rm = new RouteMatch();
		String sVerb = sHttpVerb==null ? HTTP_VERB_GET : sHttpVerb.trim().toUpperCase();
		if(sVerb.equals(HTTP_VERB_HEAD)) sVerb = HTTP_VERB_GET;
		String[] sSegments = RouteEntry.splitPath(sApiPath);
		for(RouteEntry re : arrRoutes)
		{
			LinkedHashMap<String, String> map = re.match(sSegments);
			if(map==null) continue;
			rm.AllowedVerbs.add(re.Verb);
			if(!re.Verb.equals(sVerb)) continue;
			//most literal segments wins, first added wins a tie
			if(rm.Entry==null || re.LiteralCount>rm.Entry.LiteralCount)
			{
				rm.Entry = re;
				rm.PathParams = map;
			}
		}
		if(rm.AllowedVerbs.contains(HTTP_VERB_GET)) rm.AllowedVerbs.add(HTTP_VERB_HEAD);
		return rm;
	}


	/**
	 * This will typically NOT be overridden by the developer, is called by HTTPRequestManager
	 */
	public String execute()
	{
		JSONActionRunnerReply reply;
		try
		{
			buildMap();
			m_sHttpVerb = getRequestVerb();
			m_sApiPath = getRequestApiPath();
			if(ShowUsage && isShowUsageRequest())
				reply = getUsageReply();
			else
			{
				reply = preProcessRequest(m_sHttpVerb, m_sApiPath);
				if(reply==null) reply = executeRestCommand();
			}
		}
		catch(Throwable t)
		{
			if(t instanceof VirtualMachineError) throw (VirtualMachineError)t; //eg OutOfMemoryError, let the server deal with it
			reply = handleException(t);
		}
		if(reply==null) reply = JSONActionRunnerReply.noContent();
		sendReply(reply);
		return "";
	}

	/**
	 * This is overridden by the developer, used to build the URI map with {@link #addToMap(String, String, String)}. eg:
	 * <pre>
	 * addToMap(HTTP_VERB_GET, "/staff/{employeeid}/training", "getEmployeeTraining");
	 * addToMap(HTTP_VERB_DELETE, "/staff/{employeeid}", "deleteEmployee");
	 * </pre>
	 */
	protected void buildMap()
	{
		throw new UnsupportedOperationException(getClass().getName() + " must implement buildMap()");
	}

	/**
	 * Override to do processing before the mapped method is called, eg checking an API key or bearer
	 * token. Return null to continue, or a reply to send it instead of calling the mapped method.
	 * @param sHttpVerb eg "GET"
	 * @param sApiPath the path after the action name, eg "/staff/42/training"
	 */
	protected JSONActionRunnerReply preProcessRequest(String sHttpVerb, String sApiPath)
	{
		return null;
	}

	/**
	 * Called when anything is thrown while processing the request. The default logs unexpected errors
	 * and returns a 500 reply that does not reveal any internal detail to the client.
	 * Override to customise error replies.
	 */
	protected JSONActionRunnerReply handleException(Throwable t)
	{
		if(t instanceof JSONActionRunnerException)
		{
			JSONActionRunnerException jare = (JSONActionRunnerException)t;
			return JSONActionRunnerReply.error(jare.getStatusCode(), jare.getMessage());
		}

		String sMsg = getClass().getName() + " " + m_sHttpVerb + " " + m_sApiPath + " " + t.toString();
		if(pSystem!=null)
		{
			pSystem.doError(sMsg, this);
			Util.logStackTrace(t, pSystem, STACKTRACE_DEPTH);
		}
		else
		{
			System.err.println(sMsg);
			t.printStackTrace();
		}
		return JSONActionRunnerReply.error(500, "An unexpected error occurred");
	}

	/**
	 * Add an entry to the command map. Call this from buildMap(). The target method is located now, so
	 * a typo or unsupported signature is reported immediately (as an IllegalArgumentException) rather
	 * than only when that URI is requested.
	 * @param sHttpVerb one of the HTTP_VERB_xxx constants
	 * @param sUriTemplate eg "/staff/{employeeid}/training"
	 * @param sTargetMethod the name of the method in the subclass to call, eg "getEmployeeTraining"
	 */
	protected void addToMap(String sHttpVerb, String sUriTemplate, String sTargetMethod)
	{
		addToMap(sHttpVerb, sUriTemplate, sTargetMethod, null);
	}

	/**
	 * As {@link #addToMap(String, String, String)}, with a short description of the endpoint that is
	 * shown on the ?showusage page. eg "Lists the training completed by an employee"
	 */
	protected void addToMap(String sHttpVerb, String sUriTemplate, String sTargetMethod, String sDescription)
	{
		if(sHttpVerb==null || sHttpVerb.trim().length()==0) throw new IllegalArgumentException("addToMap() http verb is required");
		if(sUriTemplate==null) throw new IllegalArgumentException("addToMap() uri template is required");
		if(sTargetMethod==null || sTargetMethod.length()==0) throw new IllegalArgumentException("addToMap() method name is required");

		RouteEntry re = new RouteEntry(sHttpVerb, sUriTemplate, sTargetMethod);
		re.Description = sDescription;
		re.TargetMethod = findTargetMethod(re);
		m_arrRoutes.add(re);
	}

	/**
	 * @return true if this is a GET for "?showusage", eg "/group/app.pma/StaffAPI?showusage".
	 * Puakma treats the first "?xxx" as the action, so check there as well as the parameters.
	 */
	private boolean isShowUsageRequest()
	{
		if(!m_sHttpVerb.equals(HTTP_VERB_GET) && !m_sHttpVerb.equals(HTTP_VERB_HEAD)) return false;
		if(ActionDocument==null || ActionDocument.rPath==null) return false;
		return hasShowUsageFlag(ActionDocument.rPath.Action, ActionDocument.rPath.Parameters);
	}

	/**
	 * @param sAction eg "showusage" from "?showusage"
	 * @param sParameters eg "&amp;dept=IT&amp;showusage"
	 */
	static boolean hasShowUsageFlag(String sAction, String sParameters)
	{
		if(sAction!=null && sAction.trim().equalsIgnoreCase(SHOW_USAGE_PARAMETER)) return true;
		if(sParameters==null) return false;
		for(String sPart : sParameters.split("&"))
		{
			int iPos = sPart.indexOf('=');
			String sName = iPos>=0 ? sPart.substring(0, iPos) : sPart;
			if(sName.trim().equalsIgnoreCase(SHOW_USAGE_PARAMETER)) return true;
		}
		return false;
	}

	/**
	 * Build the html page describing this API
	 */
	private JSONActionRunnerReply getUsageReply()
	{
		String sHTML = JSONActionUsage.getUsageHTML(getClass().getSimpleName(), getApiBaseURL(), m_arrRoutes);
		return new JSONActionRunnerReply(200, sHTML).setContentType("text/html; charset=utf-8");
	}

	/**
	 * @return the url of this API, eg "/group/app.pma/StaffAPI"
	 */
	public String getApiBaseURL()
	{
		return getDBURL() + '/' + getActionDesignName();
	}

	/**
	 * @return the name of the action design element this class was run as, eg "StaffAPI"
	 */
	private String getActionDesignName()
	{
		String sDesignName = (ActionDocument==null || ActionDocument.designObject==null) ? null : ActionDocument.designObject.getDesignName();
		if(sDesignName==null || sDesignName.length()==0) sDesignName = getClass().getSimpleName();
		return sDesignName;
	}

	/**
	 * Locate the method for this route on the subclass, checking it has a signature we can call
	 */
	private Method findTargetMethod(RouteEntry re)
	{
		ArrayList<Method> arrCandidates = new ArrayList<Method>();
		ArrayList<String> arrRejected = new ArrayList<String>();
		Set<String> setSeen = new LinkedHashSet<String>();
		for(Class<?> c = getClass(); c!=null && c!=JSONActionRunner.class && c!=Object.class; c = c.getSuperclass())
		{
			for(Method m : c.getDeclaredMethods())
			{
				if(!m.getName().equals(re.MethodName) || m.isSynthetic() || m.isBridge()) continue;
				if(!setSeen.add(Arrays.toString(m.getParameterTypes()))) continue; //already found the override in a subclass
				String sProblem = getSignatureProblem(m, re);
				if(sProblem==null)
					arrCandidates.add(m);
				else
					arrRejected.add(m.toGenericString() + ": " + sProblem);
			}
		}

		String sRoute = "Route " + re.Verb + " " + re.Template + " in " + getClass().getName();
		if(arrCandidates.size()==1)
		{
			Method m = arrCandidates.get(0);
			m.setAccessible(true); //allow protected/private methods in the subclass
			return m;
		}
		if(arrCandidates.size()>1) throw new IllegalArgumentException(sRoute + ": method '" + re.MethodName + "' is ambiguous, there are " + arrCandidates.size() + " overloads that could match");
		if(arrRejected.size()>0) throw new IllegalArgumentException(sRoute + ": method '" + re.MethodName + "' has an unsupported signature. " + arrRejected);
		throw new IllegalArgumentException(sRoute + ": method '" + re.MethodName + "' was not found");
	}

	/**
	 * @return null if the method can be called for this route, otherwise the reason it can't
	 */
	private static String getSignatureProblem(Method m, RouteEntry re)
	{
		if(Modifier.isStatic(m.getModifiers())) return "must not be static";
		int iBodyParams = 0;
		int iPathParams = 0;
		for(Class<?> c : m.getParameterTypes())
		{
			if(isBodyType(c))
				iBodyParams++;
			else if(isPathParamType(c))
				iPathParams++;
			else
				return "parameter type " + c.getSimpleName() + " is not supported";
		}
		if(iBodyParams>1) return "only one JSONObject/JSONArray/byte[] body parameter is allowed";
		if(iPathParams!=re.PlaceholderCount) return "has " + iPathParams + " path parameter(s) but the uri has " + re.PlaceholderCount + " {placeholder}(s)";
		return null;
	}

	private static boolean isBodyType(Class<?> c)
	{
		return c==JSONObject.class || c==JSONArray.class || c==byte[].class;
	}

	private static boolean isPathParamType(Class<?> c)
	{
		return c==String.class ||
				c==int.class || c==Integer.class ||
				c==long.class || c==Long.class ||
				c==double.class || c==Double.class ||
				c==boolean.class || c==Boolean.class;
	}

	/**
	 * Convert a path value to the type of the method parameter. Throws a 400 if it can't be converted.
	 */
	static Object convertPathParameter(String sName, String sValue, Class<?> c)
	{
		if(c==String.class) return sValue;
		try
		{
			if(c==int.class || c==Integer.class) return Integer.valueOf(sValue.trim());
			if(c==long.class || c==Long.class) return Long.valueOf(sValue.trim());
			if(c==double.class || c==Double.class) return Double.valueOf(sValue.trim());
			if(c==boolean.class || c==Boolean.class)
			{
				String s = sValue.trim();
				if(s.equalsIgnoreCase("true") || s.equals("1")) return Boolean.TRUE;
				if(s.equalsIgnoreCase("false") || s.equals("0")) return Boolean.FALSE;
			}
		}
		catch(NumberFormatException nfe) {}
		throw new JSONActionRunnerException(400, "Invalid value for {" + sName + "}: '" + sValue + "'");
	}

	/**
	 * This method reads the map and compares it to the incoming URI, then via reflection determines which method to call
	 */
	private JSONActionRunnerReply executeRestCommand() throws Throwable
	{
		RouteMatch rm = findRoute(m_arrRoutes, m_sHttpVerb, m_sApiPath);
		if(rm.Entry==null)
		{
			if(rm.AllowedVerbs.size()>0)
			{
				JSONActionRunnerReply reply = JSONActionRunnerReply.error(405, m_sHttpVerb + " is not supported for " + m_sApiPath);
				reply.addHeader("Allow", String.join(", ", rm.AllowedVerbs));
				return reply;
			}
			return JSONActionRunnerReply.notFound("No API endpoint matches " + m_sHttpVerb + " " + m_sApiPath);
		}

		m_mapPathParams = rm.PathParams;
		Method m = rm.Entry.TargetMethod;
		Object[] args = buildArguments(m, rm.PathParams);
		Object objResult;
		try
		{
			objResult = m.invoke(this, args);
		}
		catch(InvocationTargetException ite)
		{
			throw ite.getCause()!=null ? ite.getCause() : ite;
		}

		if(objResult==null) return JSONActionRunnerReply.noContent();
		if(objResult instanceof JSONActionRunnerReply) return (JSONActionRunnerReply)objResult;
		if(objResult instanceof byte[]) return JSONActionRunnerReply.ok(objResult).setContentType(CONTENT_TYPE_BINARY);
		return JSONActionRunnerReply.ok(objResult);
	}

	/**
	 * Build the argument list: path values in placeholder order, body for the body typed parameter
	 */
	private Object[] buildArguments(Method m, LinkedHashMap<String, String> mapPathParams)
	{
		Class<?>[] types = m.getParameterTypes();
		Object[] args = new Object[types.length];
		String[] sNames = mapPathParams.keySet().toArray(new String[0]);
		String[] sValues = mapPathParams.values().toArray(new String[0]);
		int iPathIndex = 0;
		for(int i=0; i<types.length; i++)
		{
			Class<?> c = types[i];
			if(c==JSONObject.class) args[i] = getRequestJSONObject();
			else if(c==JSONArray.class) args[i] = getRequestJSONArray();
			else if(c==byte[].class) args[i] = getRequestBodyBytes();
			else
			{
				args[i] = convertPathParameter(sNames[iPathIndex], sValues[iPathIndex], c);
				iPathIndex++;
			}
		}
		return args;
	}

	private void sendReply(JSONActionRunnerReply reply)
	{
		int iStatus = reply.getStatusCode();
		setHttpReplyCode(iStatus);
		setHttpReplyText(reply.getStatusText());
		setContentType(reply.getContentType());
		if(ActionDocument!=null)
		{
			for(Map.Entry<String, String> entry : reply.getHeaders().entrySet())
			{
				ActionDocument.setExtraHeaderValue(entry.getKey(), entry.getValue(), true);
			}
		}
		//204 and 304 must not carry a body
		if(iStatus==204 || iStatus==304)
			setBuffer(null);
		else
			setBuffer(reply.getReplyBytes());
	}

	/**
	 * @return the http verb used for this request, eg "GET", "POST"
	 */
	public String getRequestVerb()
	{
		String sVerb = ActionDocument==null ? null : ActionDocument.getItemValue("@Method");
		if(sVerb==null || sVerb.length()==0) return HTTP_VERB_GET;
		return sVerb.trim().toUpperCase();
	}

	/**
	 * Determine the part of the path after this action's name. eg for "/group/app.pma/StaffAPI/staff/42"
	 * returns "/staff/42"
	 */
	public String getRequestApiPath()
	{
		if(ActionDocument==null || ActionDocument.rPath==null) return "/";
		return computeApiPath(ActionDocument.rPath.DesignElementName, getActionDesignName());
	}

	/**
	 * Strip the action's design name from the requested design element name, eg ("StaffAPI/staff/42", "StaffAPI") -> "/staff/42"
	 */
	static String computeApiPath(String sDesignElementName, String sDesignName)
	{
		String sPath = sDesignElementName==null ? "" : sDesignElementName;
		if(sDesignName==null) sDesignName = "";
		int iLen = sDesignName.length();

		if(sPath.regionMatches(true, 0, sDesignName, 0, iLen) && (sPath.length()==iLen || sPath.charAt(iLen)=='/'))
			sPath = sPath.substring(iLen); //"StaffAPI/staff/42"
		else if(sPath.length()>iLen && sPath.regionMatches(true, sPath.length()-iLen, sDesignName, 0, iLen) && sPath.charAt(sPath.length()-iLen-1)=='/')
			sPath = sPath.substring(0, sPath.length()-iLen-1); //the server also finds "something/StaffAPI"

		if(!sPath.startsWith("/")) sPath = '/' + sPath;
		return sPath;
	}

	/**
	 * Get the value of a {placeholder} from the matched uri template, eg getPathParameter("employeeid")
	 * @return null if there is no such placeholder
	 */
	public String getPathParameter(String sName)
	{
		if(sName==null) return null;
		return m_mapPathParams.get(sName.trim().toLowerCase());
	}

	/**
	 * Get a value from the query string, eg "?OpenAction&page=2" getQueryParameter("page") returns "2"
	 */
	public String getQueryParameter(String sName)
	{
		if(ActionDocument==null || sName==null) return null;
		return ActionDocument.getParameter(sName);
	}

	/**
	 * Get an incoming http header, eg getRequestHeader("Authorization")
	 */
	public String getRequestHeader(String sName)
	{
		if(ActionDocument==null || sName==null) return null;
		return ActionDocument.getItemValue('@' + sName);
	}

	/**
	 * @return the raw request body (un-gzipped if required), or null if there is none.
	 */
	public byte[] getRequestBodyBytes()
	{
		if(m_bRequestBodyRead) return m_bufRequestBody;
		m_bRequestBodyRead = true;
		if(ActionDocument==null) return null;

		byte[] bufData;
		try
		{
			bufData = readItemBytes(ActionDocument.getItem(Document.DATA_ITEM_NAME));
		}
		catch(IOException ioe)
		{
			throw new JSONActionRunnerException(400, "The request body could not be read", ioe);
		}

		if(bufData!=null && isRequestGzipped())
		{
			bufData = Util.ungzipBuffer(bufData);
			if(bufData==null) throw new JSONActionRunnerException(400, "The request body is not valid gzip data");
		}

		m_bufRequestBody = bufData;
		return m_bufRequestBody;
	}

	/**
	 * Get the request body as a stream, without loading it all into memory. Use this for large uploads,
	 * eg to copy straight to a file or database blob. The caller must close the stream.
	 * @return null if there is no body
	 */
	public InputStream getRequestBodyInputStream() throws IOException
	{
		if(m_bRequestBodyRead) return m_bufRequestBody==null ? null : new ByteArrayInputStream(m_bufRequestBody);
		if(ActionDocument==null) return null;

		InputStream is = null;
		DocumentItem di = ActionDocument.getItem(Document.DATA_ITEM_NAME);
		if(di instanceof DocumentFileItem) //large payloads are spooled to a temp file
		{
			FileData fd = ((DocumentFileItem)di).getFileData(0);
			if(fd!=null && fd.file!=null) is = new BufferedInputStream(new FileInputStream(fd.file));
		}
		else if(di!=null)
		{
			byte[] buf = di.getValue();
			if(buf!=null) is = new ByteArrayInputStream(buf);
		}
		if(is!=null && isRequestGzipped()) is = new GZIPInputStream(is);
		return is;
	}

	/**
	 * @return the mime type of the request body without parameters, eg "application/zip", or null
	 */
	public String getRequestContentType()
	{
		String sType = getRequestHeader("Content-Type");
		if(sType==null) return null;
		int iPos = sType.indexOf(';');
		if(iPos>=0) sType = sType.substring(0, iPos);
		sType = sType.trim();
		return sType.length()==0 ? null : sType;
	}

	private boolean isRequestGzipped()
	{
		String sEncoding = getRequestHeader("Content-Encoding");
		return sEncoding!=null && sEncoding.trim().equalsIgnoreCase("gzip");
	}

	/**
	 * Read the raw bytes of the "Data" item the server puts a non-form request body in. Bodies over
	 * 0.5MB are spooled to a temp file, which is read fully (FileData.getBytes() does a single read()).
	 * @return null if there is no item
	 */
	static byte[] readItemBytes(DocumentItem di) throws IOException
	{
		if(di==null) return null; //eg GET, or a form-urlencoded/multipart POST which the server parses into items
		if(di instanceof DocumentFileItem)
		{
			DocumentFileItem dfi = (DocumentFileItem)di;
			FileData fd = dfi.getFileCount()>0 ? dfi.getFileData(0) : null;
			if(fd==null || fd.file==null) return null;
			return Files.readAllBytes(fd.file.toPath());
		}
		return di.getValue();
	}

	/**
	 * @return the request body as a utf-8 String, or null if there is none
	 */
	public String getRequestBodyString()
	{
		byte[] buf = getRequestBodyBytes();
		if(buf==null) return null;
		return Util.stringFromUTF8(buf);
	}

	/**
	 * Parse the request body as a JSON object. Replies 400 if the body is missing or not a valid JSON object.
	 */
	public JSONObject getRequestJSONObject()
	{
		String sBody = getRequiredBody();
		try
		{
			return new JSONObject(sBody);
		}
		catch(JSONException je)
		{
			throw new JSONActionRunnerException(400, "Request body is not a valid JSON object", je);
		}
	}

	/**
	 * Parse the request body as a JSON array. Replies 400 if the body is missing or not a valid JSON array.
	 */
	public JSONArray getRequestJSONArray()
	{
		String sBody = getRequiredBody();
		try
		{
			return new JSONArray(sBody);
		}
		catch(JSONException je)
		{
			throw new JSONActionRunnerException(400, "Request body is not a valid JSON array", je);
		}
	}

	private String getRequiredBody()
	{
		String sBody = getRequestBodyString();
		if(sBody==null || sBody.trim().length()==0) throw new JSONActionRunnerException(400, "A JSON request body is required");
		return sBody;
	}

	//Developer adds their own methods in a subclass

}
