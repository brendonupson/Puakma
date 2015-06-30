/** ***************************************************************
ActionReturn.java
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
package puakma.addin.http.action;

/**
* Just a holder class to return from the runActionOnDocument() method in the
* HTTPRequestManager
*/
public class ActionReturn
{

  //public StringBuilder sBuffer=null;
  public byte[] bBuffer=null;
  public String RedirectTo=null;
  public String ContentType=null;
  public boolean HasStreamed=false; //action has streamed data directly to the browser

  public ActionReturn()
  {
  }
  
  public String toString()
  {
	  StringBuilder sb = new StringBuilder();
	  
	  if(bBuffer==null)
		  sb.append("bBuffer=null");
	  else
	  {
		  sb.append("bBuffer=" + bBuffer.length);
		  sb.append("bBuffer=" + new String(bBuffer));
	  }
	  sb.append("RedirectTo=" + RedirectTo);
	  sb.append("ContentType=" + ContentType);
	  sb.append("HasStreamed=" + HasStreamed);
	  return sb.toString();
  }

} 