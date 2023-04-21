/** ***************************************************************
pmaTempFileCleaner.java
Copyright (C) 2023  Brendon Upson 
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

import puakma.server.*;
import puakma.util.Util;
import puakma.error.*;

import java.io.File;
import java.util.*;

public class pmaTempFileCleaner extends Thread
{
	private pmaSystem m_pSystem;
	private pmaServer m_pParent;
	private Date m_dtSecondReference=new Date();

	public pmaTempFileCleaner(pmaServer paramParent, pmaSystem paramSystem)
	{
		m_pParent = paramParent;
		m_pSystem = paramSystem;
	}

	public void run()
	{
		int iCheckEveryHours=2;
		int iCheckEverySeconds=iCheckEveryHours*60*60;

		cleanTempFiles();
		while(m_pSystem.isSystemRunning())
		{
			if(secondsHaveElapsed(iCheckEverySeconds))
			{				
				m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_STANDARD, iCheckEverySeconds + " second check for old temp files", m_pParent);
				cleanTempFiles();
			}
			try{Thread.sleep(60000);}catch(Exception e){}
		}
	}

	private void cleanTempFiles() 
	{
		try
		{
			Date dtExpires = Util.adjustDate(new Date(), 0, 0, 0, -2, 0, 0);//older than 2 hours
			
			File fTempDir = m_pSystem.getTempDir();
			File[] files = fTempDir.listFiles();
			int iDeleteCount = 0;
			if(files!=null)
			{
				for(int i=0; i<files.length; i++)
				{
					File file = files[i];
					System.out.println("Checking temp file: " + file.getName());
					if(file.isFile() && file.getName().toLowerCase().endsWith(".tmp") && file.lastModified()<dtExpires.getTime())
					{
						System.err.println("Deleting temp file: " + file.getName());
						//m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_STANDARD, "Deleting temp file: " + file.getName(), m_pParent);
						try { file.delete(); iDeleteCount++; }catch(Exception e) {e.printStackTrace();}
					}
				}
			}
			if(iDeleteCount>0) m_pSystem.pErr.doInformation(iDeleteCount + " temp files removed", m_pParent);
		}
		catch(Exception e)
		{
			m_pSystem.pErr.doError("cleanTempFiles(): " + e.toString(), m_pParent);
		}
		
	}

	/**
	 * @return true if iSeconds have elapsed since the method was last called
	 */
	private boolean secondsHaveElapsed(int iSeconds)
	{
		Date dtNow = new Date();
		if( (int)((dtNow.getTime()-m_dtSecondReference.getTime())/1000) > iSeconds)
		{
			m_dtSecondReference = new Date();
			return true;
		}
		return false;
	}
}