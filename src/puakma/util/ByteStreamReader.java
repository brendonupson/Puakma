/** ***************************************************************
ByteStreamReader.java
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


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * This class is designed to read a stream of bytes. It is a reader because we
 * want to be able to use readLine(). This will read a line up to the CRLF (\r\n)
 * and consume the CRLF. A bare LF is also accepted as a line terminator.
 * Bytes are mapped 1:1 to chars (ISO-8859-1) by the char read methods. 
 * @author  bupson
 */
public class ByteStreamReader extends Reader 
{    
    /** Longest line readLine() will accept before throwing an IOException */
    public static final int MAX_LINE_LENGTH = 65536;
    private static final int MAX_SCRATCH = 8192;
    
    private BufferedInputStream m_is=null;
    private Charset m_charset = null;
    private byte[] m_lineBuf = new byte[256]; //reused by readLine(), grows up to MAX_LINE_LENGTH
    private byte[] m_scratch = null; //reused by the char read methods
        
    public ByteStreamReader(InputStream is, int iBufferSize, String sCharSet) 
    {        
        initialise(is, iBufferSize, sCharSet);
    }
    
    public ByteStreamReader(InputStream is) 
    {
        initialise(is, -1, null);
    }
    
    /**
     * Do the heavy lifting to create the reader
     */
    private void initialise(InputStream is, int iBufferSize, String sCharSet)
    {        
        if(is==null) throw new IllegalArgumentException("InputStream cannot be null");
        if(iBufferSize>0) 
            m_is = new BufferedInputStream(is, iBufferSize);        
        else
            m_is = new BufferedInputStream(is);
        
        Charset cs = Charset.forName("ISO-8859-1");
        if(sCharSet!=null)
        {
            try{ cs = Charset.forName(sCharSet); }
            catch(Exception e){} //unsupported or illegal name, stay with ISO-8859-1
        }
        m_charset = cs;
    }
    
    public void close() throws java.io.IOException 
    {
        if(m_is!=null) m_is.close();
    }
    
    public void mark(int readlimit)
    {
        m_is.mark(readlimit);
    }
    
    public boolean markSupported()
    {
        return true;
    }
    
    public void reset() throws IOException
    {
        m_is.reset();
    }
    
    public boolean ready() throws IOException
    {
        return m_is.available()>0;
    }
    
    public long skip(long n) throws IOException
    {
        if(n<=0) return 0;
        return m_is.skip(n); //1 byte == 1 char
    }
    
    /**     
     * Read up to iLen bytes from the stream into the char array, one char per byte.
     * @return the number of chars read or -1 at the end of the stream
     */
    public int read(char[] cbuf, int iOffset, int iLen) throws java.io.IOException 
    {
        if(cbuf==null) throw new NullPointerException();
        if(iOffset<0 || iLen<0 || iLen>cbuf.length-iOffset) throw new IndexOutOfBoundsException();
        if(iLen==0) return 0;
        
        int iWanted = Math.min(iLen, MAX_SCRATCH);
        if(m_scratch==null || m_scratch.length<iWanted) m_scratch = new byte[iWanted];
        int iRead = m_is.read(m_scratch, 0, iWanted);
        for(int i=0; i<iRead; i++)
        {
            cbuf[iOffset+i] = (char)(m_scratch[i] & 0xFF);
        }
        return iRead;
    }
    
    /**
     *Read a block of bytes from the stream into the char buffer
     *
     */
    public int read(char[] cbuf) throws java.io.IOException
    {
        if(cbuf==null || cbuf.length==0) return 0;
        return read(cbuf, 0, cbuf.length);
    }
    
    /**
     *Read a block of bytes from the stream into the byte buffer
     *
     */
    public int read(byte[] buf) throws java.io.IOException
    {
        if(buf==null || buf.length==0) return 0;
                
        return m_is.read(buf);
    }
    
    
    
    /**
     * Read all the way up to a LF and consume it, removing a preceding CR if present. 
     * Usually used for reading http streams
     * @return The string up to the CRLF. Returns null if no data was read from the stream 
     * (end of stream). A final unterminated line is returned as is.
     * @throws IOException if the line is longer than MAX_LINE_LENGTH
     */
    public String readLine() throws java.io.IOException
    {
        byte[] buf = m_lineBuf;
        int iLen = 0;
        boolean bTerminated = false;
        int b;
        while((b=m_is.read()) >= 0)
        {
            if(b=='\n')
            {
                bTerminated = true;
                break;
            }
            if(iLen==buf.length)
            {
                if(iLen>=MAX_LINE_LENGTH) throw new IOException("Line too long (>"+MAX_LINE_LENGTH+" bytes)");
                buf = java.util.Arrays.copyOf(buf, Math.min(iLen*2, MAX_LINE_LENGTH));
                m_lineBuf = buf;
            }
            buf[iLen++] = (byte)b;
        }
        
        if(!bTerminated && iLen==0) return null; //end of stream
        if(bTerminated && iLen>0 && buf[iLen-1]=='\r') iLen--;
        
        String s = new String(buf, 0, iLen, m_charset);
        if(buf.length>4096) m_lineBuf = new byte[256]; //don't hold a large buffer for the life of the connection
        return s;
    }
    
    /**
     * Get a handle to the underlying stream
     */
    public InputStream getInputStream()
    {
        return m_is;
    }
    
}
