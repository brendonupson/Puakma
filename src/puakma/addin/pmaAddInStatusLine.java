/** ***************************************************************
pmaAddInStatusLine.java
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
package puakma.addin;

import java.util.*;
import java.text.*;

/**
* This is the mechanism for addins to commnuicate their status
*/
public class pmaAddInStatusLine
{
  private String AddInName="";
  private String Status="";
  private Date dtLastUpdated = new Date();
  SimpleDateFormat sdf = new SimpleDateFormat( "d/M/yy H:mm:ss" );
  static final int NAME_WIDTH=20;

  public pmaAddInStatusLine(String paramName)
  {
    AddInName = paramName;
    if(AddInName.length()>NAME_WIDTH) AddInName = AddInName.substring(0, NAME_WIDTH);
    for(int i=paramName.length(); i<NAME_WIDTH; i++)
    {
      AddInName += " ";
    }
  }

  /**
  * Sets the status line
  */
  public synchronized void setStatus(String paramStatus)
  {
    dtLastUpdated = new Date();
    Status = paramStatus;
  }

  /**
  * @return the status line to the caller
  */
  public String toString()
  {
    return  AddInName + "| " + Status + " (" + sdf.format(dtLastUpdated) +")";
  }
}