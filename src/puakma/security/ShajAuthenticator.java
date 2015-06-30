package puakma.security;

import com.cenqua.shaj.Shaj;

import puakma.system.SessionContext;
import puakma.system.X500Name;

/**
 * This authenticator is based on the Shaj code: http://opensource.cenqua.com/shaj/
 * Its purpose is to login a user from the operating system's table of usernames and
 * passwords, and resolve groups based on the OS.
 * 
 * Note that there are some modifications to be made to the underlying OS. A native 
 * library must be loaded and, on linux, some pam config adjustments made. See http://opensource.cenqua.com/shaj/api/com/cenqua/shaj/PAMAuthenticator.html
 * Basically: copy the library to the java shared library folder, then create a /etc/pam.d/shaj/ entry to
 * tell PAM how to access.
 * 
 * The username will be the login name, eg: a login of 'bupson' will result in a canonical name
 * of 'CN=bupson'
 * @author bupson
 *
 */
public class ShajAuthenticator extends pmaAuthenticator
{
	private String m_sShajDomain=null;
	
	/**
	 * Called when authenticator is loaded
	 */
	public void init()
    { 
		if(Shaj.init()) SysCtx.doInformation("Shaj Authenticator loaded", this);
		m_sShajDomain = SysCtx.getSystemProperty("ShajDomain");
    }
	
	/**
	 * 
	 */
	public LoginResult loginUser(String sUserName, String sPassword, String sAddress, String sUserAgent, String sAppURI)
    {
		LoginResult loginResult = new LoginResult();
		loginResult.ReturnCode = LoginResult.LOGIN_RESULT_INVALID_USER;
		
		if(Shaj.init()) 
		{
			//System.out.println("Checking "+sUserName + "/" + sPassword);
			if(Shaj.checkPassword(m_sShajDomain, sUserName, sPassword))
			{
				//System.out.println("C>> "+sUserName + " OK");
				X500Name nmUser = new X500Name(sUserName);
				loginResult.UserName = nmUser.getCanonicalName();
	            loginResult.FirstName = nmUser.getFirstName();
	            loginResult.LastName = nmUser.getLastName();
	            loginResult.ReturnCode = LoginResult.LOGIN_RESULT_SUCCESS;
			}
		}
		return loginResult;
    }
	
	/**
	 * 
	 */
	public boolean isUserInGroup(SessionContext sessCtx, String sGroupName, String sAppURI)
    {
		if(Shaj.init()) 
		{
			X500Name nmUser = new X500Name(sessCtx.getUserName());
			//System.out.println("Checking "+nmUser.getCommonName() + " in " + sGroupName);
			return Shaj.checkGroupMembership(null, nmUser.getCommonName(), sGroupName);
		}
		return false;
    }
	
}//class
