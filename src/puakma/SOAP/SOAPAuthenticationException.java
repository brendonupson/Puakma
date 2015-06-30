/** ***************************************************************
SOAPAuthenticationException.java
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


package puakma.SOAP;

/**
 * <p>Title: </p>
 * <p>Description: This is thrown when a SOAP call fails because the user is not allowed access to the widget.</p>
 * <p>Copyright: Copyright (c) 2005</p>
 * <p>Company: </p>
 * @author Brendon Upson
 * @version 1.0
 */

public class SOAPAuthenticationException extends SOAPFaultException
{

	private static final long serialVersionUID = 108209411800935195L;

	public SOAPAuthenticationException(String sCode, String sMessage)
	{
		super(sCode, sMessage);
	}

}