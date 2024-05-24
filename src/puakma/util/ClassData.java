/** ***************************************************************
ClassData.java
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
package puakma.util;

import java.io.*;

/**
 * Used to determine the contents of a binary class file.
 */
public class ClassData
{


	// tags: https://en.wikipedia.org/wiki/Java_class_file

	public static final int CONSTANT_Utf8 = 1;
	public static final int CONSTANT_Integer = 3;
	public static final int CONSTANT_Float = 4;
	public static final int CONSTANT_Long = 5;
	public static final int CONSTANT_Double = 6;
	public static final int CONSTANT_Class = 7;
	public static final int CONSTANT_String = 8;
	public static final int CONSTANT_Fieldref = 9;
	public static final int CONSTANT_Methodref = 10;
	public static final int CONSTANT_InterfaceMethodref = 11;
	public static final int CONSTANT_NameAndType = 12;

	public static final int CONSTANT_MethodHandle = 15;
	public static final int CONSTANT_MethodType = 16;
	public static final int CONSTANT_Dynamic = 17;
	public static final int CONSTANT_InvokeDynamic = 18;
	public static final int CONSTANT_Module = 19;
	public static final int CONSTANT_Package = 20;


	private static int JAVA_CLASS_MAGIC = 0xCAFEBABE;
	public int m_Magic=0;
	public int m_MajorVersion=0;
	public int m_MinorVersion=0;
	public String m_PackageName="";
	public String m_ClassName="";
	public String m_FullName="";  //name including all package references

	/*public static void main(String[] args)
	{
		try
		{
		ClassData cd = new ClassData(new FileInputStream("/Users/bupson/Downloads/OpenDashboard.class"));
		System.out.println(cd.m_ClassName + " valid=" + cd.isValidClass());
		}catch(Exception e) {System.err.println(e.toString());}
	}*/


	/** Returns the package name of the class file that can be read by
	 * the input stream. Returns the empty string if the class is in
	 * the default package.
	 */
	public ClassData(InputStream ins)
	{
		DataInputStream in = null;

		try
		{
			in = new DataInputStream(ins);
			m_Magic = in.readInt();
			if(m_Magic!=JAVA_CLASS_MAGIC) return; //not a Java class file, bail out
				
			m_MinorVersion=in.readShort(); // minor
			m_MajorVersion=in.readShort(); // major
			int count = in.readUnsignedShort();
			Object[] pool = new Object[count];

			for (int i = 1; i < count; i++)
			{
				int tag = in.readUnsignedByte();
				switch (tag)
				{
				case CONSTANT_Class:
					pool[i] = new Integer(in.readUnsignedShort());// name_index
					break;
				case CONSTANT_Fieldref:
				case CONSTANT_Methodref:
				case CONSTANT_InterfaceMethodref:
					in.readShort(); // class index
					in.readUnsignedShort(); // name_and_type index
					break;
				case CONSTANT_String:
					in.readShort(); // string index
					break;
				case CONSTANT_Integer:
				case CONSTANT_Float:
					in.readInt(); // bytes
					break;
				case CONSTANT_Long:
				case CONSTANT_Double:
					i++;
					in.readInt(); // high bytes
					in.readInt(); // low bytes
					break;
				case CONSTANT_NameAndType:
					in.readShort(); // name index
					in.readUnsignedShort(); // descriptor index
					break;
				case CONSTANT_Utf8:					
					pool[i] = in.readUTF();					
					break;

				case CONSTANT_MethodHandle:
					//in.readInt(); //4`
					in.readShort(); //2
					in.readByte(); //1
					break;
				case CONSTANT_MethodType:
					in.readShort();
					break;
				case CONSTANT_Dynamic:
					in.readInt(); //4`
					break;
				case CONSTANT_InvokeDynamic:
					in.readInt(); //4`
					break;
				case CONSTANT_Module:
					in.readShort(); //2
					break;
				case CONSTANT_Package:
					in.readShort(); //2
					break;
				default:
					System.err.println("Unknown tag in class: "+tag);
					break;
				}//switch
			}//for
			in.readUnsignedShort(); // access flags
			int thisClass = in.readUnsignedShort();

			Integer nameIndex = (Integer)pool[thisClass];
			String name="";
			if(nameIndex!=null) name = (String)pool[nameIndex.intValue()];

			name = name.replace('/', '.');
			m_FullName = name; //new String(name);
			int dot = name.lastIndexOf('.');
			if (dot>=0)
			{
				m_PackageName = name.substring(0, dot);
				m_ClassName = name.substring(dot+1, name.length());
			}
			else
			{
				m_PackageName = "";
				m_ClassName = name; //new String(name);
			}
			in.close();
		}
		catch (Exception e)
		{
			System.err.println("Error parsing ClassData: " + e.toString());
			e.printStackTrace();
		}
		finally
		{
			try{ if(in!=null) in.close(); }catch(Exception r) {}
		}
	}

	/**
	 * determines if the class is valid based on its 'magic' value
	 */
	public boolean isValidClass()
	{
		if(m_Magic == JAVA_CLASS_MAGIC && m_ClassName!=null && m_ClassName.length()>0) return true;
		return false;
	}
}