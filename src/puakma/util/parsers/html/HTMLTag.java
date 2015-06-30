/** ***************************************************************
HTMLTag.java
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

package puakma.util.parsers.html;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;

/**
 * This represents a single HTML tag. Each TagToken has a name and a
 * list of attributes and values.
 */
public class HTMLTag {


	public static final char ESCAPE = '\\';
	public static final char QUOTE = '"';
	private String name = null;

	private boolean end = false;

	private Hashtable attr = null;

	public HTMLTag (String line) {

		attr = new Hashtable();
		tokenizeAttributes(line);
	}

	public String getName () {
		return name;
	}

	public Hashtable getAttributes () {
		return attr;
	}

	public boolean isEndTag () {
		return end;
	}

	public boolean hasAttribute (String name) {
		return attr.contains(name.toLowerCase());
	}

	public String getAttribute (String name) {
		return (String)attr.get(name.toLowerCase());
	}

	public String toString () 
	{

		StringBuilder sb;  // Stores the string to be returned.
		//Enumeration list; // List of node's arguments or children.

		// Get a new StringBuilder.
		sb = new StringBuilder(256);

		// Write the opening of the tag.
		if (end)
			sb.append("</" + name);
		else
			sb.append('<' + name);

		// Check if there are any attributes.
		if (attr != null) {

			Enumeration enu = attr.elements();
			if (enu.hasMoreElements()) {
				sb.append(' ');

				while (enu.hasMoreElements()) {
					String attrName = null;
					String attrValue = null;

					attrName = (String)enu.nextElement();
					attrValue = (String)attr.get(attrName);

					sb.append(attrName);
					sb.append("=\"");
					sb.append(attrValue);
					sb.append("\"");

				}
			}
		}

		sb.append('>');
		return sb.toString();
	}

	/**
	 * Sets the name of the token and also whether it is a begin
	 * or an end token.
	 * @param name the name of the token.
	 */
	private void setName (String name) {

		if (name == null) 
		{
			this.name = null;
			return;
		}
		if(name.equals("")) 
		{
			this.name = "";
			return;
		}

		String lcname = name.toLowerCase();

		if (lcname.charAt(0) == '/') {
			this.name = lcname.substring(1);
			end = true;
		} else {
			this.name = lcname;
		}
	}

	/**
	 * Adds a attribute and value to the list. 
	 * @param name the name of the attribute.
	 * @param value the value of the attribute.
	 */
	private void setAttribute (String name, String value) {
		attr.put(name.toLowerCase(), value);
	}

	/**
	 * Adds a attribute to the list using the given string. The string
	 * may either be in the form 'attribute' or 'attribute=value'.
	 * @param s contains the attribute information.
	 */
	private void setAttribute (String s) {

		int idx;	// The index of the = sign in the string.
		String name;	// Stores the name of the attribute.
		String value;	// Stores the value of the attribute.

		// Check if the string is null.
		if (s == null) return; 

		// Get the index of = within the string.
		idx = s.indexOf('=');

		// Check if there was '=' character present.
		if (idx < 0) {

			// If not, add the whole string as the attribute
			// name with a null value.
			setAttribute(s.toLowerCase(), "");
		} else {

			// If so, split the string into a name and value.

			name = s.substring(0, idx);
			value = s.substring(idx + 1);

			// Add the name and value to the attribute list.
			setAttribute(name.toLowerCase(), value);
		}
	}

	/**
	 * Tokenizes the given string and uses the resulting vector
	 * to to build up the TagToken's attribute list.
	 * @param args the string to tokenize.
	 */
	private void tokenizeAttributes (String args) 
	{

		String[] tokens = null;	// Array of tokens from vector.
		int length;		// Size of the vector.
		int i;			// Loop variable.

		// Get the vector of tokens.
		ArrayList v = tokenizeString(args);

		// Check it is not null.
		if (v == null) return;

		// Create a new String array.
		length = v.size() - 1;
		if (length > 0) tokens = new String[length];

		// Get an enumeration of the vector's elements.
		Enumeration e = Collections.enumeration(v);//v.elements();

		// Store the first element as the TagToken's name.
		setName((String) e.nextElement());

		// Stop processing now if there are no more elements.
		if (! e.hasMoreElements()) return;

		// Put the rest of the elements into the string array.
		i = 0;
		while (e.hasMoreElements())
			tokens[i++] = (String) e.nextElement();

		// Deal with the name/value pairs with separate = signs.
		for (i = 1; i < (length - 1); i++) {

			if (tokens[i] == null) continue;

			if (tokens[i].equals("=")) {
				setAttribute(tokens[i - 1], tokens[i + 1]);
				tokens[i] = null;
				tokens[i - 1] = null;
				tokens[i + 1] = null;
			}
		}

		// Deal with lone attributes and joined name/value pairs.
		for (i = 0; i < length; i++)
			if (tokens[i] != null) setAttribute(tokens[i]);
	}

	/**
	 * This method tokenizes the given string and returns a vector
	 * of its constituent tokens. It understands quoting and character
	 * escapes.
	 * @param s the string to tokenize.
	 */
	private ArrayList tokenizeString (String s) {

		// First check that the args are not null or zero-length.
		if (s == null || s.length() == 0) return null;

		boolean whitespace = false; // True if we are reading w/space.
		boolean escaped = false;    // True if next char is escaped.
		boolean quoted = false;	    // True if we are in quotes.
		int length;		    // Length of attribute string.
		int i = 0;		    // Loop variable.

		// Create a vector to store the complete tokens.
		ArrayList tokens = new ArrayList();

		// Create a buffer to store an individual token.
		StringBuilder buffer = new StringBuilder(80);

		// Convert the String to a character array;
		char[] array = s.toCharArray();

		length = array.length;

		// Loop over the character array.
		while (i < length) {

			// Check if we are currently removing whitespace.
			if (whitespace) {
				if (isWhitespace(array[i])) {
					i++;
					continue;
				} else {
					whitespace = false;
				}
			}

			// Check if we are currently escaped.
			if (escaped) {

				// Add the next character to the array.
				buffer.append(array[i++]);

				// Turn off the character escape.
				escaped = false;

				continue;
			} else {

				// Check for the escape character.
				if (array[i] == ESCAPE) {
					escaped = true;
					i++;
					continue;
				}

				// Check for the quotation character.
				if (array[i] == QUOTE) {
					quoted = !quoted;
					i++;
					continue;
				}

				// Check for the end of the token.
				if (!quoted && isWhitespace(array[i])) {

					// Add the token and refresh the buffer.
					tokens.add(buffer.toString());
					buffer = new StringBuilder(80);

					// Stop reading the token.
					whitespace = true;

					continue;
				}

				// Otherwise add the character to the buffer.
				buffer.append(array[i++]);
			}
		}

		// Add the last token to the vector if there is one.
		if (! whitespace) tokens.add(buffer.toString());

		return tokens;
	}

	/**
	 * Returns true if the given character is considered to be
	 * whitespace.
	 * @param c the character to test.
	 */
	private boolean isWhitespace (char c) {
		return (c == ' ' || c == '\t' || c == '\n');
	}
}