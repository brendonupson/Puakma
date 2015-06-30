/** ***************************************************************
DominoLDAPAuthenticator.java
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

import puakma.system.*;
import puakma.error.*;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;


public class DominoLDAPAuthenticator extends pmaAuthenticator
{
    private static String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private String m_sLDAPHost;
    private String m_sBindUserName=null;
    private String m_sBindPassword=null;
    private String m_sBindMethod="simple";
    
    
    public void init()
    {               
        m_sLDAPHost = SysCtx.getSystemProperty("LDAPURL");
        if(m_sLDAPHost==null || m_sLDAPHost.length()==0) m_sLDAPHost = "ldap://localhost:389";
        m_sBindUserName = SysCtx.getSystemProperty("LDAPBindUserName");
        if(m_sBindUserName!=null && m_sBindUserName.length()==0) m_sBindUserName = null;
        m_sBindPassword = SysCtx.getSystemProperty("LDAPBindPassword");
        if(m_sBindPassword!=null && m_sBindPassword.length()==0) m_sBindPassword = null;
        
        m_sBindMethod = SysCtx.getSystemProperty("LDAPBindMethod");
        if(m_sBindMethod==null || m_sBindMethod.length()==0) m_sBindMethod = "simple";//unencrypted
    }
    
    /**
     * Set up the hastable for binding to the ldap dir.
     * The username and password params specify the account to use when binding
     */
    private Hashtable setupJNDIEnvironment(String szUserName, String szPassword)
    {
        Hashtable htJNDI = new Hashtable();
        htJNDI.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
        if(m_sLDAPHost!=null) htJNDI.put(Context.PROVIDER_URL, m_sLDAPHost);
        
        //plain text auth
        htJNDI.put(Context.SECURITY_AUTHENTICATION, m_sBindMethod);
        if(szUserName!=null)
        {
            htJNDI.put(Context.SECURITY_PRINCIPAL, szUserName);
            htJNDI.put(Context.SECURITY_CREDENTIALS, szPassword);
        }
        
        return htJNDI;
    }
    
    
    
    
    /**
     * called each time a person attempts to log in
     */
    public LoginResult loginUser(String szUserName, String szPassword, String szAddress, String szUserAgent, String sAppURI)
    {
        LoginResult loginResult = new LoginResult();
        boolean bFound = false;
        
        Hashtable htJNDI = setupJNDIEnvironment(m_sBindUserName, m_sBindPassword);
        
        try
        {
            DirContext ctx = new InitialDirContext(htJNDI);            
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            
            String sSearchBase = "";
            String sLDAPSearchString = makeSearchString(szUserName);
            NamingEnumeration results = ctx.search(sSearchBase, sLDAPSearchString, constraints);            
            if(results!=null && results.hasMore())
            {
                bFound = true;
                SearchResult sr = (SearchResult)results.next();
                if(results.hasMore())                
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
            SysCtx.doError("Error logging in LDAP user '%s'", new String[]{e.toString()}, this);            
        }
        
        return loginResult;
    }
    
    /**
     * Now try to bind the user we found to the LDAP dir.
     */
    private LoginResult bindUser(SearchResult sr, String szPassword, LoginResult loginResult)
    {        
        try
        {            
            String sDN = sr.getName();
            //System.out.println("Binding as:" + sr.toString());
            Hashtable htJNDI = setupJNDIEnvironment(sDN, szPassword);
            new InitialDirContext(htJNDI);
            
            //if we get here, then the password etc must be OK
            //Attributes att = sr.getAttributes();
            loginResult.ReturnCode = LoginResult.LOGIN_RESULT_SUCCESS;
            X500Name nmUser = new X500Name(sDN, ",");
            nmUser.setSeperator("/");
            loginResult.UserName = nmUser.getCanonicalName();
            //Jake's bugfix follows... ;-)
            loginResult.FirstName = nmUser.getFirstName();
            loginResult.LastName = nmUser.getLastName();
            
        }
        catch(AuthenticationNotSupportedException wp) //wrong password
        {
            loginResult.ReturnCode = LoginResult.LOGIN_RESULT_FAIL;
        }
        catch(Exception e)
        {
            SysCtx.doError("Error binding LDAP user '%s'", new String[]{e.toString()}, this);            
        }
        return loginResult;
    }
    
    
    
    private String makeSearchString(String szUserName)
    {
        String sSearch = "(|";
        sSearch += "(cn=" + szUserName + ")";
        sSearch += "(alias=" + szUserName + ")";
        sSearch += "(sn=" + szUserName + ")";
        sSearch += "(givenname=" + szUserName + ")";
        sSearch += "(uid=" + szUserName + ")";
        
        return sSearch + ")";
    }
    
    
    /**
     * Called by the Puakma server to determine if the given session belongs to 
     * a group
     */
    public boolean isUserInGroup(SessionContext sessCtx, String szGroup, String sAppURI)
    {
        return isUserInGroupPrivate(null, null, sessCtx, szGroup);
    }
    
    
    /**
     * Recursively called... pass null for the first 2 params initially.
     *
     */
    private boolean isUserInGroupPrivate(Hashtable ht, DirContext ctx, SessionContext sessCtx, String szGroup)
    {
        if(ht==null) ht = new Hashtable();       
        SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "isUserInGroupPrivate(%s->%s)", new String[]{sessCtx.getUserName(), szGroup}, this);
        boolean bIsInGroup=false;
        if(ht.containsKey(szGroup)) return false;
        ht.put(szGroup, szGroup);
                
        X500Name nmUser = new X500Name(sessCtx.getUserName());
        nmUser.setSeperator(",");
        try
        {
            if(ctx==null) ctx = new InitialDirContext(setupJNDIEnvironment(m_sBindUserName, m_sBindPassword));
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{"member"});
            
            String sSearchBase = "";
            String sLDAPSearchString = "(&(objectclass=groupofuniquenames)(cn=" + szGroup + "))";
            NamingEnumeration results = ctx.search(sSearchBase, sLDAPSearchString, constraints);            
            while(results!=null && results.hasMore())
            {
                SearchResult sr = (SearchResult)results.next();                
                Attributes att = sr.getAttributes();
                if(att!=null)
                {
                    NamingEnumeration nme = att.getAll();
                    while(nme!=null && nme.hasMore())
                    {
                        Attribute lda = (Attribute)nme.next();
                        for(int i=0; i<lda.size(); i++)
                        {
                            String sName = (String)lda.get(i);  
                            X500Name nmResult = new X500Name(sName, ",");                            
                            //System.out.println("...checking " + nmUser.toString() + " against " + nmResult.toString());
                            if(nmUser.equals(nmResult)
                                || sName.equals("*")
                                || nmUser.matches(nmResult)) //check a partial match, eg: "*/Mkt/YourCo"
                            {
                                SysCtx.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "User '%s' is in group '%s'", new String[]{sessCtx.getUserName(), szGroup}, sessCtx);                                
                                bIsInGroup =true;
                                break;
                             }
                             else
                             {
                                bIsInGroup = isUserInGroupPrivate(ht, ctx, sessCtx, nmResult.getAbbreviatedName());                                
                                if(bIsInGroup) break;
                             }                                                        
                        }
                    }
                }
                
            }
            
        }
        catch(Exception e)
        {
            SysCtx.doError("Error recursing LDAP groups '%s'", new String[]{e.toString()}, this);            
        }
        
        return bIsInGroup;
    }
    
}//end of class