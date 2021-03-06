/* ***************************************************************
HTMLViewCallback.java
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

package puakma.addin.http.document;

public interface HTMLViewCallback 
{
	/**
	 * Return true if the row should be included in the output, false to skip.
	 * New items can also be added or removed from the TableManager object
	 * @param t
	 * @return
	 */
	public boolean htmlViewCallback(TableManager t);
}
