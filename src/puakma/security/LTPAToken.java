/** ***************************************************************
LTPAToken.java
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

package puakma.security; 

import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import puakma.util.Util;

/**
 *
 * @author  Brendon Upson
 *
 * CookieName = "LtpaToken"
 * CookieValue = Base64(LtpaToken)
 * CookieDomain = Subdomain of server (i.e. "raleigh.ibm.com")
 * LtpaToken = TokenHeader[4 BYTES] +
        HexEncoded((DWORD)TokenCreationDate)[8 BYTES] +
        HexEncoded((DWORD)TokenExpirationDate)[8 BYTES] +
        Canonicalized username [variable] +
        SHA1(TokenHeader+TokenCreation+TokenExpiration+Username+Secret)[20 BYTES]
 * TokenHeader = First byte reserved, three bytes for secret sequence usage
 * TokenCreationDate = A number representing the time and date of the token creation. (TokenCreationDate is the number of seconds to elapse since midnight (00:00:00), January 1, 1970) in GMT
 * TokenExpirationDate = A number representing the time and date of the token expiration. (TokenExpirationDate is the number of seconds to elapse since midnight (00:00:00), January 1, 1970) in GMT 
 *
 */
public class LTPAToken
{
    private Date m_dtCreation = new Date();
    private Date m_dtExpiry;
    private String m_sUserName;
    private byte m_bufHeader[]=new byte[]{0x00,0x01,0x02,0x03};
    private static byte STANDARD_PREAMBLE[]=new byte[]{0x00,0x01,0x02,0x03};  
    public final static String LTPACOOKIENAME="LtpaToken";
    private String m_sCharEncoding="CP850";
    
    
    
    
    /** For creating new LTPATokens */
    public LTPAToken() 
    {
    }
    
    /**
     * For decrypting existing tokens. Both parameters in base64 format.
     */
    public LTPAToken(String sBase64EncodedToken, String sBase64Secret, String sCharEncoding) throws Exception
    {
        if(sCharEncoding!=null) m_sCharEncoding = sCharEncoding;
        byte bufToken[] = puakma.util.Util.base64Decode(sBase64EncodedToken);
        byte bufSecret[] = puakma.util.Util.base64Decode(sBase64Secret);
        setupToken(bufToken, bufSecret);
    }
    
    /**
     * For decrypting existing tokens
     */
    public LTPAToken(String sBase64EncodedToken, byte bufSecret[], String sCharEncoding) throws Exception
    {
        if(sCharEncoding!=null) m_sCharEncoding = sCharEncoding;
        byte bufToken[] = puakma.util.Util.base64Decode(sBase64EncodedToken);        
        setupToken(bufToken, bufSecret);
    }
    
    /**
     * For decrypting existing tokens
     */
    public LTPAToken(byte bufToken[], byte bufSecret[], String sCharEncoding) throws Exception
    {
        if(sCharEncoding!=null) m_sCharEncoding = sCharEncoding;
        setupToken(bufToken, bufSecret);
    }
    
    /**
     * For decrypting existing tokens
     */
    public LTPAToken(String sBase64EncodedToken, String sCharEncoding)
    {
        if(sCharEncoding!=null) m_sCharEncoding = sCharEncoding;
        byte bufToken[] = puakma.util.Util.base64Decode(sBase64EncodedToken);
        //if(!decodeToken(bufToken, null)) throw new Exception("Invalid Ltpa Token");
        //decode the token without the secret - just looking ;-)
        decodeToken(bufToken, null);
    }
    
    /**
     * Internal method to decode the toaken and set internal data items such as username,
     * token creation date etc
     */
    private void setupToken(byte bufToken[], byte bufSecret[]) throws Exception
    {        
        if(!decodeToken(bufToken, bufSecret)) throw new Exception("Invalid Ltpa Token");
    }
 
    
    /**
     * Return the username from the token
     */
    public String getUserName()
    {
        return m_sUserName;
    }
    
    /**
     * Set the username to be added to a new token generated by this class
     */
    public void setUserName(String sNewName)
    {
        m_sUserName = sNewName;
    }
    
    /**
     * Defines the character set used to encode the token's name into a byte array.
     * Default is CP850
     */
    public void setCharacterEncoding(String sNewEncoding)
    {
        m_sCharEncoding = sNewEncoding;
    }
    
    /**
     * Determine the character set that will be used to encode the token's name into a byte array
     */
    public String getCharacterEncoding()
    {
        return m_sCharEncoding;
    }
    
    /**
     * Determines if the token has expired
     */
    public boolean hasExpired()
    {
        if(System.currentTimeMillis()>m_dtExpiry.getTime()) return true;
        return false;
    }
    
    /**
     * Return the expiry date of the token
     */
    public Date getExpiryDate()
    {
        return m_dtExpiry;
    }
      
    /**
     * Set the expiry date for a new token to be created by this class
     */
    public void setExpiryDate(Date dtNewDate)
    {
        m_dtExpiry = dtNewDate;
    }
    
    
    /**
     * Return the date the token was created
     */
    public Date getCreationDate()
    {
        return m_dtCreation;
    }
        
    /**
     * Set the creation date for new tokens created by this class
     */
    public void setCreationDate(Date dtNewDate)
    {
        m_dtCreation = dtNewDate;
    }
    
    /**
     * Rerturns the token as an array of bytes, prior to hashing
     */
    public byte[] getToken(byte bufSecret[])
    {        
        if(bufSecret==null) bufSecret = "secret".getBytes();            
        int iSecretLen= bufSecret.length;
        
        byte bufCreation[] = toHexDate(m_dtCreation.getTime());
        byte bufExpiry[] = toHexDate(m_dtExpiry.getTime());
        
        byte bufHeader[] = new byte[20];
        System.arraycopy(m_bufHeader, 0, bufHeader, 0, 4);
        System.arraycopy(bufCreation, 0, bufHeader, 4, 8);
        System.arraycopy(bufExpiry, 0, bufHeader, 12, 8);        
        
        
        byte bufTrailer[] = new byte[20];
        
        
        byte bufName[] = m_sUserName.getBytes();   
        try{ bufName = m_sUserName.getBytes(m_sCharEncoding); }catch(Exception t){}
        
        /*System.out.println("origname="+m_sUserName.length() + " buflen="+bufName.length);
        for(int i=0; i<bufName.length; i++)
        {
            System.out.print(" " + Integer.toHexString(bufName[i]));
        }*/
        
        byte bufToken[] = new byte[40+bufName.length];
        System.arraycopy(bufHeader, 0, bufToken, 0, 20);
        
        System.arraycopy(bufName, 0, bufToken, 20, bufName.length);
        int iTrailerStart = 20+bufName.length;
        
        //make the buffer for hashing
        //SHA1(TokenHeader+TokenCreation+TokenExpiration+Username+Secret)[20 BYTES]
        int iHashSize = (bufToken.length-20)+iSecretLen;
        byte bufHashHeader[]=new byte[iHashSize];        
        System.arraycopy(bufToken, 0, bufHashHeader, 0, iHashSize-iSecretLen);
        System.arraycopy(bufSecret, 0, bufHashHeader, iHashSize-iSecretLen, iSecretLen);
        bufTrailer = hashBytes(bufHashHeader);
                                        
        System.arraycopy(bufTrailer, 0, bufToken, iTrailerStart, 20);
        
        return bufToken;
    }
    
    /**
     * Preforms a SHA-1 hash of the buf[] parameter
     */
    public static byte[] hashBytes(byte buf[])
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-1") ;         
            md.update(buf);            
            return md.digest();
        }
        catch(Exception e)
        {
            System.out.println(e.toString());
        }
        
        return null;
    }
    
    
    /**
     * Determines if one array of bytes is the same as another
     */
    public static boolean arrayMatches(byte buf1[], byte buf2[])
    {
        if(buf1==null && buf2==null) return true;
        if((buf1==null && buf2!=null) || (buf1!=null && buf2==null)) return false;
        
        if(buf1.length!=buf2.length) return false;
        
        for(int i=0; i<buf1.length; i++)
        {
            if(buf1[i]!=buf2[i]) return false;
        }
        
        return true;
    }
    
    
    /**
     * Create a string suitable for adding to a Set-Cookie: http header.
     * Caution: setting an expiry date on the cookie will cause it to stay on the browser's disk between browser restarts.
     * It will also set the expiry date embedded in the cookie to the same date
     *
     */
    public String getAsCookie(byte bufSecret[], String sPath, String sDomain, boolean bIsSecure, Date dtCookieExpiry)
    {
        StringBuilder sbReturn = new StringBuilder(256);
        if(dtCookieExpiry!=null) setExpiryDate(dtCookieExpiry); //override as there's no point storing on disk if the token expires before the cookie
        sbReturn.append("LtpaToken="+getEncodedToken(bufSecret));
        
        if(sPath!=null) sbReturn.append("; path="+sPath);
        if(sDomain!=null) sbReturn.append("; domain="+sDomain);
        
        // Setting an expiry date on the cookie will cause it to stay on the browser's disk between browser restarts.
        if(dtCookieExpiry!=null)
        {        
            sbReturn.append("; expires="+ Util.toGMTString(dtCookieExpiry));
        }
        
        if(bIsSecure) sbReturn.append("; secure");        
        return sbReturn.toString();
    }
    
    /**
     * Get the full base64 encoded token
     */
    public String getEncodedToken(byte bufSecret[])
    {       
        return puakma.util.Util.base64Encode(getToken(bufSecret));
    }
    
    /**
     * Pull apart an ltpa token. returns true if the decode was successful. Pass null
     * for the secret to avoid checking the the token for validity.
     */
    public boolean decodeToken(byte bufToken[], byte bufSecret[])
    {
        if(bufToken==null || bufToken.length<41) return false;
        
        int iSecretLen = 0;
        if(bufSecret!=null) iSecretLen = bufSecret.length;
        
        byte bufTmpPreamble[]=new byte[4];
        System.arraycopy(bufToken, 0, bufTmpPreamble, 0, 4);
        if(!arrayMatches(bufTmpPreamble, STANDARD_PREAMBLE)) return false;
        
        //SHA1(TokenHeader+TokenCreation+TokenExpiration+Username+Secret)[20 BYTES]
        int iHashSize = (bufToken.length-20)+iSecretLen;
        byte bufHashHeader[]=new byte[iHashSize];        
        System.arraycopy(bufToken, 0, bufHashHeader, 0, iHashSize-iSecretLen);
        if(bufSecret!=null) System.arraycopy(bufSecret, 0, bufHashHeader, iHashSize-iSecretLen, iSecretLen);
        
        byte bufTmpTrailer[]=new byte[20];
        System.arraycopy(bufToken, bufToken.length-20, bufTmpTrailer, 0, 20);
        
        byte bufName[] = new byte[bufToken.length-40];
        System.arraycopy(bufToken, 20, bufName, 0, bufName.length);
        
        /*
        creation=3C D2 9D 70 = 1020435824
        expiry=3C D2 A4 78
         */
        
        byte bufCreation[] = new byte[8];
        System.arraycopy(bufToken, 4, bufCreation, 0, 8);               
        long lCreation = toLong(bufCreation);        
        
        byte bufExpiry[] = new byte[8];
        System.arraycopy(bufToken, 12, bufExpiry, 0, 8);
        long lExpiry = toLong(bufExpiry);        
                            
        byte bufTrailer[] = hashBytes(bufHashHeader);
        byte bufCompareTrailer[] = new byte[20];
        System.arraycopy(bufTrailer, 0, bufCompareTrailer, 0, 20);
        //test the SHA1 of the header against the trailer.
        
        if(bufSecret!=null && !arrayMatches(bufCompareTrailer, bufTmpTrailer)) return false;                               
        
        //it all checks out, copy the temp vars to the class members
        m_sUserName = new String(bufName);
        try{ m_sUserName = new String(bufName, m_sCharEncoding); }catch(Exception t){}                
        m_dtExpiry = makeDateFromLong(lExpiry);        
        m_dtCreation = makeDateFromLong(lCreation);
        
        return true;
    }
    
     
    /**
     * Converts a long into a java.util.Date
     */
    public Date makeDateFromLong(long lSeconds)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));        
        cal.setTimeInMillis(lSeconds*1000);
        return cal.getTime();
    }
    
    
    /**
     * Converts a byte array into a long. The byte array is typically a date value extracted 
     * from the raw token bytes.
     */
    public static final long toLong (byte[] byteArray)
    {
        String s = new String(byteArray);
        try
        {
            return Long.parseLong(s, 16);
        }
        catch(Exception e)
        {
            return 0;
        }
    }
    
    /**
     * Convert a time in milliseconds since 1/1/1970 to hex seconds since
     */
    public static byte[] toHexDate(long lTimeMS)
    {        
        String s = Long.toHexString(lTimeMS/1000);        
        return s.toUpperCase().getBytes();
    }
       
}