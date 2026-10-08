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
import puakma.util.Util;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;


public class DominoLDAPAuthenticator extends pmaAuthenticator
{
    private static String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final int MAX_GROUPS_VISITED = 500;
    private static final int MAX_GROUPS_PER_SEARCH = 50;
    private String m_sLDAPHost;
    private String m_sBindUserName=null;
    private String m_sBindPassword=null;
    private String m_sBindMethod="simple";
    private long m_lConnectTimeoutMS = 5000;
    private long m_lReadTimeoutMS = 30000;
    
    
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
        
        String sTemp = SysCtx.getSystemProperty("LDAPConnectTimeoutSeconds");
        if(sTemp!=null && Util.toInteger(sTemp)>0) m_lConnectTimeoutMS = Util.toInteger(sTemp)*1000;
        sTemp = SysCtx.getSystemProperty("LDAPReadTimeoutSeconds");
        if(sTemp!=null && Util.toInteger(sTemp)>0) m_lReadTimeoutMS = Util.toInteger(sTemp)*1000;
    }
    
    /**
     * Set up the hastable for binding to the ldap dir.
     * The username and password params specify the account to use when binding
     */
    private Hashtable<String, String> setupJNDIEnvironment(String szUserName, String szPassword)
    {
        Hashtable<String, String> htJNDI = new Hashtable<String, String>();
        htJNDI.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
        if(m_sLDAPHost!=null) htJNDI.put(Context.PROVIDER_URL, m_sLDAPHost);
        
        //plain text auth
        htJNDI.put(Context.SECURITY_AUTHENTICATION, m_sBindMethod);
        if(szUserName!=null)
        {
            htJNDI.put(Context.SECURITY_PRINCIPAL, szUserName);
            if(szPassword!=null) htJNDI.put(Context.SECURITY_CREDENTIALS, szPassword);
        }
        //stop a slow or dead LDAP server from tying up request threads forever
        if(m_lConnectTimeoutMS>0) htJNDI.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(m_lConnectTimeoutMS));
        if(m_lReadTimeoutMS>0) htJNDI.put("com.sun.jndi.ldap.read.timeout", String.valueOf(m_lReadTimeoutMS));
        
        return htJNDI;
    }
    
    
    
    
    /**
     * called each time a person attempts to log in
     */
    public LoginResult loginUser(String szUserName, String szPassword, String szAddress, String szUserAgent, String sAppURI)
    {
        LoginResult loginResult = new LoginResult();
        if(szUserName==null || szUserName.length()==0 || szUserName.length()>120) return loginResult;
        //an LDAP simple bind with an empty password is an anonymous bind and would "succeed"
        if(szPassword==null || szPassword.length()==0) return loginResult;
        
        DirContext ctx = null;
        NamingEnumeration<SearchResult> results = null;
        String sDN = null;
        boolean bTooMany = false;
        try
        {
            ctx = new InitialDirContext(setupJNDIEnvironment(m_sBindUserName, m_sBindPassword));
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setCountLimit(2); //we only need to know if there is more than one
            constraints.setReturningAttributes(new String[0]);
            
            String sSearchBase = "";
            String sLDAPSearchString = makeSearchString(szUserName);
            results = ctx.search(sSearchBase, sLDAPSearchString, constraints);
            try
            {
                if(results.hasMore())
                {
                    sDN = results.next().getNameInNamespace();
                    if(results.hasMore()) bTooMany = true;
                }
            }
            catch(SizeLimitExceededException slee)
            {
                //more matches than the count limit
                bTooMany = true;
            }
            //note: TOO_MANY_MATCHES has the same value as INVALID_USER
            if(bTooMany) loginResult.ReturnCode = LoginResult.LOGIN_RESULT_TOO_MANY_MATCHES;
            else if(sDN==null) loginResult.ReturnCode = LoginResult.LOGIN_RESULT_INVALID_USER;
        }
        catch(Exception e)
        {
            SysCtx.doError("Error logging in LDAP user '%s'", new String[]{e.toString()}, this);
            sDN = null;
        }
        finally
        {
            close(results);
            close(ctx);
        }
        
        //bind as the user after the search connection has been closed
        if(sDN!=null && !bTooMany) 
            return bindUser(sDN, szPassword, loginResult);
        return loginResult;
    }
    
    /**
     * Now try to bind the user we found to the LDAP dir.
     */
    private LoginResult bindUser(String sDN, String szPassword, LoginResult loginResult)
    {        
        DirContext ctx = null;
        try
        {            
            ctx = new InitialDirContext(setupJNDIEnvironment(sDN, szPassword));
            
            //if we get here, then the password etc must be OK
            loginResult.ReturnCode = LoginResult.LOGIN_RESULT_SUCCESS;
            X500Name nmUser = new X500Name(sDN, ",");
            nmUser.setSeperator("/");
            loginResult.UserName = nmUser.getCanonicalName();
            //Jake's bugfix follows... ;-)
            loginResult.FirstName = nmUser.getFirstName();
            loginResult.LastName = nmUser.getLastName();
            
        }
        catch(AuthenticationNotSupportedException wp)
        {
            loginResult.ReturnCode = LoginResult.LOGIN_RESULT_FAIL;
        }
        catch(AuthenticationException ae) //wrong password
        {
            loginResult.ReturnCode = LoginResult.LOGIN_RESULT_FAIL;
        }
        catch(Exception e)
        {
            SysCtx.doError("Error binding LDAP user '%s'", new String[]{e.toString()}, this);            
        }
        finally
        {
            close(ctx);
        }
        return loginResult;
    }
    
    
    
    private String makeSearchString(String szUserName)
    {
        String sUser = LDAPAuthenticator.escapeFilterValue(szUserName);
        String sSearch = "(|";
        sSearch += "(cn=" + sUser + ")";
        sSearch += "(alias=" + sUser + ")";
        sSearch += "(sn=" + sUser + ")";
        sSearch += "(givenname=" + sUser + ")";
        sSearch += "(uid=" + sUser + ")";
        
        return sSearch + ")";
    }
    
    /**
     * Called by the Puakma server to determine if the given session belongs to 
     * a group
     */
    public boolean isUserInGroup(SessionContext sessCtx, String szGroup, String sAppURI)
    {
        return isUserInGroupPrivate(sessCtx, szGroup);
    }
    
    
    /**
     * Walks the group and its nested groups breadth first on a single LDAP connection.
     * Each level of nesting is one search (batched), each group name is searched at most
     * once (case insensitive) so cyclic nesting cannot loop, and the walk is capped at
     * MAX_GROUPS_VISITED.
     */
    private boolean isUserInGroupPrivate(SessionContext sessCtx, String szGroup)
    {
        if(szGroup==null || szGroup.length()==0) return false;
        SysCtx.doDebug(pmaLog.DEBUGLEVEL_FULL, "isUserInGroupPrivate(%s->%s)", new String[]{sessCtx.getUserName(), szGroup}, this);
                
        X500Name nmUser = new X500Name(sessCtx.getUserName());
        nmUser.setSeperator(",");
        HashSet<String> visited = new HashSet<String>();
        ArrayList<String> level = new ArrayList<String>();
        visited.add(szGroup.toUpperCase());
        level.add(szGroup);
        
        DirContext ctx = null;
        try
        {
            ctx = new InitialDirContext(setupJNDIEnvironment(m_sBindUserName, m_sBindPassword));
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{"member"});
            
            while(!level.isEmpty())
            {
                ArrayList<String> members = new ArrayList<String>();
                for(int iStart=0; iStart<level.size(); iStart+=MAX_GROUPS_PER_SEARCH)
                {
                    List<String> batch = level.subList(iStart, Math.min(iStart+MAX_GROUPS_PER_SEARCH, level.size()));
                    getGroupMembers(ctx, constraints, batch, members);
                }
                
                ArrayList<String> nextLevel = new ArrayList<String>();
                for(int i=0; i<members.size(); i++)
                {
                    String sName = members.get(i);
                    X500Name nmResult = new X500Name(sName, ",");
                    if(nmUser.equals(nmResult)
                        || sName.equals("*")
                        || nmUser.matches(nmResult)) //check a partial match, eg: "*/Mkt/YourCo"
                    {
                        SysCtx.doDebug(pmaLog.DEBUGLEVEL_VERBOSE, "User '%s' is in group '%s'", new String[]{sessCtx.getUserName(), szGroup}, sessCtx);                                
                        return true;
                    }
                    //may be a nested group, search for it on the next pass
                    String sNested = nmResult.getAbbreviatedName();
                    if(sNested!=null && sNested.length()>0 && visited.add(sNested.toUpperCase()))
                    {
                        if(visited.size()>MAX_GROUPS_VISITED)
                        {
                            SysCtx.doError("isUserInGroup() LDAP group nesting of '%s' exceeds %s groups, stopping", new String[]{szGroup, String.valueOf(MAX_GROUPS_VISITED)}, this);
                            return false;
                        }
                        nextLevel.add(sNested);
                    }
                }
                level = nextLevel;
            }
        }
        catch(Exception e)
        {
            SysCtx.doError("Error recursing LDAP groups '%s'", new String[]{e.toString()}, this);            
        }
        finally
        {
            close(ctx);
        }
        
        return false;
    }
    
    /**
     * Search for all the groups named in one LDAP query and add every member value to the list
     */
    private void getGroupMembers(DirContext ctx, SearchControls constraints, List<String> groups, List<String> members) throws NamingException
    {
        StringBuilder sbFilter = new StringBuilder("(&(objectclass=groupofuniquenames)(|");
        for(int i=0; i<groups.size(); i++) sbFilter.append("(cn=").append(LDAPAuthenticator.escapeFilterValue(groups.get(i))).append(')');
        sbFilter.append("))");
        
        NamingEnumeration<SearchResult> results = null;
        try
        {
            results = ctx.search("", sbFilter.toString(), constraints);
            while(results.hasMore())
            {
                Attributes att = results.next().getAttributes();
                if(att==null) continue;
                NamingEnumeration<? extends Attribute> nme = att.getAll();
                try
                {
                    while(nme.hasMore())
                    {
                        Attribute lda = nme.next();
                        for(int i=0; i<lda.size(); i++)
                        {
                            Object obj = lda.get(i);
                            if(obj!=null) members.add(obj.toString());
                        }
                    }
                }
                finally
                {
                    close(nme);
                }
            }
        }
        finally
        {
            close(results);
        }
    }
    
    private static void close(Context ctx)
    {
        if(ctx==null) return;
        try{ ctx.close(); }catch(Exception e){}
    }
    
    private static void close(NamingEnumeration<?> ne)
    {
        if(ne==null) return;
        try{ ne.close(); }catch(Exception e){}
    }
    
}//end of class
