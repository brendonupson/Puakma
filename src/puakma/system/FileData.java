/** ***************************************************************
FileData.java
Copyright (C) 2012  Brendon Upson 
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
package puakma.system;

import java.io.File;
import java.io.FileInputStream;

public class FileData 
{	
	public File file;
	public String mimeType="";
	public String fileName="";
	public String transferEncoding=null;
	
	/**
	 * Easy one line object create
	 * @param sFileName
	 * @param fileData
	 * @param sMimeType
	 * @param sTransferEncoding 
	 */
	public FileData(String sFileName, File fileData, String sMimeType, String sTransferEncoding) 
	{
		fileName = sFileName;
		mimeType = sMimeType;
		file = fileData;
		transferEncoding = sTransferEncoding;
	}
	
	/**
	 * Convenience method for getting the file as a byte array
	 * @return
	 */
	public byte[] getBytes()
	{
		if(file==null) return null;
		byte b[] = new byte[(int)file.length()];

		try
		{
			FileInputStream fis = new FileInputStream(file);
			fis.read(b);
			fis.close();
		}
		catch(Exception e)
		{
			return null;
		}
		return b;
	}
	
}
