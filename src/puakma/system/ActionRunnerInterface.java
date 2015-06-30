/** ***************************************************************
ActionRunnerInterface.java
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

import java.io.IOException;
import java.io.OutputStream;

import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.document.HTMLDocument;

//****** THIS SHOULD LIVE IN THE SYSTEM PACKAGE *******

public interface ActionRunnerInterface
{
	public void init(HTTPSessionContext paramSession, HTMLDocument paramDoc, String paramGroup, String paramApp);
	public String execute();
	public StringBuilder getStringBuilder();
	public byte[] getByteBuffer();
	public String getContentType();
	public boolean shouldQuit();
	public void requestQuit();
	public boolean hasStreamed();  
	public void streamToClient(byte buf[]) throws IOException;  
	public OutputStream getOutputStream();
}