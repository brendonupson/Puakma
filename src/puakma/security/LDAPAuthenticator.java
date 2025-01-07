/** ***************************************************************
LDAPAuthenticator.java
Copyright (C) 2001  Brendon Upson 
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



/**
 *
 * This authenticator is used to authenticate against an LDAP directory. 
 * Ensure the following settings are in your puakma.config file
 *
#********* LDAP SETTINGS **********
LDAPURL=ldap://yourserver.com:389
LDAPBindMethod=simple
LDAPBindUserName=
LDAPBindPassword=
LDAPSearchBase=
# following may be omitted, defaults shown
LDAPGroupSearchString=(objectClass=user)
LDAPUserSearchString=(&(objectclass=user)(|(cn=%s)(uid=%s)(sAMAccountName=%s)))

 *
 *
 * The bind username and password may be left blank if your LDAP dir  supports
 * anonymous connections. Methods other than "simple" have NOT be implemented 
 * nor tested.
 *
 * To install:
 * 1. copy the LDAPAuthenticator.class file into the /puakma/addins directory
 * 2. in the puakma.config file, alter the Authenticators= line to read
 * Authenticators=LDAPAuthenticator
 *
 * This implementation is based on Sun's JNDI technology.
 *
 *
 *
 *****************************************************************************
 *****************************************************************************
 *
 * Author: Brendon Upson
 * Created: 8 November 2002
 * copyright: webWise Network Consultants Pty Ltd
 * http://www.puakma.net
 * mailto:puakma@puakma.net
 *
 * This file is distributed under an open license. This file may be freely changed, recompiled 
 * and used commercially. No warranty is given, either express or implied. This 
 * code may be used at your own risk. webWise Network Consultants assume no 
 * liability through its use.
 *
 */

package puakma.security;

import java.util.ArrayList;
import java.util.Hashtable;

import javax.naming.AuthenticationException;
import javax.naming.AuthenticationNotSupportedException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import puakma.system.SessionContext;
import puakma.system.X500Name;
import puakma.system.pmaSession;
import puakma.util.Util;


public class LDAPAuthenticator extends pmaAuthenticator
{
	/*
	 * Simple: Authenticates fast using plain text usernames and passwords.
	 * SSL: Authenticates with SSL encryption over the network.
	 * SASL: Uses MD5/Kerberos mechanisms. SASL is a simple authentication and security layer-based scheme
	 */
	private String m_sBindMethod="simple";

	private static String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";	
	private String m_sLDAPHostConfigEntry;
	private String m_sLDAPHosts[] = null;
	private String m_sBindUserName=null;
	private String m_sBindPassword=null;	
	private String m_sGroupSearchString = "(objectClass=user)"; //"(&(objectClass=user)(distinguishedName=%s))"; 
	private String m_sUserSearchString = "(&(objectclass=user)(|(cn=%s)(uid=%s)(sAMAccountName=%s)))";
	private String m_sLDAPSearchBase = "";
	private String m_sLDAPDNAttribute = "distinguishedName";//"distinguishedName" used by ActiveDirectory
	private String m_sLDAPGroupMemberAttribute = "memberOf"; //"groupMembership" for NDS
	private String m_sLDAPSocketFactory = null; //eg "puakma.util.RelaxedSSLSocketFactory"
	private String m_sFirstNameSurname[] = new String[]{"givenName", "sn"};
	private long m_lConnectTimeoutMS = 5000;
	private boolean m_bDebug = false;



	public void init()
	{          
		/*
		ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
		ClassLoader newCl = this.getClass().getClassLoader();
		System.out.println("Context classloader:" + prevCl.getClass().getName());
		System.out.println("New ctx classloader:" + newCl.getClass().getName());
		Thread.currentThread().setContextClassLoader(newCl);
		 */
		m_sLDAPHostConfigEntry = SysCtx.getSystemProperty("LDAPURL");
		if(m_sLDAPHostConfigEntry==null || m_sLDAPHostConfigEntry.length()==0) m_sLDAPHostConfigEntry = "ldap://localhost:389";
		m_sBindUserName = SysCtx.getSystemProperty("LDAPBindUserName");
		if(m_sBindUserName!=null && m_sBindUserName.length()==0) m_sBindUserName = null;
		m_sBindPassword = SysCtx.getSystemProperty("LDAPBindPassword");
		if(m_sBindPassword!=null && m_sBindPassword.length()==0) m_sBindPassword = null;

		m_sBindMethod = SysCtx.getSystemProperty("LDAPBindMethod");
		if(m_sBindMethod==null || m_sBindMethod.length()==0) m_sBindMethod = "simple";//unencrypted

		String sTemp = SysCtx.getSystemProperty("LDAPGroupSearchString");
		if(sTemp!=null && sTemp.length()>0) m_sGroupSearchString = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPUserSearchString");
		if(sTemp!=null && sTemp.length()>0) m_sUserSearchString = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPSearchBase");
		if(sTemp!=null && sTemp.length()>0) m_sLDAPSearchBase = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPDNAttribute");
		if(sTemp!=null && sTemp.length()>0) m_sLDAPDNAttribute = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPGroupMemberAttribute");
		if(sTemp!=null && sTemp.length()>0) m_sLDAPGroupMemberAttribute = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPFNameAttribute");
		if(sTemp!=null && sTemp.length()>0) m_sFirstNameSurname[0] = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPLNameAttribute");
		if(sTemp!=null && sTemp.length()>0) m_sFirstNameSurname[1] = sTemp;

		sTemp = SysCtx.getSystemProperty("LDAPSocketFactory");
		if(sTemp!=null && sTemp.length()>0) m_sLDAPSocketFactory = sTemp;


		sTemp = SysCtx.getSystemProperty("LDAPConnectTimeoutSeconds");
		if(sTemp!=null && Util.toInteger(sTemp)>0) m_lConnectTimeoutMS = Util.toInteger(sTemp)*1000;

		m_bDebug = Util.toInteger(SysCtx.getSystemProperty("LDAPDebug"))==1;
	}

	/**
	 * For testing...
	 */
	private void doStaticSetup()
	{
		/*	
		 m_sLDAPHostConfigEntry = "ldaps://ldap1";		 
		m_sBindMethod="simple";
		m_sLDAPSearchBase="o=GoldingContractors";
		m_sBindUserName="cn=puakma,ou=Puakma,o=goldingcontractors";
		m_sBindPassword="zzz";
		m_sLDAPGroupMemberAttribute="groupMembership";
		m_sLDAPDNAttribute = "";
		 */


		//m_sLDAPHostConfigEntry = "ldaps://ad.golding.com.au";
		m_sLDAPHostConfigEntry = "ldaps://vdcadc01.ad.golding.com.au";
		m_sBindMethod="simple";
		//m_sLDAPSearchBase="OU=Sites,DC=ad,DC=golding,DC=com,DC=au";
		m_sLDAPSearchBase="DC=ad,DC=golding,DC=com,DC=au";
		m_sBindUserName="CN=SVC_PUAKMA,OU=Global Service Accounts,OU=Administration,DC=ad,DC=golding,DC=com,DC=au";
		m_sBindPassword="zzz";
		m_sLDAPSocketFactory = "puakma.util.RelaxedSSLSocketFactory";

	}

	public static void main(String sArgs[])
	{
		LDAPAuthenticator ldap = new LDAPAuthenticator();
		ldap.doStaticSetup();
		System.out.println("---------------------------"); 
		System.out.println("Using: " + ldap.m_sLDAPHostConfigEntry);
		ldap.setConnectTimeoutMS(2000);
		long lStart = System.currentTimeMillis();
		LoginResult lr = ldap.loginUser("SVC_ClemLimitedAccess", "zzz", "", "", "");


		System.out.println(lr.toString()); 
		System.out.println("Took: " + (System.currentTimeMillis()-lStart) + "ms");
		System.out.println("---------------------------"); 

		//String sUserName = "CN=upsonb,OU=Users,OU=MainOffice,OU=Gladstone,OU=QLD,OU=AU,O=GoldingContractors";

		String sUserName = "CN=SVC_ClemLimitedAccess,OU=Role Based Service Accounts,OU=Administration,DC=ad,DC=golding,DC=com,DC=au";
		String sGroupName = "DC_Limited Admin"; //"PuakmaGlobalAdmin";
		lStart = System.currentTimeMillis();
		boolean b = ldap.isUserInGroupPrivate(sUserName, sGroupName);
		System.out.println(sUserName +" in group ["+sGroupName+"] = "+b);
		System.out.println("Took: " + (System.currentTimeMillis()-lStart) + "ms");


		lStart = System.currentTimeMillis();
		String sGroup2 = "PuakmaGlobalAdmin";
		boolean b2 = ldap.isUserInGroupPrivate(sUserName, sGroup2);
		System.out.println(sUserName +" in group ["+sGroup2+"] = "+b2);
		System.out.println("Took: " + (System.currentTimeMillis()-lStart) + "ms");

		/*String sUserName = "CN=Brendon Upson/OU=Administrators/DC=desqld/DC=internal";//"CN=Brendon Upson/CN=Users/DC=wnc/DC=net/DC=au";
		boolean b = ldap.isUserInGroupPrivate(sUserName, "SEC-AC-ADMIN-MOEINTERFACE-ADMINISTRATOR");
		System.out.println("inGroup="+b);
		 */
	}

	/**
	 * 
	 * @param iMilliseconds
	 */
	public void setConnectTimeoutMS(int iMilliseconds) 
	{
		m_lConnectTimeoutMS = iMilliseconds;		
	}

	/**
	 * Set up the hastable for binding to the ldap dir.
	 * The username and password params specify the account to use when binding
	 */
	private Hashtable setupJNDIEnvironment(String szUserName, String szPassword)
	{
		Hashtable<String, String> htJNDI = new Hashtable<String, String>();		
		htJNDI.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
		//if(m_sLDAPHostConfigEntry!=null) htJNDI.put(Context.PROVIDER_URL, m_sLDAPHostConfigEntry);

		//plain text auth
		htJNDI.put(Context.SECURITY_AUTHENTICATION, m_sBindMethod);
		/*
		if(m_sLDAPHost!=null && m_sLDAPHost.toLowerCase().startsWith("ldaps:"))
		{
			htJNDI.put(Context.SECURITY_PROTOCOL, "ssl");               
			htJNDI.put("java.naming.ldap.factory.socket", "puakma.util.RelaxedSSLSocketFactory");
			//so we don't have to mess with the classpath that initiates the jvm
			ClassLoader newCl = this.getClass().getClassLoader();
			Thread.currentThread().setContextClassLoader(newCl);
		}*/
		if(szUserName!=null)
		{
			htJNDI.put(Context.SECURITY_PRINCIPAL, szUserName);
			htJNDI.put(Context.SECURITY_CREDENTIALS, szPassword);
		}


		htJNDI.put("com.sun.jndi.ldap.connect.pool.protocol", "plain ssl"); //BU new
		htJNDI.put("com.sun.jndi.ldap.connect.pool", "true");//Set connection pooling 
		//System.out.println(i + ". Trying: " + sLDAPHosts[i]);		
		if(m_lConnectTimeoutMS>0) htJNDI.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(m_lConnectTimeoutMS));		
		htJNDI.put("com.sun.jndi.ldap.connect.pool.maxsize","30");
		htJNDI.put("com.sun.jndi.ldap.connect.pool.timeout", "600000");//ms

		return htJNDI;
	}

	/**
	 * 
	 * @return
	 */
	private String[] getLDAPHosts()
	{
		if(m_sLDAPHosts!=null) return m_sLDAPHosts;
		if(m_sLDAPHostConfigEntry==null) m_sLDAPHostConfigEntry="";

		ArrayList<String> arr = Util.splitString(m_sLDAPHostConfigEntry, ',');

		ArrayList<String> arrParsedHosts = new ArrayList<String>();
		for(int i=0; i<arr.size(); i++)
		{
			String sHostURL = Util.trimSpaces((String) arr.get(i));
			if(sHostURL!=null && sHostURL.length()>0) arrParsedHosts.add(sHostURL);
		}

		m_sLDAPHosts = new String[arrParsedHosts.size()];
		for(int i=0; i<arrParsedHosts.size(); i++)
		{
			String sHostURL = Util.trimSpaces((String) arrParsedHosts.get(i));
			m_sLDAPHosts[i] = sHostURL;
		}

		return m_sLDAPHosts;
	}

	/**
	 * 
	 * @param htJNDI
	 * @return
	 * @throws NamingException
	 */
	private DirContext getInitialDirContext(String sUserName, String sPassword) throws Exception
	{
		String sLDAPHosts[] = getLDAPHosts();

		if(sLDAPHosts!=null)
		{
			String sProviderURLs = Util.implode(sLDAPHosts, " ");

			//System.err.println("Trying LDAP host(s) [" + sProviderURLs +"]");
			Hashtable htJNDI = setupJNDIEnvironment(sUserName, sPassword);

			htJNDI.remove(Context.PROVIDER_URL);
			htJNDI.remove(Context.SECURITY_PROTOCOL);
			htJNDI.remove("java.naming.ldap.factory.socket");

			htJNDI.put(Context.PROVIDER_URL, sProviderURLs);

			if(m_sLDAPSocketFactory != null && m_sLDAPSocketFactory.length()>0)
			{
				htJNDI.put("java.naming.ldap.factory.socket", m_sLDAPSocketFactory);//"puakma.util.RelaxedSSLSocketFactory"
				//so we don't have to mess with the classpath that initiates the jvm
				ClassLoader newCl = this.getClass().getClassLoader();
				Thread.currentThread().setContextClassLoader(newCl);
			}
			
			//if ANY entry is secure, all must be. can't mix ldap:xxx and ldaps:xxx
			if(sProviderURLs.toLowerCase().indexOf("ldaps://")>=0)
			{
				// https://docs.oracle.com/javase/jndi/tutorial/ldap/security/ssl.html
				htJNDI.remove("com.sun.jndi.ldap.connect.timeout");//there's a bug that stops this working with ssl. Grrr				
				htJNDI.put(Context.SECURITY_PROTOCOL, "ssl");
			}
			/*if(sLDAPHosts[i]!=null && sLDAPHosts[i].toLowerCase().startsWith("ldaps:"))
				{
					// https://stackoverflow.com/questions/14459280/i-need-to-use-multiple-ldap-provider-how-can-i-check-ldap-server-availability
					// see http://www-01.ibm.com/support/docview.wss?uid=swg21242786
					// https://bugs.openjdk.java.net/browse/JDK-8173451
					htJNDI.remove("com.sun.jndi.ldap.connect.timeout");//there's a bug that stops this working with ssl. Grrr
					htJNDI.put(Context.SECURITY_PROTOCOL, "ssl");               
					htJNDI.put("java.naming.ldap.factory.socket", "puakma.util.RelaxedSSLSocketFactory");
					//so we don't have to mess with the classpath that initiates the jvm
					ClassLoader newCl = this.getClass().getClassLoader();
					Thread.currentThread().setContextClassLoader(newCl);
				}*/

			try
			{
				long lStart = System.currentTimeMillis();
				InitialDirContext ctx = new InitialDirContext(htJNDI);
				System.err.println("Using LDAP host: " + ctx.getEnvironment().get(Context.PROVIDER_URL) + "  (" + (System.currentTimeMillis()-lStart) + "ms)");

				//System.out.println("OK: " + sLDAPHosts[i]);
				return ctx;
			}
			catch(javax.naming.CommunicationException ce)
			{
				System.err.println("ERR: " + ce.toString());
			}
			catch(Exception e) //catch all
			{					
				throw e; 
			}
			//System.err.println("LDAP host is unavailable [" + sLDAPHosts[i] +"]");
		}
		throw new javax.naming.CommunicationException("No LDAP servers appear to be available: [" + m_sLDAPHostConfigEntry +"]");

	}

	/*
	private DirContext old_getInitialDirContext(String sUserName, String sPassword) throws NamingException
	{
		String sLDAPHosts[] = getLDAPHosts();

		if(sLDAPHosts!=null)
		{
			for(int i=0; i<sLDAPHosts.length; i++)
			{
				System.err.println("Trying LDAP host [" + sLDAPHosts[i] +"] " + (i+1) +"/"+sLDAPHosts.length);
				Hashtable htJNDI = setupJNDIEnvironment(sUserName, sPassword);

				htJNDI.remove(Context.PROVIDER_URL);
				htJNDI.remove(Context.SECURITY_PROTOCOL);
				htJNDI.remove("java.naming.ldap.factory.socket");

				if(sLDAPHosts[i]!=null) htJNDI.put(Context.PROVIDER_URL, sLDAPHosts[i]);
				if(sLDAPHosts[i]!=null && sLDAPHosts[i].toLowerCase().startsWith("ldaps:"))
				{
					// https://stackoverflow.com/questions/14459280/i-need-to-use-multiple-ldap-provider-how-can-i-check-ldap-server-availability
					// see http://www-01.ibm.com/support/docview.wss?uid=swg21242786
					// https://bugs.openjdk.java.net/browse/JDK-8173451
					htJNDI.remove("com.sun.jndi.ldap.connect.timeout");//there's a bug that stops this working with ssl. Grrr
					htJNDI.put(Context.SECURITY_PROTOCOL, "ssl");               
					htJNDI.put("java.naming.ldap.factory.socket", "puakma.util.RelaxedSSLSocketFactory");
					//so we don't have to mess with the classpath that initiates the jvm
					ClassLoader newCl = this.getClass().getClassLoader();
					Thread.currentThread().setContextClassLoader(newCl);
				}

				try
				{
					InitialDirContext ctx = new InitialDirContext(htJNDI);
					//System.out.println("OK: " + sLDAPHosts[i]);
					return ctx;
				}
				catch(javax.naming.CommunicationException ce){System.err.println("ERR: " + ce.toString());}
				catch(NamingException ne)
				{					
					throw ne; 
				}
				System.err.println("LDAP host is unavailable [" + sLDAPHosts[i] +"]");
			}//for
		}
		throw new javax.naming.CommunicationException("No LDAP servers appear to be available: [" + m_sLDAPHostConfigEntry +"]");
	}*/




	/**
	 * called each time a person attempts to log in
	 */
	public LoginResult loginUser(String szUserName, String szPassword, String szAddress, String szUserAgent, String sAppURI)
	{
		LoginResult loginResult = new LoginResult();
		boolean bFound = false;
		DirContext ctx = null;

		try
		{
			long lStart = System.currentTimeMillis();			 

			ctx = getInitialDirContext(m_sBindUserName, m_sBindPassword);
			System.out.println("Bind for [" + m_sBindUserName + "]   took: " + (System.currentTimeMillis()-lStart) + "ms");

			lStart = System.currentTimeMillis();
			SearchControls constraints = new SearchControls();
			constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
			if(m_sLDAPDNAttribute!=null && m_sLDAPDNAttribute.length()>0) constraints.setReturningAttributes(new String[]{m_sLDAPDNAttribute});

			String sSearchBase = m_sLDAPSearchBase;
			String sLDAPSearchString = parseSearchString(m_sUserSearchString, szUserName); //makeSearchString(szUserName);
			if(m_bDebug) SysCtx.doDebug(0, "UserSearchString=" + m_sUserSearchString, this);
			NamingEnumeration results = ctx.search(sSearchBase, sLDAPSearchString, constraints);            
			if(results!=null && results.hasMore())
			{
				bFound = true;
				System.out.println("Search for [" + sLDAPSearchString + "]   took: " + (System.currentTimeMillis()-lStart) + "ms");
				SearchResult sr = (SearchResult)results.next();
				boolean bMoreResults = false;
				try{ bMoreResults = results.hasMore(); }catch(NamingException ne){}
				if(bMoreResults)                
					loginResult.ReturnCode = LoginResult.LOGIN_RESULT_TOO_MANY_MATCHES;                                    
				else   				
					return bindUser(sr, szPassword, loginResult);                
			}

			if(!bFound)
			{
				loginResult.ReturnCode = LoginResult.LOGIN_RESULT_INVALID_USER;
			}

		}
		catch(Exception e)
		{
			if(SysCtx!=null)
				SysCtx.doError("Error logging in LDAP user '%s'", new String[]{e.toString()}, this);            
			else
				e.printStackTrace();
		}
		finally
		{
			try {
				if(ctx!=null)				
					ctx.close();
			} catch (Exception e) {}
		}

		return loginResult;
	}

	/**
	 * Now try to bind the user we found to the LDAP dir.
	 */
	private LoginResult bindUser(SearchResult sr, String sPassword, LoginResult loginResult)
	{  
		//System.out.println("Attempting Bind as:" + sr.toString());

		/**
		 * There is something in the JNDI JDK that allows a bind with no password.
		 * So we force a check, if no pw supplied, bomb them out.
		 */
		if(sPassword==null || sPassword.length()==0)
		{
			loginResult.ReturnCode = LoginResult.LOGIN_RESULT_FAIL;
			return loginResult;
		}

		try
		{       
			String sDN = null;
			Attributes attrs = sr.getAttributes();
			if(attrs==null) return null;
			// || m_sLDAPDNAttribute.trim().length()==0 || m_sLDAPDNAttribute==null
			Attribute att = null;
			if(m_sLDAPDNAttribute!=null && m_sLDAPDNAttribute.trim().length()>0) att = attrs.get(m_sLDAPDNAttribute);
			if(att!=null)
			{
				//System.out.println(att.get());
				sDN = String.valueOf(att.get());
			}
			else
			{
				if(m_sLDAPDNAttribute!=null && m_sLDAPDNAttribute.length()>0) System.err.println("LDAPDNAttribute=" + m_sLDAPDNAttribute + " does not exist in this schema.");
				sDN = sr.getName() + "," + m_sLDAPSearchBase;
			}

			long lStart = System.currentTimeMillis();			 
			DirContext ctx = getInitialDirContext(sDN, sPassword);
			ctx.close();
			System.out.println("Bind for [" + sDN + "]   took: " + (System.currentTimeMillis()-lStart) + "ms");

			//if we get here, then the password etc must be OK
			loginResult.ReturnCode = LoginResult.LOGIN_RESULT_SUCCESS;
			X500Name nmUser = new X500Name(sDN, ",");
			nmUser.setSeperator("/");
			loginResult.UserName = nmUser.getCanonicalName();
			//Jake's bugfix follows... ;-)
			//set defaults
			loginResult.FirstName = nmUser.getFirstName();
			loginResult.LastName = nmUser.getLastName();
			//now get from ldap if they exist
			Attribute aFName = attrs.get(m_sFirstNameSurname[0]);
			Attribute aLName = attrs.get(m_sFirstNameSurname[1]);
			if(aFName!=null) loginResult.FirstName = (String)aFName.get();
			if(aLName!=null) loginResult.LastName = (String)aLName.get();


		}
		catch(AuthenticationNotSupportedException wp) //wrong password
		{
			loginResult.ReturnCode = LoginResult.LOGIN_RESULT_FAIL;
			wp.printStackTrace();
		}
		catch(AuthenticationException ae)
		{
			//bad password
			loginResult.ReturnCode = LoginResult.LOGIN_RESULT_FAIL;
		}
		catch(Exception e)
		{
			if(SysCtx!=null )SysCtx.doError("Error binding LDAP user '%s'", new String[]{e.toString()}, this);
			e.printStackTrace();
		}
		return loginResult;
	}


	/*
	 *
	 */
	/*private String makeSearchString(String szUserName)
    {
        String sSearch = "(|";
        sSearch += "(cn=" + szUserName + ")";
        sSearch += "(alias=" + szUserName + ")";
        sSearch += "(sn=" + szUserName + ")";
        sSearch += "(givenname=" + szUserName + ")";
        sSearch += "(uid=" + szUserName + ")";

        return sSearch + ")";
    }*/


	/**
	 * Called by the Puakma server to determine if the given session belongs to 
	 * a group
	 */
	public boolean isUserInGroup(SessionContext sessCtx, String szGroup, String sAppURI)
	{
		return isUserInGroupPrivate(sessCtx.getUserName(), szGroup);
	}

	/**
	 * Find the user object then look at the "memberOf" (for NDS use "groupMembership") attributes.
	 */
	private boolean isUserInGroupPrivate(String sUserName, String sGroup)
	{               
		//don't bother checking anonymous users group memberships in LDAP
		if(sUserName.equalsIgnoreCase("CN="+pmaSession.ANONYMOUS_USER) || sUserName.equalsIgnoreCase(pmaSession.ANONYMOUS_USER)) return false;

		DirContext ctx = null;
		if(m_bDebug) SysCtx.doDebug(0, "isUserInGroupPrivate(\""+sUserName + "\",\"" + sGroup + "\") attr:" + m_sLDAPGroupMemberAttribute, this);
		X500Name nmUser = new X500Name(sUserName);
		nmUser.setSeperator(",");
		X500Name nmFindGroup = new X500Name(sGroup);
		nmFindGroup.setSeperator(",");
		try
		{
			ctx = getInitialDirContext(m_sBindUserName, m_sBindPassword);            
			SearchControls constraints = new SearchControls();
			//Specify the search scope
			constraints.setSearchScope(SearchControls.OBJECT_SCOPE);            
			constraints.setReturningAttributes(new String[]{m_sLDAPGroupMemberAttribute});//"groupMembership"});

			String sSearchBase = nmUser.getCanonicalName();
			//String sLDAPSearchString = m_sGroupSearchString;
			String sLDAPSearchString = parseSearchString(m_sGroupSearchString, sSearchBase);
			System.out.println(sLDAPSearchString);
			NamingEnumeration results = ctx.search(sSearchBase, sLDAPSearchString, constraints);
			if(results!=null && results.hasMore())
			{

				SearchResult sr = (SearchResult)results.next();                
				System.out.println("Found=["+sr.getName() + "]");

				//System.out.println("looking for=["+nmFindGroup.getCanonicalName()+"]["+nmFindGroup.getCommonName()+"]");
				Attributes attrs = sr.getAttributes();                    
				Attribute aGroups = attrs.get(m_sLDAPGroupMemberAttribute);
				if(aGroups!=null)
				{                    
					for(int i=0; i<aGroups.size(); i++)
					{
						String sGroupMembership = (String)aGroups.get(i);
						X500Name nmGroup = new X500Name(sGroupMembership, ",");
						if(m_bDebug) SysCtx.doDebug(0, "found=["+nmGroup.getCanonicalName()+"]["+nmGroup.getCommonName()+"]", this);                        
						if(nmGroup.getCommonName().equalsIgnoreCase(nmFindGroup.getCommonName())) return true;                    
					}//for
				}
			}//if has results

		}
		catch(Exception e)
		{
			if(SysCtx!=null) 
				SysCtx.doError("Error finding user's group memberships '%s'", new String[]{e.toString()}, this);            
			else
				e.printStackTrace();
		}
		finally
		{
			try {
				if(ctx!=null)				
					ctx.close();
			} catch (Exception e) {}
		}

		return false;
	}


	/**
	 * Called when a user has a SSO token and is authenticating automatically
	 */
	public LoginResult populateSession(String sCanonicalName, String sURI)
	{
		LoginResult loginResult = new LoginResult();
		if(sCanonicalName==null || sCanonicalName.length()==0) return loginResult;

		DirContext ctx = null;
		X500Name nmUser = new X500Name(sCanonicalName);
		nmUser.setSeperator(",");        
		try
		{
			//Hashtable htJNDI = setupJNDIEnvironment(m_sBindUserName, m_sBindPassword);
			ctx = getInitialDirContext(m_sBindUserName, m_sBindPassword);//new InitialDirContext(htJNDI);            
			SearchControls constraints = new SearchControls();
			//Specify the search scope
			constraints.setSearchScope(SearchControls.OBJECT_SCOPE);            
			constraints.setReturningAttributes(m_sFirstNameSurname);

			String sSearchBase = nmUser.getCanonicalName();
			String sLDAPSearchString = m_sGroupSearchString;
			NamingEnumeration results = ctx.search(sSearchBase, sLDAPSearchString, constraints);
			if(results!=null && results.hasMore())
			{                
				SearchResult sr = (SearchResult)results.next();
				boolean bMore = false;
				try{ bMore = results.hasMore(); }catch(Exception e){}
				if(bMore)                
					loginResult.ReturnCode = LoginResult.LOGIN_RESULT_TOO_MANY_MATCHES;                                    
				else  
				{
					loginResult.ReturnCode = LoginResult.LOGIN_RESULT_SUCCESS;
					//X500Name nmFoundUser = new X500Name(sCanonName, ",");
					Attributes attrs = sr.getAttributes();
					Attribute aFName = attrs.get(m_sFirstNameSurname[0]);
					Attribute aLName = attrs.get(m_sFirstNameSurname[1]);
					if(aFName!=null) loginResult.FirstName = (String)aFName.get();
					if(aLName!=null) loginResult.LastName = (String)aLName.get();                         
					nmUser.setSeperator("/");
					loginResult.UserName = sCanonicalName;//nmFoundUser.getCanonicalName();             
				}

			}//if has results

		}
		catch(Exception e)
		{
			if(SysCtx!=null) 
				SysCtx.doError("Error populating session '%s'", new String[]{e.toString()}, this);            
			else
				e.printStackTrace();
		}
		finally
		{
			try {
				if(ctx!=null)				
					ctx.close();
			} catch (Exception e) {}
		}
		return loginResult;
	}


	/**
	 *
	 */
	public static String parseSearchString(String sSearchQuery, String sReplacement)
	{
		String sParam = "%s";
		int iPos = sSearchQuery.indexOf(sParam);
		while(iPos>=0)
		{
			String sFirstPart = sSearchQuery.substring(0, iPos);
			String sLastPart = sSearchQuery.substring(iPos+sParam.length(), sSearchQuery.length());
			sSearchQuery = sFirstPart + sReplacement + sLastPart;
			iPos = sSearchQuery.indexOf(sParam);
		}
		return sSearchQuery;
	}

}//end of class