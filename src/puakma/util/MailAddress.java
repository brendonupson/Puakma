/** ***************************************************************
MailAddress.java
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

/*
 * MailAddress.java
 *
 * Created on 16 September 2003, 21:12
 */

package puakma.util;

import java.util.ArrayList;

/**
 * For parsing internet email addresses ""Brendon Upson" bupson@wnc.net.au"
 * @author  bupson
 */
public class MailAddress 
{
    private String m_sOriginalAddress="";
    private String m_sHost="";
    private String m_sUserName="";
    private String m_sUserDescription="";
    private boolean m_bValid=false;
    
    /** Creates a new instance of MailAddress */
    public MailAddress(String sAddress) 
    {
        setAddress(sAddress);        
    }
    
    
    public static void main(String[] args) 
    {
        /*
        String m1 = "\"Brendon Upson\" bupson@wnc.net.au";
        String m2 = "bupson@wnc.net.au";
        String m3 = "<bupson@wnc.net.au>";
        String m4 = "\"Brendon Upson\" <bupson@wnc.net.au>";
        String m5 = "Jake Howlett <jake@codestore.net>";
        String m6 = "\"<Brendon Upson>\" <bupson@wnc.net.au>";
         */
        
        //String sAll = "\"bupson@somewhere\" bupson@wnc.net.au, bupson@wnc.net.au,<bupson@wnc.net.au>,\"Brendon Upson\" <bupson@wnc.net.au>, Jake Howl <jakeh@xyz.net>, \"<Brendon Upson>\" <bupson@wnc.net.au>";
        
        String sAll = "\"bupson@somewhere\" bupson@wnc.net.au, \"somethin@here\" test,,bu@somwhere.net.au,@";
        MailAddress maReturn[] = parseMailAddresses(sAll, null);
        for(int i=0; i<maReturn.length; i++)
        {            
            System.out.println("Addr: ["+maReturn[i].getFullParsedEmailAddress()+"] valid=" + maReturn[i].isValidAddressSyntax());
            //System.out.println("SMTP: ["+maReturn[i].getSMTPEmailAddress()+"]");
        }
        
        
        System.out.println("-------------");
        MailAddress ma = new MailAddress("v@fred");
        System.out.println("User: ["+ma.getUserName()+"]");
        System.out.println("Host: ["+ma.getHost()+"]");
        System.out.println("Desc: ["+ma.getUserDescription()+"]");
        System.out.println("SMTP: ["+ma.getSMTPEmailAddress()+"] valid=" + ma.isValidAddressSyntax());
         
        System.out.println("done.");
    }
     
    
    /**
     * Pass a single string containing multiple addresses and have this method 
     * parse it and return multiple valid MailAddress objects. Any bad addresses 
     * will be dropped. The delimiter is ignored inside "quoted strings" and 
     * &lt;angle brackets&gt;, so "Smith, John" &lt;j@x.com&gt; is a single address.
     * Returns null if there is nothing to parse.
     */
    public static MailAddress[] parseMailAddresses(String sAddresses, String sDelimiter)
    {
        if(sAddresses==null || sAddresses.length()==0) return null;
        if(sDelimiter==null || sDelimiter.length()==0) sDelimiter = ",";
        
        ArrayList<MailAddress> arr = new ArrayList<MailAddress>();
        int iLen = sAddresses.length();
        int iStart = 0;
        boolean bInQuote = false;
        boolean bInAngle = false;
        for(int i=0; i<=iLen; )
        {
            boolean bSplit = false;
            int iSkip = 1;
            if(i==iLen) bSplit = true;
            else
            {
                char c = sAddresses.charAt(i);
                if(bInQuote)
                {
                    if(c=='\\') iSkip = 2; //escaped char inside quotes
                    else if(c=='\"') bInQuote = false;
                }
                else if(c=='\"') bInQuote = true;
                else if(c=='<') bInAngle = true;
                else if(c=='>') bInAngle = false;
                else if(!bInAngle && sAddresses.startsWith(sDelimiter, i))
                {
                    bSplit = true;
                    iSkip = sDelimiter.length();
                }
            }
            
            if(bSplit)
            {
                if(i>iStart)
                {
                    MailAddress ma = new MailAddress(sAddresses.substring(iStart, i));
                    if(ma.isValidAddressSyntax()) arr.add(ma);
                }
                iStart = i + iSkip;
            }
            i += iSkip;
        }
            
        return arr.toArray(new MailAddress[arr.size()]);
    }
    
    
    /**
     * Checks that the address is minimally well formed: a user name and a host 
     * separated by a single @, with no whitespace or control characters (which 
     * also prevents SMTP header/command injection). 
     * <ul>
     * <li>user: 1-64 chars of letters, digits and !#$%&amp;'*+-/=?^_`{|}~ with '.' allowed 
     * between characters (not leading, trailing or doubled). Quoted local parts are not supported.</li>
     * <li>host: 1-255 chars, dot separated labels of 1-63 letters, digits, '-' or '_' 
     * where a label may not start or end with '-'. A dotless host (eg "localhost") is accepted.</li>
     * <li>whole address is no longer than 254 chars</li>
     * </ul>
     * This DOES NOT verify that the account actually exists!
     */
    public boolean isValidAddressSyntax()
    {
        return m_bValid;
    }
    
    
    private static boolean checkSyntax(String sUser, String sHost)
    {
        int iUserLen = sUser.length();
        int iHostLen = sHost.length();
        if(iUserLen<1 || iUserLen>64 || iHostLen<1 || iHostLen>255 || iUserLen+iHostLen+1>254) return false;
        
        char cPrev = '.'; //start as '.' so a leading dot is rejected
        for(int i=0; i<iUserLen; i++)
        {
            char c = sUser.charAt(i);
            if(c=='.')
            {
                if(cPrev=='.') return false; //leading or doubled
            }
            else if(!isAtext(c)) return false;
            cPrev = c;
        }
        if(cPrev=='.') return false; //trailing
        
        int iLabelLen = 0;
        char cLast = '.';
        for(int i=0; i<iHostLen; i++)
        {
            char c = sHost.charAt(i);
            if(c=='.')
            {
                if(iLabelLen==0 || cLast=='-') return false; //empty label or ends with -
                iLabelLen = 0;
            }
            else
            {
                boolean bAlnum = (c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9');
                if(!bAlnum && c!='-' && c!='_') return false;
                if(c=='-' && iLabelLen==0) return false; //starts with -
                if(++iLabelLen>63) return false;
            }
            cLast = c;
        }
        return iLabelLen>0 && cLast!='-'; //no trailing dot / empty label
    }
    
    
    private static boolean isAtext(char c)
    {
        if((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9')) return true;
        return c<128 && "!#$%&'*+-/=?^_`{|}~".indexOf(c)>=0;
    }
    
    
    /**
     * Set the address. Understands: user@host, &lt;user@host&gt;, Name &lt;user@host&gt;, 
     * "Name" &lt;user@host&gt;, Name&lt;user@host&gt; and "Name" user@host
     */
    public void setAddress(String sAddress)
    {
        m_sOriginalAddress="";
        m_sHost="";
        m_sUserName="";
        m_sUserDescription="";
        m_bValid=false;
        
        sAddress = Util.trimChar(sAddress, new char[]{' ', '\t', '\r', '\n'});
        
        if(sAddress==null || sAddress.length()==0) return;
        m_sOriginalAddress = sAddress;
        m_sUserName = sAddress;
        
        int iLt = sAddress.lastIndexOf('<');
        int iGt = (iLt>=0) ? sAddress.indexOf('>', iLt) : -1;
        if(iGt>iLt) //Name <user@host>, ignore anything after the '>'
        {
            m_sUserDescription = sAddress.substring(0, iLt).trim();
            m_sUserName = sAddress.substring(iLt+1, iGt).trim();
        }
        else
        {
            int iPos = sAddress.lastIndexOf(' ');        
            if(iPos>0) //strip email bits before "user@y.com"
            {
                m_sUserDescription = sAddress.substring(0, iPos);
                m_sUserName = sAddress.substring(iPos+1, sAddress.length());
            }
        }
        
        int iAtPos = m_sUserName.lastIndexOf('@');
        if(iAtPos>0)
        {
            m_sHost = m_sUserName.substring(iAtPos+1, m_sUserName.length());
            m_sUserName = m_sUserName.substring(0, iAtPos);                
        }
         
        m_sHost = Util.trimChars(m_sHost, ">");
        m_sUserName = Util.trimChars(m_sUserName, "<");
        m_sUserDescription = Util.trimChars(m_sUserDescription, "\"");
        m_bValid = !hasControlChars(sAddress) && checkSyntax(m_sUserName, m_sHost);
    }


    /** CR/LF etc anywhere (even in the display name) could be used to inject SMTP commands or headers */
    private static boolean hasControlChars(String s)
    {
        for(int i=0; i<s.length(); i++)
        {
            char c = s.charAt(i);
            if(c<32 && c!='\t' || c==127) return true;
        }
        return false;
    }
    
    
    /**
     * Returns the user's mail server
     */
    public String getHost()
    {
        return m_sHost;
    }
    
    /**
     * Returns the user's familiar name.
     */
    public String getUserDescription()
    {
        return m_sUserDescription;
    }
    
    
    /**
     * Returns the user's account name.
     */
    public String getUserName()
    {
        return m_sUserName;
    }
    
    
    /**
     * Returns the original details the object was constructed with
     */
    public String getFullOriginalAddress()
    {
        return this.m_sOriginalAddress;
    }
    
    /**
     * Returns the SMTP ready address "<user@domain.com>"
     */
    public String getSMTPEmailAddress()
    {
        return "<"+m_sUserName+"@"+m_sHost+">";
    }
    
    /**
     * Returns the basic address "user@domain.com"
     */
    public String getBasicEmailAddress()
    {
        return m_sUserName+"@"+m_sHost;
    }
    
    /**
     * Returns the standards compliant address ""smith, john" <user@domain.com>"
     */
    public String getFullParsedEmailAddress()
    {
        String sDesc="";
        if(m_sUserDescription.length()>0) sDesc = "\"" + m_sUserDescription + "\" ";
        return sDesc + "<"+m_sUserName+"@"+m_sHost+">";
    }
    
}
