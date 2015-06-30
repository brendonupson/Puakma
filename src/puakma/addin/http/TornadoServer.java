package puakma.addin.http;

import puakma.system.SystemContext;

public class TornadoServer 
{
	private static TornadoServerInstance m_ts = null;
	

	private TornadoServer(){}
	
	
	/**
	 * This method should be called once initially to create the static object. Your code
	 * should then make calls to getInstance() to return the reference.
	 * @param pSystem
	 * @return
	 */
	public static synchronized TornadoServerInstance getInstance(SystemContext pSystem)
	{	
		if(pSystem==null) return null;
	    if(m_ts==null)
	    {
	    	//important to clone, since if an addin is restarted, its systemcontext is destroyed
	    	//and our object here will have an invalid reference (dbpool closed)
	    	m_ts = new TornadoServerInstance((SystemContext) pSystem.clone());
	    }
	    return m_ts;
	}
	
	/**
	 * Call this method to return a reference to this object
	 * @return
	 */
	public static TornadoServerInstance getInstance()
	{	
	    return m_ts;
	}
}//class
