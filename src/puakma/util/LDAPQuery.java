/** ***************************************************************
LDAPQuery.java
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

package puakma.util;

import java.util.ArrayList;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;


public class LDAPQuery 
{
    private static String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private String m_sLDAPHost = "ldap://localhost:389";
    private String m_sBindUserName=null;
    private String m_sBindPassword=null;
    private String m_sBindMethod="simple";
    private String m_sLDAPSearchBase = "";
    //private SystemContext m_pSystem; //caution, may be null depending on it this is invoked
    
    
    public LDAPQuery(String sURL, String sSearchBase, String sBindMethod, String sBindUserName, String sBindPassword) 
    {
        init(sURL, sSearchBase, sBindMethod, sBindUserName, sBindPassword);
    }
    
    /** Creates a new instance of LDAPQuery */
    /*public LDAPQuery(SystemContext pSystem, String sURL, String sSearchBase, String sBindMethod, String sBindUserName, String sBindPassword) 
    {
        init(pSystem, sURL, sSearchBase, sBindMethod, sBindUserName, sBindPassword);
    }*/
    
    private void init(String sURL, String sSearchBase, String sBindMethod, String sBindUserName, String sBindPassword)
    {
        //if(pSystem!=null) m_pSystem = pSystem;
        if(sURL!=null) m_sLDAPHost = sURL;
        if(sSearchBase!=null) m_sLDAPSearchBase = sSearchBase;
        if(sBindMethod!=null) m_sBindMethod = sBindMethod;
        m_sBindUserName = sBindUserName;
        m_sBindPassword = sBindPassword;
    }
    
    public static void main(String args[])
    {
        String sURL = "ldap://bnedmc01:389"; //636
        String sBase = "DC=desqld,DC=internal";                
        String sUser="CN=Brendon Upson,OU=Administrators,DC=desqld,DC=internal";
        String sPW ="xxx";
        LDAPQuery ldap = new LDAPQuery(sURL, sBase, null, sUser, sPW);
        String s[] = ldap.makeChoicesArray("(objectClass=person)", new String[]{"sAMAccountName", "distinguishedName"}, ""); //"distinguishedName", "sAMAccountName"
        for(int i=0; i<s.length; i++)
        {
            System.out.println(i+": ["+ s[i] + "]");
        }
            
        /*ArrayList arr = ldap.executeQuery("(objectClass=person)", new String[]{"sAMAccountName","distinguishedName"});
        for(int i=0; i<arr.size(); i++)
        {
            System.out.println(i+": ----------------------------");
            Attributes attrs = (Attributes)arr.get(i);
            NamingEnumeration ne = attrs.getAll();
            while(ne.hasMoreElements())
            {
                Attribute att = (Attribute)ne.nextElement();
                String sID = att.getID();
                StringBuilder sbValues = new StringBuilder(200);
                for(int k=0; k<att.size(); k++)
                {
                    try
                    {
                        String sValue = (String)att.get(k);
                        if(sbValues.length()>0) sbValues.append(",");
                        sbValues.append(sValue);
                    }catch(Exception f){}
                }
                System.out.println(sID+": ["+sbValues.toString()+"]");
            }
        }
         */
        
        System.out.println("\r\n\r\nDONE.");
    }
    
    /**
     * Set up the hastable for binding to the ldap dir.
     * The username and password params specify the account to use when binding
     */
    private Hashtable setupJNDIEnvironment(String sUserName, String sPassword)
    {
        Hashtable htJNDI = new Hashtable();
        htJNDI.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
        if(m_sLDAPHost!=null) htJNDI.put(Context.PROVIDER_URL, m_sLDAPHost);
        
        //plain text auth
        htJNDI.put(Context.SECURITY_AUTHENTICATION, m_sBindMethod);
        if(m_sLDAPHost!=null && m_sLDAPHost.toLowerCase().startsWith("ldaps:"))
        {
            htJNDI.put(Context.SECURITY_PROTOCOL, "ssl");               
            htJNDI.put("java.naming.ldap.factory.socket", "puakma.util.RelaxedSSLSocketFactory");
        }
        if(sUserName!=null)
        {
            htJNDI.put(Context.SECURITY_PRINCIPAL, sUserName);
            htJNDI.put(Context.SECURITY_CREDENTIALS, sPassword);
        }
        
        return htJNDI;
    }
    
    /**
     * Return a list of LDAP results and format ready for a call to ActionDocument.setItemChoices(...)
     */
    public String[] makeChoicesArray(String sQuery, String sAttributesToReturn[], String sSeparator)
    {  
        if(sSeparator==null) sSeparator = " ";
        StringBuilder sbValues = new StringBuilder(200);
        ArrayList arrReturn = new ArrayList(); 
        ArrayList arr = executeQuery(sQuery, sAttributesToReturn);
        if(arr.size()>0)
        {
            if(sAttributesToReturn==null || sAttributesToReturn.length==0)
            {
                //determine the setup of attributes
                Attributes attrs = (Attributes)arr.get(0);
                sAttributesToReturn = new String[attrs.size()];
                NamingEnumeration ne = attrs.getAll();
                int iPos=0;
                while(ne.hasMoreElements())
                {
                    Attribute att = (Attribute)ne.nextElement();
                    String sID = att.getID();
                    sAttributesToReturn[iPos] = sID;
                    iPos++;
                }
            }
        }
        else //no results
            return new String[0];
        
        int iNumAttributes = sAttributesToReturn.length;
        
        for(int i=0; i<arr.size(); i++)
        {            
            Attributes attrs = (Attributes)arr.get(i);
            if(iNumAttributes>1)
            {
                for(int k=0; k<iNumAttributes; k++)
                {
                    String sAttName = sAttributesToReturn[k];
                    Attribute att = (Attribute)attrs.get(sAttName);
                    if(att!=null)
                    {
                        if(k==iNumAttributes-1) sbValues.append("|");

                        for(int m=0; m<att.size(); m++)
                        {
                            try
                            {
                                String sValue = (String)att.get(m);
                                if(sbValues.length()>0) sbValues.append(sSeparator);
                                sbValues.append(sValue);
                            }catch(Exception f){}
                        }
                    }//att!=null
                }//for
            }
            else
            {
                //only one attribute
                if(sAttributesToReturn.length==1)
                {
                    String sAttName = sAttributesToReturn[0];
                    Attribute att = (Attribute)attrs.get(sAttName);
                    if(att!=null)
                    {
                        for(int m=0; m<att.size(); m++)
                        {
                            try
                            {
                                String sValue = (String)att.get(m);
                                if(sbValues.length()>0) sbValues.append(sSeparator);
                                sbValues.append(sValue);
                            }catch(Exception f){}
                        }
                    }//att!=null
                }
            }
            
            /*NamingEnumeration ne = attrs.getAll();
            while(ne.hasMoreElements())
            {
                Attribute att = (Attribute)ne.nextElement();
                String sID = att.getID();                
                for(int k=0; k<att.size(); k++)
                {
                    try
                    {
                        String sValue = (String)att.get(k);
                        if(sbValues.length()>0) sbValues.append(" ");
                        sbValues.append(sValue);
                    }catch(Exception f){}
                }
                //System.out.println(sID+": ["+sbValues.toString()+"]");
                
            }*/
            arrReturn.add(sbValues.toString());
            sbValues.delete(0, sbValues.length());
        }
        
        return Util.objectArrayToStringArray(arrReturn.toArray());
    }
    
    /**
     *
     * @return an ArrayList of Attributes objects, one for each result
     */
    public ArrayList executeQuery(String sQuery, String sAttributesToReturn[])
    {               
        ArrayList arrReturn = new ArrayList();        
        try
        {
            Hashtable htJNDI = setupJNDIEnvironment(m_sBindUserName, m_sBindPassword);
            DirContext ctx = new InitialDirContext(htJNDI);            
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);            
            constraints.setReturningAttributes(sAttributesToReturn);
                     
            NamingEnumeration results = ctx.search(m_sLDAPSearchBase, sQuery, constraints);
            boolean bMore = (results!=null);
            while(bMore)
            {   
                /*try{
                    bMore = results.hasMore();
                }catch(javax.naming.PartialResultException pre){}*/
                SearchResult sr = (SearchResult)results.next();
                if(sr==null) bMore=false;
                if(bMore)
                {                                    
                    Attributes attrs = sr.getAttributes();                    
                    arrReturn.add(attrs);
                }
                
            }//if has results
            
        }
        catch(Exception e)
        {
            //Exceptions are thrown at the end of the result set
            //We will get some crud exceptiosn thrown which are by and large unimportant
            //so we'll ignore them
            
            /*
            if(m_pSystem!=null) 
                m_pSystem.doError("executeQuery() '%s' '%s'", new String[]{e.toString(), sQuery}, this);            
            else
                e.printStackTrace();
             */
        }
        
        return arrReturn;
    }
}//class
