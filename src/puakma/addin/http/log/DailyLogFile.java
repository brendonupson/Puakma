/** ***************************************************************
HTTPLogger.java
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

package puakma.addin.http.log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A buffered, daily rolling text log file. The previous day's file is closed when a new one is opened. 
 * NOT thread safe: use from a single thread, eg the AsyncLogWriter thread. Call flush() when idle.
 */
public class DailyLogFile
{
	private static final long OPEN_RETRY_MS = 30000;
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final Supplier<String> m_patternSupplier; //file name with a * that is replaced by the date
	private final SimpleDateFormat m_dateFormat;
	private final Supplier<String> m_newFileHeader; //may be null. Text written at the top of a newly created file
	private Consumer<String> m_openListener = null;
	private BufferedOutputStream m_out = null;
	private LocalDate m_dayOpened = null;
	private long m_lNextOpenAttempt = 0;

	public DailyLogFile(Supplier<String> patternSupplier, SimpleDateFormat dateFormat, Supplier<String> newFileHeader)
	{
		m_patternSupplier = patternSupplier;
		m_dateFormat = dateFormat;
		m_newFileHeader = newFileHeader;
	}

	/**
	 * Notified with the file name each time a file is opened
	 */
	public void setOpenListener(Consumer<String> listener)
	{
		m_openListener = listener;
	}

	/**
	 * Write a line (the caller includes any line terminator). 
	 * @throws IOException if the file cannot be opened or written. After an open failure further attempts 
	 * are suppressed for 30 seconds (and throw too) so a bad path doesn't hammer the file system.
	 */
	public void write(String sText) throws IOException
	{
		LocalDate today = LocalDate.now();
		if(m_out==null || !today.equals(m_dayOpened)) open(today);
		m_out.write(sText.getBytes(UTF8));
	}

	private void open(LocalDate today) throws IOException
	{
		long lNow = System.currentTimeMillis();
		if(lNow<m_lNextOpenAttempt) throw new IOException("Log file unavailable, retrying later");

		close(); //release the previous day's file
		try
		{
			String sPattern = m_patternSupplier.get();
			if(sPattern==null || sPattern.length()==0) throw new IOException("No log file name configured");
			String sFile = sPattern.replace("*", m_dateFormat.format(new Date(lNow)));
			boolean bExists = new File(sFile).exists();
			FileOutputStream fos = new FileOutputStream(sFile, true);
			m_out = new BufferedOutputStream(fos, 32768);
			m_dayOpened = today;
			if(m_openListener!=null) m_openListener.accept(sFile);
			if(!bExists && m_newFileHeader!=null)
			{
				String sHeader = m_newFileHeader.get();
				if(sHeader!=null) m_out.write(sHeader.getBytes(UTF8));
			}
		}
		catch(IOException e)
		{
			close();
			m_lNextOpenAttempt = lNow + OPEN_RETRY_MS;
			throw e;
		}
	}

	public void flush()
	{
		if(m_out==null) return;
		try{ m_out.flush(); }catch(IOException e){ close(); } //reopened on the next write
	}

	public void close()
	{
		if(m_out==null) return;
		try{ m_out.close(); }catch(IOException e){}
		m_out = null;
	}
}
