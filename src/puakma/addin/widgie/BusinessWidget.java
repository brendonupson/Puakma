/** ***************************************************************
BusinessWidget.java
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
package puakma.addin.widgie;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.Hashtable;

import puakma.SOAP.SOAPCallParser;
import puakma.SOAP.VoidReturn;
import puakma.addin.http.TornadoApplication;
import puakma.addin.http.TornadoServer;
import puakma.addin.http.TornadoServerInstance;
import puakma.addin.http.action.HTTPSessionContext;
import puakma.addin.http.action.SharedActionClassLoader;
import puakma.error.ErrorDetect;
import puakma.error.pmaLog;
import puakma.system.RequestPath;
import puakma.system.SessionContext;
import puakma.system.SystemContext;
import puakma.system.pmaSystem;


/**
 * This class describes a widget. It is what gets loaded in to the Hashtable, and
 * it contains a running BusinessWidget
 */
public class BusinessWidget extends Thread implements ErrorDetect
{
	private boolean m_bRunning=false;
	protected SystemContext m_pSystem=null;
	private String m_sDescription = "";
	private static String SESSION_CONTEXT_CLASS = "puakma.addin.http.action.HTTPSessionContext";
	//private static String BYTE_ARRAY="byte[]";
	private WIDGIE m_WidgetContainer=null;
	private RequestPath m_rPath = null;
	private int m_iCallbackMS=60000;
	private static final int DEFAULT_SB_SIZE = 4096;

	/**
	 * constructor
	 */
	public BusinessWidget()
	{
		super("BusinessWidget");
		setDaemon(true);
	}

	/**
	 * Override this method with your own initialisation code
	 * @return true if init was successful. false will cause the thread to not start
	 */
	protected boolean init()
	{
		//your init code here
		//System.out.println("DEBUG: init() " + getErrorSource());
		return true;
	}

	/**
	 * Call to initialise this object
	 * @param paramSystem
	 */
	public final void init(SystemContext paramSystem, WIDGIE wContainer, RequestPath rPath)
	{
		if(m_pSystem==null) m_pSystem = paramSystem;
		if(m_WidgetContainer==null) m_WidgetContainer = wContainer;
		if(rPath!=null) m_rPath = new RequestPath(rPath.getFullPath());
	}

	/**
	 * sets the description of this service
	 * @param sNewDesc
	 */
	public final synchronized void setDescription(String sNewDesc)
	{
		m_sDescription = sNewDesc;
	}

	/**
	 * gets the description of this service
	 * @return
	 */
	public final String getDescription()
	{
		return m_sDescription;
	}


	/**
	 * increase the number of times this object has been used
	 */
	public final void run()
	{
		if(m_bRunning) return;

		m_bRunning=init();
		boolean bFirstTime=true;
		while(m_bRunning)
		{
			//System.out.println("DEBUG: " + currentThread().getName() + " Running " + getErrorSource());
			if(!bFirstTime) widgetCallback();
			bFirstTime = false;
			try{Thread.sleep(this.m_iCallbackMS);} catch(Exception e){}
		}
		//System.out.println("DEBUG: FINISHED " + getErrorSource());
	}

	public synchronized final void setCallbackMS(int iNewCallbackIntervalMS)
	{
		if(iNewCallbackIntervalMS<=0) iNewCallbackIntervalMS = 60000;
		m_iCallbackMS = iNewCallbackIntervalMS;
	}

	/**
	 * get the number of milliseconds used for internal callback
	 */
	public final int getCallbackMS()
	{
		return m_iCallbackMS;
	}
	/**
	 * Called every x ms by the widget. Used to do periodic processing in your Widget.
	 *Overload with your own method
	 */
	protected void widgetCallback()
	{

	}


	/**
	 * Override this method with your own shutdown code
	 */
	protected void requestQuit()
	{
		//your shutdown code here
		//m_pSystem.doInformation("Shutting down", this);
	}

	/**
	 * Return a session context. If no user specific context is available (due to callback or init), then
	 * return a system based context
	 */
	public final HTTPSessionContext getSession()
	{
		HTTPSessionContext pSession = m_WidgetContainer.getSessionFromThreadID(Thread.currentThread().getName());

		//check to see if widget is being initialised
		if(pSession==null) //if used during callback
		{
			SessionContext sessCtx = m_pSystem.createSystemSession(this.getErrorSource());
			//sessCtx.setSessionTimeOut(1); //default 1 minute timout
			//use server default timeouts
			TornadoServerInstance tsi = TornadoServer.getInstance();
			TornadoApplication ta = tsi.getTornadoApplication(m_rPath.getPathToApplication());
			ta.setUpUserRoles(sessCtx);
			HTTPSessionContext HTTPSessCtx = new HTTPSessionContext(m_pSystem, sessCtx, m_rPath);

			return HTTPSessCtx;
		}

		pSession.setLastTransactionTime();
		return pSession;

	}

	/**
	 * this is how we stop the Widget. If you override this class, you must call
	 * this via (eg:) super.requestQuit();
	 */
	public final synchronized void postQuitMessage()
	{
		m_bRunning = false;
		this.interrupt();
		this.requestQuit();
	}




	/**
	 * This will output the WSDL method definitions for this class. Note that system methods will
	 * not be displayed.
	 * @return
	 */
	public StringBuilder getWSDLMessages()
	{
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		//StringBuilder sbJavaMethod = new StringBuilder(DEFAULT_SB_SIZE);

		Method meth[] = this.getClass().getDeclaredMethods(); //doesn't show inheritied methods
		for(int i=0; i<meth.length; i++)
		{
			String sParams=null;
			if(Modifier.isPublic(meth[i].getModifiers()))
			{
				sb.append("  <message name=\"" + meth[i].getName() + "Request\">\r\n");
				Class c[]=meth[i].getParameterTypes();
				int iParamCount=1;
				for(int k=0; k<c.length; k++)
				{
					if(k==0)
					{
						if(!c[k].getName().equals(SESSION_CONTEXT_CLASS)) sb.append("\t<part name=\"p" + iParamCount++ + "\" type=\"" + getSOAPType(c[k], false) + "\"/>\r\n");
					}
					else
						sb.append("\t<part name=\"p" + iParamCount++ + "\"     type=\"" + getSOAPType(c[k], false) + "\"/>\r\n");

					if(!c[k].getName().equals(SESSION_CONTEXT_CLASS)) if(sParams==null) sParams = getJavaType(c[k]); else sParams += ", " + getJavaType(c[k]);
				}
				sb.append("  </message>\r\n");

				if(sParams==null) sParams = ""; //no method parameters
				sb.append("\t<!-- " + getJavaType(meth[i].getReturnType()) + " " + meth[i].getName() + "( " + sParams + " ); -->\r\n");

				//if(!meth[i].getReturnType().getName().equals("void"))
				//{
				sb.append("  <message name=\"" + meth[i].getName() + "Response\">\r\n");
				if(!meth[i].getReturnType().getName().equals("void")) sb.append("\t<part name=\""+meth[i].getName()+"Return\" type=\"" + getSOAPType(meth[i].getReturnType(), false)+ "\"/>\r\n");
				sb.append("  </message>\r\n\r\n");
				//}
			}
		}

		return sb;
	}


	/**
	 * This will output the &lt;operation&gt; WSDL tags. Note that system methods will
	 * not be displayed.
	 * @return
	 */
	public StringBuilder getWSDLBindings(String sActionName)
	{
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE); 
		String sBody = "\t<soap:body use=\"encoded\" namespace=\"urn:DefaultNamespace\" encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" />\r\n";

		Method meth[] = this.getClass().getDeclaredMethods(); //doesn't show inheritied methods
		for(int i=0; i<meth.length; i++)
		{
			if(Modifier.isPublic(meth[i].getModifiers()))
			{
				sb.append("<operation name=\"" + meth[i].getName() + "\">\r\n");
				//sb.append("\t<soap:operation soapAction=\"" + sActionName + "Action\" />\r\n");
				sb.append("\t<soap:operation soapAction=\"\" />\r\n");
				//int iLen = meth[i].getParameterTypes().length;
				//for(int k=0; k<iLen; k++)
				//{
				sb.append("\t<input name=\"" + meth[i].getName() + "Request\">\r\n");
				sb.append(sBody);
				sb.append("\t</input>\r\n");
				//}
				//no output if void return
				//if(!meth[i].getReturnType().getName().equals("void"))
				//{
				sb.append("\t<output name=\"" + meth[i].getName() + "Response\">\r\n");
				sb.append(sBody);
				sb.append("\t</output>\r\n");
				//}
				sb.append("</operation>\r\n\r\n");
			}
		}

		return sb;
	}

	/**
	 * This will output the &lt;operation&gt; WSDL tags. Note that system methods will
	 * not be displayed.
	 * @return
	 */
	public StringBuilder getWSDLPortTypes()
	{
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);
		Method meth[] = this.getClass().getDeclaredMethods(); //doesn't show inheritied methods
		for(int i=0; i<meth.length; i++)
		{
			if(Modifier.isPublic(meth[i].getModifiers()))
			{
				sb.append("<operation name=\"" + meth[i].getName() + "\">\r\n");
				//if there are parameters...
				//if(meth[i].getParameterTypes().length>0)
				sb.append("\t<input message=\"impl:" + meth[i].getName() + "Request\" name=\"" + meth[i].getName() + "Request\"/>\r\n");
				//if there is a return type
				//if(!meth[i].getReturnType().getName().equals("void"))
				//{
				sb.append("\t<output message=\"impl:" + meth[i].getName() + "Response\" name=\"" + meth[i].getName() + "Response\"/>\r\n");
				//}
				sb.append("</operation>\r\n\r\n");
			}
		}

		return sb;
	}


	/**
	 * This is where the service is called.
	 * @param sMethodName
	 * @param params
	 * @return
	 */
	public Object performService(HTTPSessionContext sess, String sMethodName, Object params[]) throws Exception
	{
		//long lStart = System.currentTimeMillis();
		
		Method meth[] = this.getClass().getDeclaredMethods(); //doesn't show inheritied methods
		Object adjustedParams[] = params;
		for(int i=0; i<meth.length; i++)
		{
			if(Modifier.isPublic(meth[i].getModifiers()) && meth[i].getName().equals(sMethodName))
			{
				
				Class c[]=meth[i].getParameterTypes();
				adjustedParams = params;
				if(adjustedParams==null) adjustedParams = new Object[0];
				//String s = c[0].getName();
				if(c.length>0 && c[0].getName().equals(SESSION_CONTEXT_CLASS))
				{
					adjustedParams = new Object[params.length+1];
					adjustedParams[0] = sess;
					for(int k=0; k<params.length; k++) adjustedParams[k+1] = params[k];
				}
				if(paramsMatch(c, adjustedParams))
				{
					ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
					
					try
					{
						SharedActionClassLoader aLoader = m_pSystem.getActionClassLoader(m_rPath);
						
						Thread.currentThread().setContextClassLoader(aLoader);
						//m_pSystem.doDebug(0, "D. invoke() ->["+meth[i].getName()+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);
						
						Object oReturn = meth[i].invoke(this, adjustedParams);
						//m_pSystem.doDebug(0, "E. invoke() ->["+meth[i].getName()+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);						
						if(meth[i].getReturnType().getName().equals("void")) return new VoidReturn();
						//m_pSystem.doDebug(0, "F. invoke() ->["+meth[i].getName()+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);						
						return oReturn;
						/*if(meth[i].getReturnType().getName().equals("void"))
						{
							m_pSystem.doDebug(0, "Da. invoke() ->["+meth[i].getName()+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);
							
							meth[i].invoke(this, adjustedParams);
							m_pSystem.doDebug(0, "E. invoke() ->["+meth[i].getName()+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);
							
							VoidReturn oReturn=new VoidReturn();
							return oReturn;
						}
						else
						{
							Object objReturn = meth[i].invoke(this, adjustedParams);													
							return objReturn;
						}*/
					}
					finally
					{
						Thread.currentThread().setContextClassLoader(originalLoader);	
						//m_pSystem.doDebug(0, "G. invoke() ->["+meth[i].getName()+"] " + (System.currentTimeMillis()-lStart) + "ms", sess);						
					}
				}
			}
		}

		//if we got this far then no method was found, so throw an exception
		String sParamList="";
		if(adjustedParams!=null)
		{
			for(int p=0; p<adjustedParams.length; p++)
			{
				if(sParamList.length()==0) sParamList = adjustedParams[p].getClass().getName();
				else sParamList += ", " + adjustedParams[p].getClass().getName();
			}
		}

		String s = m_pSystem.getSystemMessageString("WIDGIE.MethodNotFound");
		String sMsg = pmaLog.parseMessage(s, new String[]{sMethodName, sParamList});
		throw new Exception(sMsg);
	}



	/**
	 * Tests to see if the parameters passed match the actual method's parameters,
	 * including the order!
	 * @param c
	 * @param params
	 * @return true if they match
	 */
	private boolean paramsMatch(Class c[], Object params[])
	{
		if(c==null || params==null || c.length!=params.length) return false;

		for(int i=0; i<c.length; i++)
		{
			String s1 = alterClassType(c[i]);
			String s2 = null;
			if(params[i]!=null) s2 = params[i].getClass().getName();
			if(s1.startsWith("java") && s2==null) return true;
			//if objects don't match
			if(!s1.equals(s2)) return false;
		}

		//if we get here, everything has matched OK.
		return true;
	}

	/**
	 * Map the class types to their SOAP names
	 * @param c
	 * @return
	 */
	private String getSOAPType(Class c, boolean bIsDefinition)
	{
		String sPrefix = "impl:"; //"typens:";
		if(bIsDefinition) sPrefix = "";
		int iArraySize=0;
		String sBrackets="";
		if(c.isArray())
		{
			iArraySize = getArraySize(c);
			while(c.isArray()) c = c.getComponentType();
			for(int i=0; i<iArraySize; i++) sBrackets += "[]";
		}
		String sClassName = c.getName();
		if(sClassName.equals(int.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("int", iArraySize);
			else return "xsd:int";
		if(sClassName.equals(long.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("integer", iArraySize);
			else return "xsd:integer";
		if(sClassName.equals(float.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("float", iArraySize);
			else return "xsd:float";
		if(sClassName.equals(double.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("double", iArraySize);
			else return "xsd:double";
		if(sClassName.equals(boolean.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("boolean", iArraySize);
			else return "xsd:boolean";
		if(sClassName.equals(byte.class.getName())) //TODO?
			if(iArraySize==1) return "xsd:base64Binary";
		if(sClassName.equals(Date.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("dateTime", iArraySize);
			else return "xsd:dateTime";
		if(sClassName.equals(String.class.getName()))
			if(iArraySize>0) return sPrefix + makeComplexArrayType("string", iArraySize);
			else return "xsd:string";

		//default return...
		return sClassName + sBrackets;
	}


	/**
	 * Returns the xml required for the complex types used in this widget. Just
	 * returns the complextype blocks.
	 * @return
	 */
	public StringBuilder getWSDLComplexTypes()
	{
		Hashtable htTypeNames = new Hashtable(); //store each typename so we don't double-up
		StringBuilder sb = new StringBuilder(DEFAULT_SB_SIZE);

		Method meth[] = this.getClass().getDeclaredMethods(); //doesn't show inheritied methods
		for(int i=0; i<meth.length; i++)
		{
			if(Modifier.isPublic(meth[i].getModifiers()))
			{
				Class c[]=meth[i].getParameterTypes();
				for(int k=0; k<c.length; k++)
				{
					if(k==0)
					{
						if(!c[k].getName().equals(SESSION_CONTEXT_CLASS)) updateComplexType(c[k], getSOAPType(c[k], true), sb, htTypeNames);
					}
					else
						updateComplexType(c[k], getSOAPType(c[k], true), sb, htTypeNames);
				}
				updateComplexType(meth[i].getReturnType(), getSOAPType(meth[i].getReturnType(), true), sb, htTypeNames);
			}
		}

		return sb;
	}

	/**
	 * Makes a unique list of complex types
	 * @param c
	 * @param sSOAPType
	 * @param sb
	 * @param htTypeNames
	 */
	private void updateComplexType(Class c, String sSOAPType, StringBuilder sb, Hashtable htTypeNames)
	{
		if(!c.isArray() || sSOAPType.indexOf(SOAPCallParser.OBJECT_TYPE_BASE64)>=0) return; //we only map arrays for now!
		if(htTypeNames.containsKey(sSOAPType)) return; //we've already done this one...
		htTypeNames.put(sSOAPType, ""); //add to the table so we don't do it again

		String sBrackets = "";
		if(c.isArray())
		{
			int iArraySize = getArraySize(c);
			while(c.isArray()) c = c.getComponentType();
			for(int i=0; i<iArraySize; i++) sBrackets += "[]";
		}

		String sBaseType = getSOAPType(c, false);
		/*
    sb.append("\t<complexType  name=\"" + sSOAPType + "\">\r\n");
    sb.append("\t\t<complexContent mixed=\"false\">\r\n");
    sb.append("\t\t<restriction base=\"SOAP-ENC:Array\">\r\n");
    sb.append("\t\t\t<attribute ref=\"SOAP-ENC:arrayType\" wsdl:arrayType=\"" + sBaseType + sBrackets + "\"/>\r\n");
    sb.append("\t\t</restriction>\r\n");
    sb.append("\t\t</complexContent>\r\n");
    sb.append("\t</complexType>\r\n");
		 */
		sb.append("\t<complexType name=\"" + sSOAPType + "\">\r\n");
		sb.append("\t\t<complexContent>\r\n");
		sb.append("\t\t<restriction base=\"soapenc:Array\">\r\n");
		sb.append("\t\t\t<attribute ref=\"soapenc:arrayType\" wsdl:arrayType=\"" + sBaseType + sBrackets + "\"/>\r\n");
		sb.append("\t\t</restriction>\r\n");
		sb.append("\t\t</complexContent>\r\n");
		sb.append("\t</complexType>\r\n");

	}


	/**
	 * Makes a definition for an array. Can be multiple dimensions.
	 * @param sBaseType
	 * @param iArraySize
	 * @return
	 */
	private String makeComplexArrayType(String sBaseType, int iArraySize)
	{
		String sDimensions = "";
		for(int i=0; i<iArraySize; i++) sDimensions += "_";
		return sBaseType + "Array" + sDimensions;
	}


	/**
	 * Map the class types to their Java names
	 * @param c
	 * @return
	 */
	private String getJavaType(Class c)
	{
		int iArraySize=0;
		String sBrackets="";
		if(c.isArray())
		{
			iArraySize = getArraySize(c);
			while(c.isArray()) c = c.getComponentType();
			for(int i=0; i<iArraySize; i++) sBrackets += "[]";
		}
		String sClassName = c.getName();
		return sClassName + sBrackets;
	}

	/**
	 * Determines the dimensions of an array based on the classname
	 * @param c
	 * @return
	 */
	private int getArraySize(Class c)
	{
		String sName = c.getName();
		int iReturn=0;
		for(int i=0; i<sName.length(); i++)
		{
			if(sName.charAt(i)=='[')
				iReturn++;
			else
				break;
		}
		return iReturn;
	}

	/**
	 * Checks for primitive types and matches them up with their Object equivalent
	 * so the parameter comparison can occur correctly
	 * TODO: Other array types!!
	 * @param c
	 * @return
	 */
	private String alterClassType(Class c)
	{
		if(c.isPrimitive())
		{
			String sName = c.getName();
			if(sName.equals("int")) return Integer.class.getName();
			if(sName.equals("float")) return Float.class.getName();
			if(sName.equals("short")) return Short.class.getName();
			if(sName.equals("long")) return Long.class.getName();
			if(sName.equals("char")) return Character.class.getName();
			if(sName.equals("byte")) return Byte.class.getName();
			if(sName.equals("boolean")) return Boolean.class.getName();
			if(sName.equals("double")) return Double.class.getName();
		}

		return c.getName();
	}


	public String getErrorSource()
	{
		return "bw::"+this.getClass().getName();
	}

	public String getErrorUser()
	{
		return pmaSystem.SYSTEM_ACCOUNT;
	}
}
