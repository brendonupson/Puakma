/** ***************************************************************
pmaSessionCleaner.java
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

import puakma.server.*;
import puakma.error.*;
import java.util.*;

public class pmaSessionCleaner extends Thread
{
	private pmaSystem m_pSystem;
	private pmaServer m_pParent;
	private Date m_dtSecondReference=new Date();

	public pmaSessionCleaner(pmaServer paramParent, pmaSystem paramSystem)
	{
		m_pParent = paramParent;
		m_pSystem = paramSystem;
	}

	public void run()
	{
		int iCheckEvery=60;

		while(m_pSystem.isSystemRunning())
		{
			if(secondsHaveElapsed(iCheckEvery))
			{				
				m_pSystem.pErr.doDebug(pmaLog.DEBUGLEVEL_STANDARD, iCheckEvery + " second check for expired sessions", m_pParent);
				m_pSystem.dropSessions(false);
				//System.gc(); //ask for a garbage collection
			}
			try{Thread.sleep(10000);}catch(Exception e){}
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