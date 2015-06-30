package puakma.addin.http;

import puakma.addin.pmaAddIn;
import puakma.addin.pmaAddInStatusLine;
import puakma.addin.http.document.DesignElement;

public class TESTER extends pmaAddIn 
{
	private pmaAddInStatusLine m_pStatus;

	public void pmaAddInMain()
    {
      setAddInName("TESTER");
      m_pStatus = createStatusLine();
      m_pStatus.setStatus("Starting...");
      m_pSystem.doInformation("TESTER.Startup", this);
      
      TornadoServerInstance tsi = TornadoServer.getInstance(m_pSystem);
      
      TornadoApplication ta = tsi.getTornadoApplication("/puakma.pma");
      ta.getDesignElement("403", DesignElement.DESIGN_TYPE_PAGE);
      ta.getDesignElement("404", DesignElement.DESIGN_TYPE_PAGE);
      ta.getDesignElement("Main", DesignElement.DESIGN_TYPE_PAGE);
      ta.getDesignElement("Main", DesignElement.DESIGN_TYPE_PAGE);
      ta.getDesignElement("XYZ", DesignElement.DESIGN_TYPE_PAGE);
      ta = tsi.getTornadoApplication("/gtest/clementine.pma");
      ta = tsi.getTornadoApplication("/system/webdesign.pma");
      ta = tsi.getTornadoApplication("/puakma.pma");
      ta = tsi.getTornadoApplication("/puakma/dbmaker.pma");
      System.out.println(tsi.toString());
      tsi.flushApplicationCache();
      System.out.println(">>>> FLUSH");
      System.out.println(tsi.toString());
      
      m_pSystem.doInformation("TESTER.Shutdown", this);
      removeStatusLine(m_pStatus);
    }
}
