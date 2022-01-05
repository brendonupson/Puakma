/** ***************************************************************
DocumentFileItem.java
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
package puakma.system;

import java.io.File;
import java.io.FileInputStream;
import java.util.Vector;

import puakma.error.pmaLog;

/**
 * Describes files attached to a document
 * 21/3/2012 Now allows multiple files to be attached to the same item. Should be backwardly compatible with older code
 */
public class DocumentFileItem extends DocumentItem
{
	//private File m_fData;
	//private String m_sMimeType="";
	private boolean m_bDeleteOnExit=true;
	private Vector m_vFiles = new Vector();

	/*
	 * Used to create file items
	 */
	public DocumentFileItem(Document paramParent, String paramItemName, String paramItemValue, File paramData, String paramMimeType)
	{
		super(paramParent, paramItemName, paramItemValue);
		setType(ITEM_TYPE_FILE);

		//m_fData = paramData;
		//m_sMimeType = paramMimeType;
		appendFile(paramItemValue, paramData, paramMimeType, null);
	}

	public DocumentFileItem(Document paramParent, String paramItemName, String paramItemValue, File paramData, String paramMimeType, String sTransferEncoding)
	{		
		super(paramParent, paramItemName, paramItemValue);
		setType(ITEM_TYPE_FILE);

		//m_fData = paramData;
		//m_sMimeType = paramMimeType;
		appendFile(paramItemValue, paramData, paramMimeType, sTransferEncoding);
	}

	/**
	 * Determines if the file associated with this object should be deleted when
	 * the object is finalized by the garbage collector. Default is true
	 * @param bDelete
	 */
	public synchronized void setDeleteOnExit(boolean bDelete)
	{
		m_bDeleteOnExit = bDelete;
	}

	/**
	 * Delete any temp files when this item is destroyed
	 */
	protected void finalize()
	{		
		//System.out.println("DEBUG: Deleting Temp File: " + m_fData.getAbsolutePath());
		if(m_bDeleteOnExit) 
		{			
			for(int i=0; i<m_vFiles.size(); i++)
			{
				FileData fData = (FileData)m_vFiles.get(i);
				if(fData!=null)
				{
					m_docParent.pSystem.doDebug(pmaLog.DEBUGLEVEL_FULL, "Deleting Temp File: " + fData.file.getAbsolutePath() + " ("+fData.fileName + ")", m_docParent);
					fData.file.delete();
					fData = null;
				}
				//m_vFiles.remove(i);
			}
		}
	}

	public byte[] getValue()
	{
		FileData fData = getFileData(0);
		if(fData==null || fData.file==null) return null;

		byte b[] = new byte[(int)fData.file.length()];

		try
		{
			FileInputStream fis = new FileInputStream(fData.file);
			fis.read(b);
			fis.close();
		}
		catch(Exception e)
		{
			return null;
		}
		return b;
	}

	public Object getData()
	{
		//return (Object)m_fData;
		FileData fData = getFileData(0);
		if(fData==null) return null;
		return (Object)fData.file;
	}

	public String getMimeType()
	{
		FileData fData = getFileData(0);
		if(fData==null) return null;
		return fData.mimeType;
	}

	/**
	 * This will return a handle to the File object
	 * @return Object
	 */
	public Object getObject()
	{
		//return (Object)m_fData;
		return getData();
	}

	/**
	 * Return the data associated with this file
	 * @param iFileIndex
	 * @return
	 */
	public FileData getFileData(int iFileIndex)
	{
		if(iFileIndex>=m_vFiles.size() || iFileIndex<0) return null;

		FileData fData = (FileData)m_vFiles.get(iFileIndex);

		return fData;
	}

	/**
	 * 
	 * @param sFileName
	 * @param file
	 * @param sMimeType
	 */
	public void appendFile(String sFileName, File file, String sMimeType)
	{
		appendFile(sFileName, file, sMimeType, null);
	}
	
	
	/**
	 * 
	 * @param sFileName
	 * @param file
	 * @param sMimeType
	 * @param sTransferEncoding 
	 */
	public void appendFile(String sFileName, File file, String sMimeType, String sTransferEncoding)
	{		
		if(sFileName==null || sFileName.length()==0) return;
		
		FileData fData = new FileData(sFileName, file, sMimeType, sTransferEncoding);
		m_vFiles.add(fData);
	
		//set value to concatentation of all filenames
		StringBuilder sbFileNames = new StringBuilder(50);
		for(int i=0; i<m_vFiles.size(); i++)
		{
			if(sbFileNames.length()>0) sbFileNames.append(", ");
			FileData f = getFileData(i);
			if(f!=null && f.fileName!=null && f.fileName.length()>0) sbFileNames.append(f.fileName);
		}
		setStringValue(sbFileNames.toString());
		setType(ITEM_TYPE_FILE);
	}

	/**
	 * Returns the number of files included
	 * @return
	 */
	public int getFileCount()
	{
		return m_vFiles.size();
	}

}