/** ***************************************************************
TripleDESCoder.java
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

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import puakma.coder.CoderB64;
 
public class TripleDESCoder
{
    static public class EncryptionException extends Exception
    {       
		private static final long serialVersionUID = 1L;

		private EncryptionException(String text, Exception chain)
        {
            super(text, chain);
        }
    }
    
    private static final String algoName = "DESede";		// triple-DES
    private SecretKey secretKey;
    private final byte[] tripleDesKeyData;
    private Cipher cipher;
    private CoderB64 m_b64Coder = new CoderB64();
    
    private Cipher getCipher()
    {
        return this.cipher;
    }
    
    private Cipher createCipher(String password) throws EncryptionException
    {
        try
        {
            secretKey = new SecretKeySpec(tripleDesKeyData, algoName);            
            // /ECB/PKCS5Padding should be the default block mode and padding but just in case
            return Cipher.getInstance(algoName + "/ECB/PKCS5Padding");
        }
        catch (Exception e)
        {
            throw new EncryptionException("Cannot create Cipher", e);
        }
    }
    
    public synchronized byte[] encodeBytes( byte[] bufOriginal ) throws EncryptionException
    {
        try
        {
            getCipher().init( Cipher.ENCRYPT_MODE, secretKey );
            //byte[] utf8 = originalText.getBytes("UTF8");
            byte[] enc = getCipher().doFinal(bufOriginal);
            return enc;
        }
        catch (Exception e)
        {
            throw new EncryptionException("Problem encrypting", e);
        }
        
    }
    
    public synchronized String encodeString( String originalText ) throws EncryptionException
    {
        try
        {
            getCipher().init( Cipher.ENCRYPT_MODE, secretKey );
            byte[] utf8 = originalText.getBytes("UTF8");
            byte[] enc = getCipher().doFinal(utf8);
            return base64Encode(enc);
        }
        catch (Exception e)
        {
            throw new EncryptionException("Problem encrypting", e);
        }
        
    }
    
    private String base64Encode(byte buf[])
    {
        return m_b64Coder.encode(buf);
    }
    
    private byte[] base64Decode(String s)
    {
        return m_b64Coder.decode(s.getBytes());
    }
    
    public synchronized String decodeString(String encryptedText) throws EncryptionException
    {
        try
        {
            getCipher().init(Cipher.DECRYPT_MODE, secretKey);
            byte[] dec = base64Decode(encryptedText);
            byte[] utf8 = getCipher().doFinal(dec);
            return new String(utf8, "UTF8");
        }
        catch (Exception e)
        {
            throw new EncryptionException("Problem decrypting", e);
        }
    }
    
    public synchronized byte[] decodeBytes(byte[] bufEncrypted) throws EncryptionException
    {
        try
        {
            getCipher().init(Cipher.DECRYPT_MODE, secretKey);
            byte[] bufReturn = getCipher().doFinal(bufEncrypted);
            return bufReturn;
        }
        catch (Exception e)
        {
            throw new EncryptionException("Problem decrypting", e);
        }
    }
    
    public TripleDESCoder(String sBase64Key) throws EncryptionException
    {
        try
        {
            tripleDesKeyData = base64Decode(sBase64Key);//"8A/Tl316CXblNXF1p/fLi+jnC4kBbO5G");//"A_key_24_characters_long".getBytes("ASCII");
            this.cipher=this.createCipher(null);
        }
        catch (EncryptionException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new EncryptionException("Problem creating DESencoder", e);
        }
    }//coder
    
    
    /*
    public static void main(String[] args)
    {
        try
        {
            puakma.coder.CoderB64 coder = new puakma.coder.CoderB64();
            // Make sure SUN are a valid provider
            Security.addProvider(new com.sun.crypto.provider.SunJCE());
            
            //TripleDESCoder dataStringEncryptAgent = new TripleDESCoder("8A/Tl316CXblNXF1p/fLi+jnC4kBbO5G");
            TripleDESCoder dataStringEncryptAgent = new TripleDESCoder("L9G3m17nYEf75YQTpDAXcnFVKgq0J4q7");
            
            // Get the data string to encrypt from the command line
            //String ltpaString = "JekqYSFkGazUuh0Tj5q7IRX8wNe44STGCxuD9EMD8Vh1AQ9qoxr/RLISYCw1W2PCRp4BSjtSxSu/v1KK+J0uQLaib+4f7eNsWdhZjHvUB0A0oCOTKDuYe0Q5hfYb2gz0GArGKhVOVFDnb34UWZS2SiMhFvasAgMFMXBa4iQxfcvplM9hwN4xI1Y4bvg+GiGzdmNUIMBxju5eW+iKH0QP1zgu0ND0/kJnlkPDfwAzwnFWXEezV2gQqmhGRwHYclCMHcKwz5iU2mDzpqOt4e1o1wV7u4/7wPD19+axFJkMBlfX7lMZ+xI971Z1eBlv/t6vZB5D8bKBChY/Rj5MIG9BvEIt90KztvzI";
            String ltpaString = "cfpc5Q0TUokFSdqYsEMQnhJTgy2RRh/jg0C+MXYY19oVBxYgl0buB2jF08g3B2iZTMOmNDXcMxzTN3Y94wtG4XIlk3WKmVo+90uQ0pYriH0UHHybx4w9nGGjjrYlZSYoptz0hZXBpEH6K2ILcBmW9JxahT8icQ+gMZbYR1MTHacT8vDFBPS+NSWLoqNg04Xs2o+aKeRWpNfvqCVYjgTzwt6OT3snYuQCjLsLTrIsWHhRgCTexRhlRPl52kJpl9W+mUhY6wrKyYDmgD35N4z7bH6g4l2EAXUtvmpw/ifkDlinGvxmd5/YqayVZ7P0g/b33mfAeW9DB2zBM3eG8DM5dA==";
            
            System.out.println("Ltpa token ......[" + ltpaString + "]");
            //System.out.println("["+ new String(coder.decode(ltpaString.getBytes())) + "]");
            
            String recoveredDataString = dataStringEncryptAgent.DecodeString(ltpaString);
            System.out.println("Recovered  >>....[" + recoveredDataString + "]");
            
            
            String sPreamble = "u:user\\:";
            int iPC = recoveredDataString.indexOf('%');
            int iLastPC = recoveredDataString.lastIndexOf('%');
            int iSlash = recoveredDataString.indexOf('/');
            
            if(iPC>0)
            {
                //rsa key is 128 bytes
                String sRSAData = "HzXoE+TgFfK4TYumP+oIpYEXVPs4AORTO7e6QvwX8dDmaML+giEGV3Hut1WqY9e7N1wQD8gpidR2sFzZqP1xEoioEzHzmrsLjLFneLITUlchILhZ1Oe8bfQGh/bS69PzrkqruMkt/QkxJa+07fGn53eeJntZdXvVqswUzGMAhCkBAAGgg+7rgf+dYH6mOCA2+HACchWytPIKQJHuQp3ButSgAmpOf2SHtwtmcREU1ycQal/4HEY6yUhh1fFugLvVT9tczfMxmAVjIC6iBj0mQWB/vyBRk5w926AzUzfbN4XHUNO6WV7OXY566AbZG/+QhS8N9asFDLTZXPuyXyIrsc8lDw==";
                byte bufRSAData[] = coder.decode(sRSAData.getBytes());
                
                String sFirstPart = recoveredDataString.substring(0, iLastPC+1);
                String sNumber = recoveredDataString.substring(iPC+1, iLastPC);
                String sTrailer = recoveredDataString.substring(iLastPC+1);
                String sRealm = recoveredDataString.substring(sPreamble.length(), iSlash);
                String sUser = recoveredDataString.substring(iSlash+1, iPC);
                byte bufTrailer[] = coder.decode(sTrailer.getBytes());
                

                byte bufSigned[] = null;
                
                byte bufFirstPart[] = sFirstPart.getBytes();
                byte bufToHash[] = puakma.util.Util.appendBytes(bufFirstPart, bufRSAData);
                byte bufHashed[] = puakma.util.Util.hashBytes(bufToHash);
                
                System.out.println("RSAData=["+sRSAData+"]");
                System.out.println("trailer=["+sTrailer+"]");
                System.out.println("trailerlen=" + bufTrailer.length);
                System.out.println("rsadata len=" + bufRSAData.length);
                
                if(bufHashed!=null) System.out.println("hashed =["+coder.encode(bufHashed)+"] hashlen="+bufHashed.length);
                
                //int iPubKeyLen = bufPublicKey.length*8;
                //int iPrivKeyLen = bufPrivateKey.length*8;
                //System.out.println("trailer :["+ sbase64Data +"] len="+bufTrailer.length);
                //System.out.println("pubkey len="+iPubKeyLen+"bit");
                //System.out.println("privkey len="+iPrivKeyLen+"bit");
              
                long lTime = Long.parseLong(sNumber);
                java.util.Date dt = new java.util.Date();
                dt.setTime(lTime);
                System.out.println("realm=["+sRealm+"]");
                System.out.println("username=["+sUser+"]");
                System.out.println("expires=["+dt+"]");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    */
    
}//class
