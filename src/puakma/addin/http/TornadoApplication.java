package puakma.addin.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import puakma.addin.http.action.ActionReturn;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.addin.http.document.DesignElement;
import puakma.addin.http.document.HTMLDocument;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.jdbc.dbConnectionPoolManager;
import puakma.pooler.Cache;
import puakma.system.ActionRunnerInterface;
import puakma.system.Document;
import puakma.system.Parameter;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.UserRoles;
import puakma.system.X500Name;
import puakma.system.pmaSession;
import puakma.system.pmaSystem;
import puakma.util.Util;

/**
 * Defines a cache of commonly used application parts, params, designelements, roles
 * @author bupson
 *
 */
public class TornadoApplication implements ErrorDetect
{
	public static final String ACCESS_APP_ROLE = "AllowAccess";
	public static final String ACCESS_WS_ROLE = "WebServiceAccess";
	public static final String ACCESS_RESOURCES_ROLE = "ResourceAccess";
	public static final String DENY_ACCESS_ROLE = "DenyAccess";

	public static final String DEFAULT_LOCALE = "DefaultLocale"; //eg 2 digit language, 'en-AU' = english, Australian

	private String m_sAppGroup = "";
	private String m_sAppName = "";
	private String[] m_sRoles = null; 
	private Parameter[] m_AppParams = null;
	private long m_lAppID=-1;
	private SystemContext m_pSystem;
	private Cache m_cacheDesign; //passed in from the parent server instance

	private dbConnectionPoolManager m_DBPoolMgr;
	public final static int MAX_CONNECTIONS = 80;
	public final static int CONN_TIMEOUT = 5000; //ms
	public final static int CONN_EXPIRY = 60; //s was 1800s. 
	/*
	 * I figure if we're only getting a connection every 60 seconds, 
	 * it's a low use server so the overhead of getting a fresh 
	 * db connection is ok 
	 */
	private int m_iMaxConnectionCount = MAX_CONNECTIONS;
	private int m_iPoolConnectionTimeoutMS = CONN_TIMEOUT;
	private int m_iPoolConnectionExpireSeconds = CONN_EXPIRY;
	private Hashtable m_htConnectionAttributeMap = new Hashtable();
	private Hashtable m_htConnections = new Hashtable();
	private int m_iDataConnGetCount=0;
	private int m_iDataConnReleaseCount=0;
	private Hashtable m_htStringTable = new Hashtable(500);
	private Locale m_applicationLocale = null;
	private SharedActionClassLoader m_sacl = null;

	protected TornadoApplication(SystemContext sysCtx, Cache cacheDesign, String sGroup, String sApp)
	{
		if(sGroup==null) sGroup = "";
		if(sApp==null) sApp = "";

		m_pSystem = sysCtx;
		m_sAppGroup = sGroup.trim().toLowerCase();
		m_sAppName = sApp.trim().toLowerCase();
		//call methods to prefill the internal cache
		m_lAppID = getApplicationID();
		if(appExists())
		{
			getAllApplicationParameters();
			getRoles();
			m_DBPoolMgr = new dbConnectionPoolManager(m_pSystem, getErrorSource());
		}
		m_cacheDesign = cacheDesign;

		if(m_cacheDesign==null)
		{
			//			set up the design cache if we got passed a dud parameter
			double dCacheSize = 10485760; //10MB default
			String sTemp = m_pSystem.getSystemProperty("HTTPServerCacheMB");
			if(sTemp!=null)
			{
				try
				{
					dCacheSize = Double.parseDouble(sTemp) *1024 *1024;
				}
				catch(Exception de){};
			}
			m_cacheDesign = new Cache(dCacheSize);
		}

	}

	/**
	 * Returns true if the application exists in the database
	 * @return
	 */
	public boolean appExists() 
	{
		return m_lAppID>=0;
	}

	/**
	 * 
	 * @return
	 */
	public RequestPath getRequestPath()
	{
		return new RequestPath(m_sAppGroup, m_sAppName, "", "");
	}

	/**
	 * 
	 * @return
	 */
	public SystemContext getSystemContext()
	{
		return m_pSystem;
	}

	/**
	 * Get the ID of the application
	 * @return -1 if not found
	 */
	public long getApplicationID()
	{
		//check if we have already looked it up, if so return our cached copy
		if(m_lAppID>=0) return m_lAppID;

		boolean bHasGroup = true;
		if(m_sAppGroup.length()==0) bHasGroup = false;

		String sQuery = "SELECT AppID,AppGroup,AppName FROM APPLICATION WHERE LOWER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL)";
		if(bHasGroup) sQuery = "SELECT AppID,AppGroup,AppName FROM APPLICATION WHERE LOWER(APPLICATION.AppName)=? AND (LOWER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*')";

		Connection cx=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = m_pSystem.getSystemConnection();        
			stmt = cx.prepareStatement(sQuery);
			stmt.setString(1, m_sAppName);
			if(bHasGroup) stmt.setString(2, m_sAppGroup);			
			rs = stmt.executeQuery();
			if(rs.next()) 
			{
				m_lAppID = rs.getLong(1);
				//save these values as we might be looking at a wildcard app
				m_sAppGroup = rs.getString(2);
				if(m_sAppGroup==null) m_sAppGroup = ""; //yay oracle....
				m_sAppName = rs.getString(3);
			}
		}
		catch (Exception sqle)
		{			
			m_pSystem.doError("TornadoApplication.getAppID", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		return m_lAppID;
	}

	/**
	 * Determines is an application has been disabled. This means no web access and no scheduled agents or widgets
	 * get loaded
	 */
	public boolean isApplicationDisabled()
	{
		long lAppID = getApplicationID();
		int iDisabled = -1;

		String sQuery = "SELECT ParamValue FROM APPPARAM WHERE AppID="+lAppID+" AND LOWER(ParamName)='"+Document.APPPARAM_DISABLEAPP+"'";

		Connection cx=null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = m_pSystem.getSystemConnection();        
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);			
			rs = stmt.executeQuery(sQuery);
			if(rs.next()) iDisabled = rs.getInt(1);			
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("TornadoApplication.isAppDisabled", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		if(iDisabled==1) return true;

		return false;
	}

	/**
	 * Determines is an application has been disabled. This means no web access and no scheduled agents or widgets
	 * get loaded
	 */
	public String[] getRoles()
	{
		if(m_sRoles!=null) return m_sRoles;

		long lAppID = getApplicationID();
		String sQuery = "SELECT DISTINCT RoleName FROM ROLE WHERE AppID="+lAppID;
		ArrayList arr = new ArrayList();

		Connection cx=null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = m_pSystem.getSystemConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);			
			rs = stmt.executeQuery(sQuery);
			while(rs.next())
			{
				String sRole = rs.getString(1);
				arr.add(sRole);
			}//while			
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("TornadoApplication.getRoles", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		m_sRoles = Util.objectArrayToStringArray(arr.toArray());

		return m_sRoles;
	}

	/**
	 * Get the value of a particular application parameter. If there are multiple matches,
	 * return a comma seperated list.
	 * @param sParamName
	 * @return
	 */
	public String getApplicationParameter(String sParamName)
	{
		StringBuilder sbReturn = new StringBuilder();
		Parameter p[] = getAllApplicationParameters();
		sParamName = sParamName.toLowerCase(); 
		for(int i=0; i<p.length; i++)
		{
			if(p[i].Name.equalsIgnoreCase(sParamName)) 
			{
				if(sbReturn.length()>0) sbReturn.append(',');
				String sValue = p[i].Value;
				if(sValue==null) sValue = "";
				sbReturn.append(sValue);			
			}
		}
		return sbReturn.toString();
	}

	/**
	 * Return all the parameters of this application
	 * @return
	 */
	public Parameter[] getAllApplicationParameters()
	{
		if(m_AppParams!=null) return m_AppParams;

		long lAppID = getApplicationID();
		ArrayList arr = new ArrayList();
		String sQuery = "SELECT ParamName,ParamValue FROM APPPARAM WHERE AppID="+lAppID + " ORDER BY ParamName";

		Connection cx=null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = m_pSystem.getSystemConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);			
			rs = stmt.executeQuery(sQuery);
			while(rs.next())
			{				
				Parameter p = new Parameter(rs.getString(1), rs.getString(2));
				arr.add(p);
			}			
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("TornadoApplication.AppParamError", new String[]{sqle.getMessage()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}

		m_AppParams = new Parameter[arr.size()];
		for(int i=0; i<arr.size(); i++)
		{
			m_AppParams[i] = (Parameter)arr.get(i);
		}

		return m_AppParams;
	}

	/**
	 * 
	 * @param iDesignType
	 * @return
	 */
	public String[] getAllDesignElementNames(int iDesignType)
	{      
		ArrayList arr = new ArrayList();    

		long lAppID = getApplicationID();

		String sTypeClause="";
		if(iDesignType!=-1) sTypeClause = " AND DesignType="+iDesignType;
		String sQuery = "SELECT Name FROM DESIGNBUCKET WHERE AppID="+lAppID+sTypeClause + " ORDER BY Name";

		Connection cx=null;
		Statement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = m_pSystem.getSystemConnection();
			stmt = cx.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			rs = stmt.executeQuery(sQuery);
			while(rs.next())
			{
				arr.add(rs.getString(1));
			}		
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("TornadoApplication.DesignNamesError", new String[]{sqle.getMessage()}, this);
		}      
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		if(arr.size()>0) return puakma.util.Util.objectArrayToStringArray(arr.toArray());
		return null;
	}

	/**
	 * 
	 * @param sDesignName
	 * @param iType
	 * @return
	 */
	private DesignElement getDesignElementFromCache(String sDesignName, int iType)
	{	
		DesignElement design=null;
		if(sDesignName==null) return design;

		String sTypes[]=null;
		if(iType==DesignElement.DESIGN_TYPE_HTTPACCESSIBLE)
		{
			sTypes = new String[4];
			sTypes[0] = String.valueOf(DesignElement.DESIGN_TYPE_ACTION);
			sTypes[1] = String.valueOf(DesignElement.DESIGN_TYPE_RESOURCE);
			sTypes[2] = String.valueOf(DesignElement.DESIGN_TYPE_PAGE);
			sTypes[3] = String.valueOf(DesignElement.DESIGN_TYPE_BUSINESSWIDGET);
		}
		else
		{
			sTypes = new String[1];
			sTypes[0] = String.valueOf(iType);
		}

		String sBaseKey = "";
		if(m_sAppGroup.length()!=0) sBaseKey += '/' + m_sAppGroup;
		if(m_sAppName.length()!=0) sBaseKey += '/' + m_sAppName + System.getProperty(pmaSystem.PUAKMA_FILEEXT_SYSTEMKEY);
		if(sDesignName.length()!=0) sBaseKey += '/' + sDesignName;
		sBaseKey = sBaseKey.toLowerCase();

		if(sTypes!=null)
		{
			for(int i=0; i<sTypes.length; i++)
			{
				String sKey = sBaseKey + '/' + sTypes[i];
				design = (DesignElement)m_cacheDesign.getItem(sKey);
				if(design!=null) break;				
			}
		}

		/*if(design!=null) 
			System.out.println("cache HIT: " + sDesignName);
		else
			System.out.println("cache miss: " + sDesignName);
		 */
		return design;
	}

	/**
	 * 
	 * @param sDesignName
	 * @param iType
	 * @return
	 */
	public DesignElement getDesignElement(String sDesignName, int iType)
	{    
		if(sDesignName==null) return null;
		int iSlashPos = -1;
		DesignElement design = getDesignElementFromCache(sDesignName, iType);
		if(design==null)
		{
			iSlashPos = sDesignName.indexOf('/');
			if(iSlashPos>0) //eg can't be "/something", must be "actionname/something" or "something/actionname"
			{
				String sActionName = sDesignName.substring(0, iSlashPos);
				String sExtra = sDesignName.substring(iSlashPos+1);
				design = getDesignElementFromCache(sActionName, iType);
				if(design==null) design = getDesignElementFromCache(sExtra, iType);
			}
		}
		if(design!=null) return design;

		long lAppID = getApplicationID();

		String sTypeClause;
		switch(iType)
		{
		case DesignElement.DESIGN_TYPE_HTTPACCESSIBLE:
			sTypeClause = " AND (DesignType=" + DesignElement.DESIGN_TYPE_ACTION + 
			" OR DesignType=" + DesignElement.DESIGN_TYPE_PAGE + 
			" OR DesignType=" + DesignElement.DESIGN_TYPE_BUSINESSWIDGET + 
			" OR DesignType=" + DesignElement.DESIGN_TYPE_RESOURCE + ")";
			break;
		case DesignElement.DESIGN_TYPE_UNKNOWN:
			sTypeClause = "";
			break;
		default:
			sTypeClause = " AND DESIGNBUCKET.DesignType=" + iType;

		}

		String sNamePart = "UPPER(DESIGNBUCKET.Name)=?";
		/*boolean bMultiNameCheck = iSlashPos>0 && (iType==DesignElement.DESIGN_TYPE_UNKNOWN || 
						iType==DesignElement.DESIGN_TYPE_ACTION || 
						iType==DesignElement.DESIGN_TYPE_HTTPACCESSIBLE); */
		boolean bMultiNameCheck = iSlashPos>0;
		if(bMultiNameCheck)
			sNamePart = "UPPER(DESIGNBUCKET.Name) IN(?,?,?)";
		String sQuery = "SELECT * FROM DESIGNBUCKET WHERE AppID="+lAppID+" AND "+sNamePart + sTypeClause;

		//System.out.println(sQuery + " " + sDesignName + " " + iSlashPos + " " + iType);

		String sUpperDesignName = sDesignName.toUpperCase();
		Connection cx=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try
		{
			cx = m_pSystem.getSystemConnection();
			stmt = cx.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY); 
			stmt.setString(1, sUpperDesignName);
			if(bMultiNameCheck)
			{
				String sFirstPart = sUpperDesignName.substring(0, iSlashPos);
				String sLastPart = sUpperDesignName.substring(iSlashPos+1);
				//System.out.println(sFirstPart + "/" + sLastPart);
				stmt.setString(2, sFirstPart);
				stmt.setString(3, sLastPart);
			}
			rs = stmt.executeQuery();
			if(rs.next())
			{          
				design=new DesignElement();

				int iDesignBucketID = rs.getInt("DesignBucketID");
				design.setApplicationName(m_sAppName);
				design.setApplicationGroup(m_sAppGroup);
				design.setDesignName(rs.getString("Name"));
				design.setDesignType(rs.getInt("DesignType"));
				design.setContentType(rs.getString("ContentType"));

				design.setDesignData(Util.getBlobBytes(rs, "DesignData"));

				design.setLastModified( puakma.util.Util.getResultSetDateValue(rs, "Updated") );
				rs.close();
				stmt.close();
				sQuery = "SELECT * FROM DESIGNBUCKETPARAM WHERE DesignBucketID=" + iDesignBucketID;
				stmt = cx.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				rs = stmt.executeQuery();
				while (rs.next())
				{
					design.addParameter(rs.getString("ParamName"), rs.getString("ParamValue"));
				}

				//The binary data specified paths to other design elements to "merge" into this one element
				if(design.getParameterValue("CompositeElement").equals("1"))
				{
					createCompositeDesignElement(design);
				}

				//System.out.println(design.toString());
				//if(bufSource!=null) System.out.println(String.valueOf(bufSource));
			}
			else 
				design=null;			
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("HTTPServer.getDesignError", new String[]{sqle.getMessage()}, this);
			sqle.printStackTrace();
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}

		final String LOGIN_PAGE = "$Login";
		//ok, we're after the login page so try to get it from the jar if the default login page in /puakma.pma
		//doesn't exist
		if(design==null && sDesignName.equalsIgnoreCase(LOGIN_PAGE) &&
				(m_sAppGroup.length()==0) && m_sAppName.equalsIgnoreCase("puakma")) 
		{
			//m_pSystem.doDebug(0, "Attempting to load resource from classpath %s/%s.pma", new String[]{sAppGroup, sAppName}, err);
			ClassLoader cl = Thread.currentThread().getClass().getClassLoader();          
			//System.out.println("ClassLoader=" + cl.getClass().getName());
			InputStream is = cl.getResourceAsStream("res/"+LOGIN_PAGE);
			if(is!=null)
			{                      
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try
				{
					byte buf[] = new byte[8192];
					while(is.available()>0)
					{						
						int iRead = is.read(buf);
						if(iRead>0) baos.write(buf, 0, iRead);
					}
					is.close();                  
					design=new DesignElement();
					design.setApplicationName(m_sAppName);
					design.setApplicationGroup(m_sAppGroup);
					design.setDesignName(LOGIN_PAGE);
					design.setDesignType(DesignElement.DESIGN_TYPE_PAGE);
					design.setContentType("text/html");
					design.setDesignData(baos.toByteArray());
					design.setLastModified( new java.util.Date() );
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}          
			}          
		}//if read from classpath

		if(design==null && sDesignName!=null && !sDesignName.equalsIgnoreCase(LOGIN_PAGE))
			m_pSystem.doError("HTTPServer.DesignNotFound", new String[]{m_sAppGroup,m_sAppName,sDesignName, ""+iType}, this);

		if(design!=null) 
		{
			int iLevel = design.getMinifyLevel();
			if(iLevel>0)
			{				
				design.setDesignData(Util.minifyJSCode(design.getContent()));
			}
			m_cacheDesign.addItem(design);
		}

		return design;
	}



	/**
	 * 
	 * @param design
	 */
	private void createCompositeDesignElement(DesignElement design) 
	{
		String sInclude = Util.stringFromUTF8(design.getContent());
		if(design==null || sInclude==null || sInclude.length()==0) return;

		String sContentType = design.getContentType();
		if(sContentType==null) sContentType = "";
		boolean bIsText = sContentType.indexOf("text")>=0;
		Date dtModified = design.getLastModified();
		if(dtModified==null) dtModified = new Date();
		byte bufContent[] = new byte[0];
		byte LINE_BREAK[] = Util.utf8FromString("\r\n\r\n");
		String sPublicDir = m_pSystem.getSystemProperty("HTTPPublicDir");
		ArrayList arrLines = Util.splitString(sInclude, '\n');
		for(int i=0; i<arrLines.size(); i++)
		{
			String sLine = Util.trimChar((String)arrLines.get(i), new char[]{'\r', '\n', ' '});

			if(sLine.length()>0)
			{
				char cFirst = sLine.charAt(0);
				if(cFirst!='#') //not a comment
				{
					if(cFirst=='/')
					{
						if(sPublicDir!=null && sPublicDir.length()>0)
						{
							File f = new File(sPublicDir + sLine);
							//m_pSystem.doDebug(0, "filesystem: [" + f.getAbsolutePath() + "] " + f.length(), this);
							if(f.exists())
							{
								if(bIsText && bufContent.length>0) bufContent = Util.appendBytes(bufContent, LINE_BREAK);
								byte bufData[] = Util.readFile(f, -1);
								bufContent = Util.appendBytes(bufContent, bufData);
								Date dtFileModified = new Date(f.lastModified());
								if(dtFileModified.after(dtModified)) dtModified = dtFileModified;
							}							
						}
					}
					else
					{						
						//m_pSystem.doDebug(0, "design element:[" + sLine + "]", this);
						//TODO There might be some recursion issues here if the programmer inserts the wrong include
						DesignElement deNext = getDesignElement(sLine, DesignElement.DESIGN_TYPE_RESOURCE);
						if(deNext!=null)
						{
							if(bIsText && bufContent.length>0) bufContent = Util.appendBytes(bufContent, LINE_BREAK);
							bufContent = Util.appendBytes(bufContent, deNext.getContent());
							Date dtDELastModified = deNext.getLastModified();
							if(dtDELastModified==null) dtDELastModified = new Date();
							if(dtDELastModified.after(dtModified)) dtModified = dtDELastModified;
						}

					}
				}
			}
		}

		design.setDesignData(bufContent);
	}

	/**
	 * Output to see what this object contains
	 */
	public String toString()
	{
		String CRLF = "\r\n";
		StringBuilder sb = new StringBuilder();
		String sLine = "--------------------------------------" + CRLF;

		sb.append(sLine);
		sb.append("Group: "+m_sAppGroup + CRLF);
		sb.append("App:   "+m_sAppName + CRLF);
		sb.append(">>ROLES" + CRLF);
		String sRoles[] = getRoles();
		for(int i=0; i<sRoles.length; i++)
		{
			sb.append("\t"+sRoles[i]+CRLF);
		}
		sb.append(">>APP PARAMS" + CRLF);
		Parameter params[] = getAllApplicationParameters();
		for(int i=0; i<params.length; i++)
		{
			sb.append("\t"+params[i].Name + ": " + params[i].Value+CRLF);
		}
		sb.append(">>DESIGN CACHE" + CRLF);
		sb.append(m_cacheDesign.toString());

		sb.append(sLine);

		return sb.toString();
	}

	/**
	 * Runs the appropriate action against the document
	 */
	public ActionReturn runActionOnDocument(HTTPSessionContext pSession, HTMLDocument doc, String sActionClass, boolean bOpenPage)
	{
		int STACKTRACE_DEPTH = 999;
		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "runActionOnDocument()", this);
		ActionReturn act_return = null;
		//String szRedirect;
		//StringBuilder sbOut=null;
		SystemContext SysCtx=null;
		long lStart = System.currentTimeMillis();
		ClassLoader ctx_cl = Thread.currentThread().getContextClassLoader();

		if(doc != null && doc.designObject != null)
		{
			try
			{
				if(sActionClass==null)
				{
					if(doc.designObject.getDesignType() == DesignElement.DESIGN_TYPE_ACTION)
						sActionClass = doc.designObject.getDesignName();
					else
					{
						if(bOpenPage)
							sActionClass = doc.designObject.getParameterValue(DesignElement.PARAMETER_OPENACTION);
						else
							sActionClass = doc.designObject.getParameterValue(DesignElement.PARAMETER_SAVEACTION);
					}
				}//szActionClass==null
				if(sActionClass!=null && sActionClass.length()!=0)
				{
					/*
					if(m_http_server.isDebug())
					{
						if(bOpenPage)
							m_pSystem.doDebug(0, "Running OpenAction: " + sActionClass, this);
						else
							m_pSystem.doDebug(0, "Running SaveAction: " + sActionClass, this);
					}  */

					//long lStart = System.currentTimeMillis();
					//BJU. Made shared so that classloader references are maintained.
					//ActionClassLoader aLoader= new ActionClassLoader(this, m_pSystem, m_pSession.getSessionContext(), doc.rPath.Group, doc.rPath.Application, szActionClass);            

					act_return = new ActionReturn();
					SysCtx = (SystemContext)m_pSystem.clone();
					//SessionContext SessCtx = (SessionContext)m_pSession.getSessionContext().clone(); //ditto with the session            
					HTTPSessionContext HTTPSessCtx = new HTTPSessionContext(SysCtx, pSession.getSessionContext(), this.getRequestPath());
					SharedActionClassLoader aLoader = m_pSystem.getActionClassLoader(this.getRequestPath()); //, DesignElement.DESIGN_TYPE_ACTION);
					Class runclass = aLoader.getActionClass(sActionClass, DesignElement.DESIGN_TYPE_ACTION);
					if(runclass==null) return act_return;            
					Object object = runclass.newInstance();
					ActionRunnerInterface act = (ActionRunnerInterface)object;
					act.init(HTTPSessCtx, doc, m_sAppGroup, m_sAppName);

					Thread.currentThread().setContextClassLoader(aLoader);
					act_return.RedirectTo = act.execute();
					Thread.currentThread().setContextClassLoader(ctx_cl);

					//if(m_http_server.isDebug()) SysCtx.checkConnections(doc.rPath.getFullPath(), sessionContext);

					act_return.HasStreamed = act.hasStreamed();
					act_return.bBuffer = act.getByteBuffer();
					act_return.ContentType = act.getContentType(); 
					//long lEnd = System.currentTimeMillis() - lStart;
					//System.out.println(szActionClass + " took " + lEnd + "ms");
				}
			}
			catch(java.lang.OutOfMemoryError ome)
			{
				Thread.currentThread().setContextClassLoader(ctx_cl);

				act_return = null; //in case there is any buffer allocated, remove all allocated memory
				//request a gc then give the JVM some time to garbage collect
				//System.gc();
				try{Thread.sleep(5000);} catch(Exception e){}
				ome.printStackTrace();
				m_pSystem.doError(ome.toString() + " path=[" + doc.rPath.getFullPath()+"] class=["+sActionClass+"]", this);				
				act_return = new ActionReturn();
				return act_return;
			}
			catch(Throwable e)
			{
				Thread.currentThread().setContextClassLoader(ctx_cl);

				String sPath = sActionClass;
				StringBuilder sb = new StringBuilder(256);
				StringBuilder sbRaw = new StringBuilder(256);
				sbRaw.append(e.toString()+"\r\n");
				if(doc!=null) sPath = doc.rPath.getFullPath() + " - " + sActionClass;
				String sRawMsg = m_pSystem.getSystemMessageString("HTTPRequest.ActionExecuteError");
				String sMsg = puakma.error.pmaLog.parseMessage(sRawMsg, new String[]{sPath, e.toString()});
				m_pSystem.doError(sMsg, this);
				StackTraceElement ste[] = e.getStackTrace();
				//if(ste.length>0)
				sb.append("<html>\r\n");
				sb.append("<head><title>500: Internal Server Error</title></head>\r\n");
				sb.append("<body><h1>" + sMsg + "</h1>");
				sb.append("<br/>");
				sb.append("<table cellspacing=\"0\" cellpadding=\"4\">");
				sb.append("<tr bgcolor=\"f0f0f0\"><td><b>Class</b></td><td><b>Method</b></td><td><b>Line</b></td></tr>\r\n");
				for(int i=0; i<ste.length; i++)  
				{
					String sLine = "Class=" + ste[i].getClassName() + " Method=" + ste[i].getMethodName() + " Line=" + ste[i].getLineNumber();
					sbRaw.append(sLine+"\r\n");
					//m_pSystem.doDebug(0, sLine, this);
					sb.append("<tr>");
					sb.append("<td>"+ste[i].getClassName()+"</td>");
					sb.append("<td><i>"+ste[i].getMethodName()+"</i></td>");
					sb.append("<td>"+ste[i].getLineNumber()+"</td>");
					sb.append("</tr>\r\n");
				}
				sb.append("</table>\r\n");
				sb.append("</body></html>\r\n");
				Util.logStackTrace(e, m_pSystem, STACKTRACE_DEPTH);
				//e.printStackTrace();
				//this.sendHTTPResponse(500, sMsg, null, this.HTTP_VERSION, "text/html", sb.toString().getBytes());
				/*if(doc==null)
					sendError(500, "text/html", sb.toString().getBytes());
				else
				{
					doc.replaceItem(Document.ERROR_FIELD_NAME, sbRaw.toString());
					sendPuakmaError(500, doc);
				}*/
			}
		}//if

		long lActionTimeMS = System.currentTimeMillis() - lStart;
		doc.replaceItem("$ActionTimeMS", lActionTimeMS);
		return act_return;
	}


	/**
	 * 
	 * @param sessCtx
	 * @param sRole
	 * @param ur 
	 * @return
	 */
	private boolean hasRoleInternal(SessionContext sessCtx, String sRole, UserRoles ur)
	{				
		if(ur!=null && ur.hasRole(sRole)) return true;

		RequestPath rPath = this.getRequestPath();
		String sAppName = rPath.Application;
		String sAppGroup = rPath.Group;

		Connection cx = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		boolean bHasRole=false;

		if(sRole==null || sAppName==null || sAppName.length()==0) return false;

		boolean bHasGroup = true;
		if(sAppGroup==null || sAppGroup.length()==0) bHasGroup = false;


		String sQuery = "SELECT PERMISSION.Name FROM APPLICATION,ROLE,PERMISSION WHERE UPPER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL) AND APPLICATION.AppID=ROLE.AppID AND ROLE.RoleID=PERMISSION.RoleID AND UPPER(ROLE.RoleName)=?";
		if(bHasGroup)
		{
			sQuery = "SELECT PERMISSION.Name FROM APPLICATION,ROLE,PERMISSION WHERE UPPER(APPLICATION.AppName)=? AND (UPPER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*') AND APPLICATION.AppID=ROLE.AppID AND ROLE.RoleID=PERMISSION.RoleID AND UPPER(ROLE.RoleName)=?";
		}

		try
		{
			cx = m_pSystem.getSystemConnection();
			stmt = cx.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sAppName.toUpperCase());
			if(bHasGroup)
			{
				stmt.setString(2, sAppGroup.toUpperCase());
				stmt.setString(3, sRole.toUpperCase());
			}
			else
				stmt.setString(2, sRole.toUpperCase());
			rs = stmt.executeQuery();
			while (rs.next())
			{
				String sResult = rs.getString(1);
				if(sResult==null || sResult.length()==0) continue;

				X500Name nmResult = new X500Name(sResult);
				X500Name nmUser = sessCtx.getX500Name();
				//check exact match, *=All, partial match (username must be longer than result!)
				if(nmUser.equals(nmResult)
						|| sResult.equals("*")
						|| nmUser.matches(nmResult))
				{
					bHasRole=true;
				}
				else
				{
					//if the user is not anonymous and the role says only logged in users, allow it.
					if(!nmUser.equals(pmaSession.ANONYMOUS_USER) && sResult.equals("!*")) 
						bHasRole=true;
					else //check groups and other roles here recursively
					{
						if(sResult.charAt(0)=='[' && sResult.charAt(sResult.length()-1)==']')
						{                        
							String sNewRole = sResult.substring(1, sResult.length()-1);
							//System.out.println("Checking role contains role "+szResult + " newrole="+sNewRole+"=");
							//call this method again                        
							bHasRole = hasRoleInternal(sessCtx, sNewRole, ur);
						}
						else
							bHasRole = m_pSystem.isUserInGroup(sessCtx, sResult, rPath.getFullPath());
					}
				}
				if(bHasRole) break;
			}//while			
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("TornadoApplication.hasRoleError", new String[]{sqle.getMessage()}, sessCtx);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cx);
		}
		return bHasRole;
	}


	/**
	 * Sets the roles in this application on the session object passed
	 * @param sessCtx
	 */
	public void setUpUserRoles(SessionContext sessCtx)
	{
		TornadoServerInstance tsi = TornadoServer.getInstance();
		m_sRoles = null; //reset the roles cache so we are forced to get it from the db. 

		UserRoles ur;
		RequestPath rPath = this.getRequestPath();
		//if the roles object does not exist
		if(!sessCtx.hasUserRolesObject(rPath.getPathToApplication()))
		{
			ur = new UserRoles(rPath.getPathToApplication());
			String sRoles[] = tsi.getApplicationRoles(rPath.Group, rPath.Application);
			//vAppRoles = getAppRoles(sysCtx, rPath.Application, rPath.Group);
			for(int i=0; i<sRoles.length; i++)
			{
				String sRoleName = sRoles[i];
				if(hasRoleInternal(sessCtx, sRoleName, ur))
				{
					ur.addRole(sRoleName);
				}
			}
			sessCtx.addUserRolesObject(ur);
		}
	}

	/**
	 * Handles a full web request and either writes directly to the outputstream or returns an object
	 * that allows the caller to handle
	 * @param pSession
	 * @param sURI
	 * @param os
	 */
	public TornadoApplicationReply processRequest(HTTPSessionContext pSession, String sRequestedHost, String sURI, boolean bSecure, OutputStream os) 
	{
		if(pSession==null || sURI==null || sURI.length()==0) 
		{
			return new TornadoApplicationReply(500, "Invalid URI", null);
		}

		pSession.setLastTransactionTime();
		if(isApplicationDisabled())
		{
			//sendPuakmaError(RET_FORBIDDEN, docErr);
			return new TornadoApplicationReply(403, "Application is disabled", null);
		}

		RequestPath rPath = new RequestPath(sURI);
		rPath.removeParameter("&logout");
		String document_path = rPath.getFullPath();

		if(rPath.DesignElementName.length()==0) //eg "/someapp.pma/"
		{
			String sDefaultOpen = getApplicationParameter(Document.APPPARAM_DEFAULTOPEN);
			if(sDefaultOpen != null && sDefaultOpen.length()!=0)
			{
				if(sDefaultOpen.charAt(0)=='/')
					document_path = rPath.getPathToApplication() + sDefaultOpen.substring(1, sDefaultOpen.length());
				else
					document_path = rPath.getPathToApplication() + '/' + sDefaultOpen.substring(0, sDefaultOpen.length());
				document_path += rPath.Parameters;
				rPath = new RequestPath(document_path);
				//ArrayList extra_headers = new ArrayList();
				String sProto = null;
				if(bSecure) sProto = "https://"; else sProto = "http://"; //assume must be http of some sort
				//extra_headers.add("Location: " + sProto + m_sRequestedHost + document_path);				
				//sendHTTPResponse(RET_SEEOTHER, "Moved", extra_headers, HTTP_VERSION, "text/html", null);
				TornadoApplicationReply tar = new TornadoApplicationReply(302, "Moved", null);
				tar.addHttpHeader("Location: " + sProto + sRequestedHost + document_path);
				return tar;
			}
		}

		HTMLDocument docHTML = new HTMLDocument(pSession);
		docHTML.setParameters(rPath.Parameters);
		docHTML.rPath = rPath;

		//FIXME don't reference this
		setUpUserRoles(pSession.getSessionContext());
		/*if(m_bIsWidgetRequest)
		{
			doWidgetRequest(docHTML);
			return;
		}*/

		if(pSession.hasUserRole(docHTML.rPath.getPathToApplication(), DENY_ACCESS_ROLE))
		{
			m_pSystem.doError("HTTPRequest.NoAccess", new String[]{pSession.getUserName(), docHTML.rPath.getFullPath()}, this);
			return new TornadoApplicationReply(403, "Access denied", null);
		}

		DesignElement design =  getDesignElement(docHTML.rPath.DesignElementName, docHTML.rPath.DesignType);
		if(design==null)
		{
			//perhaps try getting the requested resource from another place??
			docHTML.designObject = null;
			//RequestReturnCode = RET_FILENOTFOUND;
			//return RequestReturnCode;
			return new TornadoApplicationReply(404, "File not found", null);
		}

		docHTML.PageName = design.getDesignName();
		docHTML.designObject = design;

		ActionReturn ar = runActionOnDocument(pSession, docHTML, null, true);
		//return RequestReturnCode;
		//TODO ...
		TornadoApplicationReply tar = new TornadoApplicationReply(200, "OK", ar.bBuffer);
		tar.setContentType(ar.ContentType);
		return tar;
	}

	/**
	 * Get the key to uniquely reference this object. eg "/xyz/appname" or "//appname when no group specified
	 * @return
	 */
	public String getApplicationKey() 
	{
		RequestPath rp = getRequestPath();
		StringBuilder sb = new StringBuilder(); 

		sb.append('/');
		sb.append(rp.Group);

		sb.append('/');
		sb.append(rp.Application);

		return sb.toString().toLowerCase();
	}

	/**
	 * Gets a handle to a connection object. This version opens the connection by name
	 * If param is null or "" get the first connection available
	 * @param sConnectionName the name of the connection as it appears in the application's design
	 * @return a JDBC connection object, null if one could not be opened
	 */
	public Connection getDataConnection(String sConnectionName) throws Exception
	{

		if(sConnectionName!=null && sConnectionName.equalsIgnoreCase(SystemContext.DBALIAS_SYSTEM))
			return m_pSystem.getSystemConnection();

		Connection cxToReturn=null;
		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		boolean bFound=false;

		String sDriverClass="", sDBURL="", sDBURLOptions="", sDBName="", sUser="", sPW="";
		String sException="";
		String sAppGroup=m_sAppGroup, sAppName=m_sAppName;

		m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "getDataConnection(by_NAME)", this);
		String sKey = sConnectionName;
		if(sKey==null) sKey = "";
		sKey = sKey.toLowerCase();
		if(m_htConnectionAttributeMap.containsKey(sKey))
		{
			bFound = true;
			//System.out.println("cache hit: getDataConnection(String sConnectionName) ");
			HashMap hm = (HashMap)m_htConnectionAttributeMap.get(sKey);
			if(hm!=null)
			{
				sDriverClass = (String)hm.get("dbdriver");
				sDBURL = (String)hm.get("dburl");
				sDBURLOptions = (String)hm.get("dburloptions");
				sDBName = (String)hm.get("dbname");
				sUser = (String)hm.get("dbusername");
				sPW = (String)hm.get("dbpassword");
			}
		}
		else
		{
			if(sAppName==null || sAppName.length()==0) return null;
			boolean bHasGroup = true;
			if(sAppGroup==null || sAppGroup.length()==0) bHasGroup = false;

			String sQuery = "SELECT DBCONNECTION.* FROM APPLICATION,DBCONNECTION WHERE UPPER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL) AND APPLICATION.AppID=DBCONNECTION.AppID";
			if(bHasGroup)
			{
				sQuery = "SELECT DBCONNECTION.* FROM APPLICATION,DBCONNECTION WHERE UPPER(APPLICATION.AppName)=? AND (UPPER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*')AND APPLICATION.AppID=DBCONNECTION.AppID";
			}

			String sConnectJoin = "";
			if(sConnectionName!=null && sConnectionName.length()>0) sConnectJoin=" AND UPPER(DBCONNECTION.DBConnectionName)=?";// + sConnectionName.toUpperCase();

			try
			{
				cxSys = m_pSystem.getSystemConnection();
				stmt = cxSys.prepareStatement(sQuery+sConnectJoin, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				stmt.setString(1, sAppName.toUpperCase());
				if(bHasGroup)
				{
					stmt.setString(2, sAppGroup.toUpperCase());
					if(sConnectJoin.length()>0) stmt.setString(3, sConnectionName.toUpperCase());
				}
				else                        
					if(sConnectJoin.length()>0) stmt.setString(2, sConnectionName.toUpperCase());
				rs = stmt.executeQuery();
				if(rs.next())
				{
					//iDBConnectionID = RS.getInt("DBConnectionID");
					bFound = true;
					sDriverClass = rs.getString("DBDriver");
					sDBURL = rs.getString("DBURL");
					sDBURLOptions = rs.getString("DBURLOptions");
					sDBName = rs.getString("DBName");
					sUser = rs.getString("DBUserName");
					sPW = m_pSystem.decodeValue(rs.getString("DBPassword"));

					HashMap hm = new HashMap();				
					hm.put("dbdriver", sDriverClass);
					hm.put("dburl", sDBURL);
					hm.put("dburlptions", sDBURLOptions);
					hm.put("dbname", sDBName);
					hm.put("dbusername", sUser);
					hm.put("dbpassword", sPW);

					m_htConnectionAttributeMap.put(sKey, hm);
					//mark the connection as used...
					//Stmt.execute("UPDATE DBCONNECTION SET LastUsed=CURRENT_TIMESTAMP WHERE DBConnectionID=" + iDBConnectionID);
				}

			}
			catch(Exception de)
			{
				sException = de.toString();
				m_pSystem.doError("TornadoApplication.getDataConnectionError", new String[]{de.toString()}, this);
				bFound=false;
			}
			finally
			{
				Util.closeJDBC(rs);
				Util.closeJDBC(stmt);
				m_pSystem.releaseSystemConnection(cxSys);
			}
		}
		if(!bFound)
		{
			//m_SysCtx.doError("pmaSystem.getDataConnectionNotFound", new String[]{szConnectionName}, m_SessCtx);
			throw new Exception(pmaLog.parseMessage(m_pSystem.getSystemMessageString("TornadoApplication.getDataConnectionError"), new String[]{sException}) );
		}

		try
		{
			m_pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "Attempting to open a connection: %s, %s, %s, %s, pw_is_hidden", new String[]{sDriverClass, sDBURL, sDBName, sUser}, this);
			cxToReturn=getDataConnection(sDriverClass, sDBURL, sDBURLOptions, sDBName, sUser, sPW);
		}
		catch(Exception e)
		{
			throw new Exception(pmaLog.parseMessage(m_pSystem.getSystemMessageString("TornadoApplication.getDataConnectionError"), new String[]{e.getMessage()}) );
		}

		return cxToReturn;
	}

	/**
	 * 
	 * @param sDriverClass
	 * @param sDBURL
	 * @param sDBURLOptions
	 * @param sDBName
	 * @param sDBUserName
	 * @param sDBPassword
	 * @return
	 * @throws Exception
	 */
	public Connection getDataConnection(String sDriverClass, String sDBURL, String sDBURLOptions, String sDBName, String sDBUserName, String sDBPassword) throws Exception
	{
		if(m_DBPoolMgr==null) return null;
		Connection cx=null;
		String sFullURL = sDBURL + sDBName;
		if(sDBURLOptions!=null && sDBURLOptions.length()>0) sFullURL += sDBURLOptions;

		//this.doDebug(0, "Getting connection to server [" + sFullURL + "] [" + sDriverClass + "] [" + sDBURL + "] ["+ sDBUserName + "]/[" + sDBPassword + "]", this);

		try
		{
			//if no db name is specified we want a server connection. Chances are, we've called this before with bad credentials
			//especially if the webdesign dbconnection page has been visited. So for server only connections, remove any old pools and create a new one with new credentials
			if(sDBName==null || sDBName.length()==0)
			{
				//this.doDebug(0, "Getting connection to server only [" + sFullURL + "] " + sDriverClass + " " + sDBURL + " "+ sDBUserName + "/" + sDBPassword, this);
				m_DBPoolMgr.removePooler(sFullURL); //this may return false, if the pool does not exist					
				m_DBPoolMgr.createPooler(sFullURL, m_iMaxConnectionCount, m_iPoolConnectionTimeoutMS, 0,
						m_iPoolConnectionExpireSeconds, sDriverClass, sFullURL,
						sDBUserName, sDBPassword );
			}
			else
			{
				//if the pool does not exist, try to create one.
				if(!m_DBPoolMgr.hasPool(sFullURL))
				{
					m_DBPoolMgr.createPooler(sFullURL, m_iMaxConnectionCount, m_iPoolConnectionTimeoutMS, 0,
							m_iPoolConnectionExpireSeconds, sDriverClass, sFullURL,
							sDBUserName, sDBPassword );
				}
			}

			cx = m_DBPoolMgr.getConnection(sFullURL);
			m_htConnections.put(cx, sDBName);
			m_iDataConnGetCount++;
		}
		catch(Exception e)
		{
			String szMessage = pmaLog.parseMessage(m_pSystem.getSystemMessageString("TornadoApplication.NoDataConnection"), new String[]{e.getMessage()});
			throw new Exception(szMessage);
		}

		return cx;
	}

	/**
	 * 
	 * @param cx
	 */
	public boolean releaseDataConnection(Connection cx)
	{    
		boolean bReturn = false;
		if(m_DBPoolMgr==null) return bReturn;

		if(cx==null) return bReturn;
		try
		{
			if(m_DBPoolMgr.releaseConnection(cx))
			{
				m_iDataConnReleaseCount++;
				m_htConnections.remove(cx);
				bReturn = true;
			}
			else
				m_pSystem.releaseSystemConnection(cx);
		}
		catch(Exception e)
		{
			m_pSystem.doError("releaseDataConnection() " + e.toString(), this);
		}
		return bReturn;
	}

	/**
	 * 
	 * @param sConnectionName
	 * @param sPropertyName
	 * @return
	 */
	public String getDataConnectionProperty(String sConnectionName, String sPropertyName)
	{    
		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String sReturn=null;

		String sAppGroup=m_sAppGroup, sAppName=m_sAppName;

		//m_SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "getDataConnectionProperty(NAME,propertyname)", m_SessCtx);
		if(sAppName==null || sAppName.length()==0) return null;
		if(sPropertyName==null || sPropertyName.length()==0) return null;
		boolean bHasGroup = true;
		if(sAppGroup==null || sAppGroup.length()==0) bHasGroup = false;

		String sQuery = "SELECT "+sPropertyName+" FROM APPLICATION,DBCONNECTION WHERE UPPER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL) AND APPLICATION.AppID=DBCONNECTION.AppID";
		if(bHasGroup)
		{
			sQuery = "SELECT "+sPropertyName+" FROM APPLICATION,DBCONNECTION WHERE UPPER(APPLICATION.AppName)=? AND (UPPER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*') AND APPLICATION.AppID=DBCONNECTION.AppID";
		}


		try
		{
			String sConnectJoin = "";
			if(sConnectionName!=null && sConnectionName.length()>0) sConnectJoin=" AND UPPER(DBCONNECTION.DBConnectionName)=?";// + sConnectionName.toUpperCase();
			cxSys = m_pSystem.getSystemConnection();
			stmt = cxSys.prepareStatement(sQuery+sConnectJoin, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sAppName.toUpperCase());
			if(bHasGroup)
			{
				stmt.setString(2, sAppGroup.toUpperCase());
				if(sConnectJoin.length()>0) stmt.setString(3, sConnectionName.toUpperCase());
			}
			else                        
				if(sConnectJoin.length()>0) stmt.setString(2, sConnectionName.toUpperCase());
			rs = stmt.executeQuery();
			if(rs.next())
			{          
				sReturn = rs.getString(sPropertyName);
			}

		}
		catch(Exception de)
		{      
			m_pSystem.doError("TornadoApplication.getDataConnectionError", new String[]{de.toString()}, this);      
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cxSys);
		}

		return m_pSystem.decodeValue(sReturn);
	}

	/**
	 * 
	 * @return
	 */
	public Vector getAllDataConnectionNames()
	{
		Vector vReturn = new Vector();
		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String sAppGroup=m_sAppGroup, sAppName=m_sAppName;

		//m_SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "getAllDataConnectionNames()", m_SessCtx);    
		if(sAppName==null || sAppName.length()==0) return null;
		boolean bHasGroup = true;
		if(sAppGroup==null || sAppGroup.length()==0) bHasGroup = false;

		String sQuery = "SELECT DBConnectionName FROM APPLICATION,DBCONNECTION WHERE UPPER(APPLICATION.AppName)=? AND (APPLICATION.AppGroup='' OR APPLICATION.AppGroup IS NULL) AND APPLICATION.AppID=DBCONNECTION.AppID";
		if(bHasGroup)
		{
			sQuery = "SELECT DBConnectionName FROM APPLICATION,DBCONNECTION WHERE UPPER(APPLICATION.AppName)=? AND (UPPER(APPLICATION.AppGroup)=? OR APPLICATION.AppGroup='*')AND APPLICATION.AppID=DBCONNECTION.AppID";
		}

		try
		{                
			cxSys = m_pSystem.getSystemConnection();
			stmt = cxSys.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sAppName.toUpperCase());
			if(bHasGroup)        
				stmt.setString(2, sAppGroup.toUpperCase());
			rs = stmt.executeQuery();
			while(rs.next())
			{
				String sConnName = rs.getString(1);
				if(sConnName!=null && sConnName.length()>0) vReturn.add(sConnName);
			}

		}
		catch(Exception de)
		{      
			m_pSystem.doError("TornadoApplication.getDataConnectionError", new String[]{de.toString()}, this);      
		}
		finally{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cxSys);
		}
		return vReturn;
	}

	public String getErrorSource() 
	{
		return ((m_sAppGroup!=null&&m_sAppGroup.length()>0)?('/' + m_sAppGroup):"") + '/' + m_sAppName + " (" + m_lAppID + ")";
	}

	public String getErrorUser() 
	{
		return m_pSystem.getErrorUser();
	}

	public String getDBPoolStatus() 
	{
		if(m_DBPoolMgr==null || m_DBPoolMgr.getPoolCount()==0) return "";

		return "*** " + getErrorSource()+ "\r\n" + m_DBPoolMgr.getStatus();
	}


	public void finalize()
	{
		//System.out.println("FINALIZE: TornadoApplication " + getErrorSource());
		if(m_DBPoolMgr!=null) m_DBPoolMgr.shutdown();
	}

	/**
	 * 
	 */
	public void resetDatabasePool() 
	{
		m_htConnectionAttributeMap.clear();
		if(m_DBPoolMgr==null || m_DBPoolMgr.getPoolCount()==0) return;
		m_DBPoolMgr.reset();

	}

	/**
	 * Gets the raw String from the application string table. If locale is null, get the default language
	 * @param sStringTableKey
	 * @param locale
	 * @return an empty string if not found
	 */
	public String getStringTableEntry(String sStringTableKey, Locale locale) 
	{		
		if(locale==null) locale = getDefaultLocale();

		String sLanguage = locale.getLanguage(); //eg "en"
		String sCountry = locale.getCountry();//"au"

		String sEntry = getStringTableEntryFromDatabase(sStringTableKey, sLanguage, sCountry);
		if(sEntry==null && sLanguage!=null && sLanguage.length()>0) 
			sEntry = getStringTableEntryFromDatabase(sStringTableKey, sLanguage, null);

		if(sEntry==null)
		{			
			//if we haven't got a value and the session is a different locale to the default, try the default 
			Locale defaultLocale = getDefaultLocale();
			//m_pSystem.doDebug(0, sStringTableKey + " not found for [" + sLanguage+"-"+sCountry + "] isDefaultLocale=" + (locale.equals(defaultLocale)) + " " + defaultLocale.toString(), this);
			if(!locale.equals(defaultLocale)) 
			{
				String sDefaultLanguage = defaultLocale.getLanguage(); //eg "en"
				String sDefaultCountry = defaultLocale.getCountry();//"au"				
				sEntry = getStringTableEntryFromDatabase(sStringTableKey, sDefaultLanguage, sDefaultCountry);
				if(sEntry==null && sDefaultLanguage!=null && sDefaultLanguage.length()>0) 
					sEntry = getStringTableEntryFromDatabase(sStringTableKey, sDefaultLanguage, null);				
			}
		}

		if(sEntry==null) 
		{
			//if key not found, add it to the string table
			addStringTableEntryToDatabase(sStringTableKey, sLanguage, sCountry);
			sEntry = "";
		}
		return sEntry; //"ST:["+sStringTableKey+"] " + (locale==null?"NoLocale":locale.getDisplayName());
	}

	/**
	 * 
	 * @param sStringTableKey
	 * @param sLanguage
	 * @param sCountry
	 */
	private void addStringTableEntryToDatabase(String sStringTableKey, String sLanguage, String sCountry) 
	{
		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		long lAppID = getApplicationID();
		boolean bKeyExists = false;
		try
		{
			cxSys = m_pSystem.getSystemConnection();        
			String sQuery = "SELECT COUNT(StringKey) as tot FROM STRINGTABLEKEY WHERE AppID=? AND LOWER(StringKey)=?";
			stmt = cxSys.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);			
			stmt.setLong(1, lAppID);
			stmt.setString(2, sStringTableKey.toLowerCase());
			rs = stmt.executeQuery();
			if(rs.next())
			{
				bKeyExists = rs.getInt(1)>0;
			}
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);

			if(!bKeyExists)
			{
				sQuery = "INSERT INTO STRINGTABLEKEY(AppID,StringKey,Updated,UpdatedBy,KeyComment) VALUES(?,?,?,?,?)";
				stmt = cxSys.prepareStatement(sQuery);			
				stmt.setLong(1, lAppID);
				stmt.setString(2, sStringTableKey);
				stmt.setTimestamp(3, new Timestamp((new java.util.Date()).getTime())); //updated
				stmt.setString(4, pmaSystem.SYSTEM_ACCOUNT ); //updatedby
				stmt.setString(5, "");
				stmt.execute();
			}
		}
		catch (Exception sqle)
		{
			m_pSystem.doError("TornadoApplication.addStringTableEntryToDatabase", new String[]{sStringTableKey, sqle.toString()}, this);
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cxSys);
		}
	}

	/**
	 * 
	 * @param sStringTableKey
	 * @param sLanguage
	 * @param sCountry
	 * @return
	 */
	private String getStringTableEntryFromDatabase(String sStringTableKey, String sLanguage, String sCountry) 
	{
		String sLangCountryKey = (sStringTableKey + '-' + sLanguage + '-' + (sCountry==null?"":sCountry)).toLowerCase();
		if(m_htStringTable.containsKey(sLangCountryKey)) return (String)m_htStringTable.get(sLangCountryKey);

		String sReturn = null;		
		String sCountryWhere = "";
		if(sCountry!=null && sCountry.length()>0) sCountryWhere = " AND LOWER(Country)=?";

		Connection cxSys=null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		long lAppID = getApplicationID();
		String sQuery = "SELECT StringValue FROM STRINGTABLEKEY,STRINGTABLEVALUE WHERE STRINGTABLEKEY.StringTableKeyID=STRINGTABLEVALUE.StringTableKeyID"+
				" AND AppID=" + lAppID +
				" AND LOWER(StringKey)=?" +
				" AND LOWER(Language)=?" + 
				sCountryWhere;


		try
		{
			cxSys = m_pSystem.getSystemConnection();
			stmt = cxSys.prepareStatement(sQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			stmt.setString(1, sStringTableKey.toLowerCase());
			stmt.setString(2, sLanguage.toLowerCase());
			if(sCountryWhere.length()>0) stmt.setString(3, sCountry.toLowerCase()); 

			rs = stmt.executeQuery();
			if(rs.next())
			{          
				sReturn = rs.getString(1);
				if(sReturn==null) sReturn = "";
				if(!m_htStringTable.containsKey(sLangCountryKey)) 
					m_htStringTable.put(sLangCountryKey, sReturn);
			}
		}
		catch(Exception ste)
		{      
			m_pSystem.doError("TornadoApplication.getStringTableEntry", new String[]{sStringTableKey, ste.toString()}, this);      
		}
		finally
		{
			Util.closeJDBC(rs);
			Util.closeJDBC(stmt);
			m_pSystem.releaseSystemConnection(cxSys);
		}

		return sReturn;
	}

	/**
	 * 
	 * @return
	 */
	private Locale getDefaultLocale() 
	{
		if(m_applicationLocale!=null) return m_applicationLocale;

		//2 digit language, 'en-AU' = english, Australian
		String sLocale = getApplicationParameter(DEFAULT_LOCALE);
		if(sLocale!=null && sLocale.length()>0)
		{
			try
			{
				String sLanguageCountry[] = sLocale.split("-");
				String sLang = null;
				String sCountry = null;
				if(sLanguageCountry!=null && sLanguageCountry.length>0) sLang = sLanguageCountry[0];
				if(sLanguageCountry!=null && sLanguageCountry.length>1) sCountry = sLanguageCountry[1];
				m_applicationLocale  = new Locale(sLang, sCountry);
				return m_applicationLocale;
				//pSession.setLocale(loc);
			}catch(Exception e){}
		}
		// Use the JVM default
		return Locale.getDefault();
	}

	/**
	 * 
	 * @return
	 */
	public synchronized SharedActionClassLoader getActionClassLoader() 
	{
		RequestPath rPath = getRequestPath();
		if(m_sacl==null) m_sacl = new SharedActionClassLoader(m_pSystem, rPath.Group, rPath.Application);

		return m_sacl;
	}

	/**
	 * 
	 * @return
	 */
	public boolean clearClassLoader() 
	{
		if(m_sacl!=null)
		{
			m_sacl = null;
			System.out.println(getRequestPath().getPathToApplication() + " classloader cleared");
			return true;
		}

		return false;
	}

}//class
