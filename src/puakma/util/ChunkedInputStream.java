/* ***************************************************************
ChunkedInputStream.java
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
package puakma.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes an http "Transfer-Encoding: chunked" body (RFC 9112 section 7.1). Reads exactly the chunked
 * body from the underlying stream, including the final zero length chunk and any trailer fields, so
 * the next request on a keep-alive connection can be read afterwards. Closing this stream does not
 * close the underlying stream.
 */
public class ChunkedInputStream extends InputStream
{
	private final ByteStreamReader m_in;
	private long m_lRemainingInChunk = 0;
	private boolean m_bEOF = false;

	public ChunkedInputStream(ByteStreamReader in)
	{
		m_in = in;
	}

	public int read() throws IOException
	{
		byte[] b = new byte[1];
		int iRead = read(b, 0, 1);
		return iRead<0 ? -1 : (b[0] & 0xFF);
	}

	public int read(byte[] buf, int iOffset, int iLen) throws IOException
	{
		if(iLen==0) return 0;
		if(m_bEOF) return -1;
		if(m_lRemainingInChunk==0)
		{
			readChunkHeader();
			if(m_bEOF) return -1;
		}

		int iRead = m_in.read(buf, iOffset, (int)Math.min(iLen, m_lRemainingInChunk));
		if(iRead<0) throw new EOFException("Chunked request body ended part way through a chunk");
		m_lRemainingInChunk -= iRead;
		if(m_lRemainingInChunk==0) readChunkEnd();
		return iRead;
	}

	/**
	 * Read a "1a2b;ext=value" chunk size line. A size of 0 is the last chunk, followed by optional trailers
	 * and a blank line.
	 */
	private void readChunkHeader() throws IOException
	{
		String sLine = m_in.readLine();
		if(sLine==null) throw new EOFException("Chunked request body ended before the last chunk");
		int iPos = sLine.indexOf(';');
		if(iPos>=0) sLine = sLine.substring(0, iPos);
		sLine = sLine.trim();
		long lSize;
		try
		{
			if(sLine.length()==0 || sLine.length()>15 || sLine.charAt(0)=='-' || sLine.charAt(0)=='+') throw new NumberFormatException();
			lSize = Long.parseLong(sLine, 16);
		}
		catch(NumberFormatException nfe)
		{
			throw new IOException("Invalid chunk size '" + sLine + "'");
		}

		if(lSize==0)
		{
			//skip any trailer fields, up to the blank line that ends the body
			String sTrailer;
			while((sTrailer = m_in.readLine())!=null && sTrailer.length()>0) {}
			if(sTrailer==null) throw new EOFException("Chunked request body ended before the final blank line");
			m_bEOF = true;
		}
		else
			m_lRemainingInChunk = lSize;
	}

	/**
	 * Each chunk's data is followed by CRLF
	 */
	private void readChunkEnd() throws IOException
	{
		String sLine = m_in.readLine();
		if(sLine==null) throw new EOFException("Chunked request body ended after a chunk");
		if(sLine.length()!=0) throw new IOException("Chunk data was not followed by CRLF");
	}

	public void close()
	{
		//leave the underlying connection open
	}
}
