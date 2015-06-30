package puakma.util.math.formulacalculator;

import java.util.ArrayList;

/**
 * @author srahman
 * Just used for testing. Delete later.
 * Use doAssert with Expected results. The rest are taken care of.
 */
public class TestingFC {

	private static int iTestCount=0;
	private static int iErrorCount=0;
	private static FormulaCalculator fc;
	private static ArrayList arrErrorNumbers = new ArrayList(5);
	
	//this controls whether to print the tests that PASSED. Failed will be reported any way.  
	private static boolean m_bPrintPasses = false; 
	
	
	/*final public static void main(String[] args) 
	{
		m_bPrintPasses = true;
		fc = new FormulaCalculator();
		try 
		{
			String sFormula = "if({A}>0,if({B}>0,{C},{D}),0)";
			fc.setVariable("A", 1);
			fc.setVariable("B", 1);
			fc.setVariable("C", 3);
			fc.setVariable("D", 4);
			fc.setFormula(sFormula);
			doAssert(3);
		} 
		catch (InvalidVariableException e) {
			e.printStackTrace();
		}

	}
	*/
	
	/**
	 * @param args
	 */
	final public static void main(String[] args) 
	{
		fc = new FormulaCalculator();
		try 
		{
			String sFormula = "-(7-9)";
			///*
			fc.setFormula(sFormula);
			doAssert(2);

			long lDiameter = 50;
			fc.setVariable("diameter", lDiameter);
			fc.setFormula("CircleArea", "PI()*{Radius}^2");
			fc.setVariable("Radius", "{diameter}/2");
			fc.setFormula("Circumference", "pi()*{Diameter}");
			fc.evaluate("CircleArea");
			doAssert(1963.4954084936207);
			fc.evaluate("Circumference");
			doAssert(157.07963267948966);

			String a = "5 + 6 - (2e2 + 34)+ (5*6+(24--6)+2*(4e.5/5))";
			String b = "5";

			sFormula = "{A}+2+"+1000000000000f+"^.5+"+fc.POWER(100, .5)+"/"+fc.SQUARE_ROOT(64)+"+{D}";
			fc.setFormula(sFormula);
			fc.setVariable("A", a);
			fc.setVariable("B", b);
			fc.setVariable("C", "{B}+{A}");
			fc.setVariable("D", "{B}+{A}+{C}");
			doAssert(999149.0710672312);

			sFormula = "-(50)";
			fc.setFormula(sFormula);
			doAssert(-50);

			fc.setFormula("RAND()");
			System.out.println("Random number: " + fc.getResult());

			// *************************************** Test nested functions
			//*/
			fc.removeVariable(null);
			sFormula = "AVERAGE(SIN({A}), 1, 0)";

			fc.setFormula(sFormula);
			fc.setVariable("A", "AVERAGE(30, 60, 0)");
			doAssert(0.5);

			fc.removeVariable(null);
			sFormula = "LCM(6, 14, 22, 21)";
			///*
			fc.setFormula(sFormula);
			doAssert(462);

			fc.removeVariable(null);
			sFormula = "2%5+3";

			fc.setFormula(sFormula);
			doAssert(5);
			
			
			fc.removeVariable(null);
			sFormula = "IF(ROUND({A},2)=0, 0, {A}-{B})";
			fc.setVariable("A", 0.0000029388);
			fc.setVariable("B", 35000);
			fc.setFormula(sFormula);
			doAssert(0);
			
/*
			fc.removeVariable(null);
			sFormula = "ASIN(.5)";
			fc.setFormula(sFormula);
			doAssert(30);
*/

			// *************************************** Test variable multiply
			sFormula = "{A}*1.21";
			fc.setFormula(sFormula);
			for(int i=0; i<10; i++)
			{
				fc.removeVariable(null);
				fc.setVariable("a", 100);
				doAssert(121);
			}
			//*
			fc.removeVariable(null);
			fc.setFormula("({a}/{b})-1");
			fc.setVariable("a", 5);
			fc.setVariable("b", 0);
			fc.replaceDivideByZeroResult(0);
			doAssert(-1);

			//*/
			
			
			fc.removeVariable(null);
			sFormula = "4>3"; 
			fc.setFormula(sFormula);
			doAssert(1);
			
			fc.removeVariable(null);
			sFormula = "4>=4"; 
			fc.setFormula(sFormula);
			doAssert(1);
			
			fc.removeVariable(null);
			sFormula = "3<4"; 
			fc.setFormula(sFormula);
			doAssert(1);
			
			fc.removeVariable(null);
			sFormula = "3<=3"; 
			fc.setFormula(sFormula);
			doAssert(1);
			
			fc.removeVariable(null);
			sFormula = "3!=4"; 
			fc.setFormula(sFormula);
			doAssert(1);
			
			fc.removeVariable(null);
			sFormula = "3=3"; 
			fc.setFormula(sFormula);
			doAssert(1);

			fc.removeVariable(null);
			sFormula = "IF(3<4, 33, 44)"; 
			fc.setFormula(sFormula);
			doAssert(33);
			
			fc.removeVariable(null);
			sFormula = "ROUND(2.348, 0)"; 
			fc.setFormula(sFormula);
			doAssert(2);
			
			fc.removeVariable(null);
			sFormula = "ROUNDUP(45.01, 0)"; 
			fc.setFormula(sFormula);
			doAssert(46);
			
			fc.removeVariable(null);
			sFormula = "ROUNDDOWN(45.99, 1)"; 
			fc.setFormula(sFormula);
			doAssert(45.9);
			

			System.out.println("\r\n\r\n");

			if(iErrorCount>0)
				System.out.println("======>>>> !!!!!!! " + iErrorCount + " ERRORS !!!!!!! Check "+arrErrorNumbers.toString()+" above for more details");
			else
				System.out.println("*** ALL "+iTestCount+" TESTS PASSED ***");

		} 
		catch (InvalidVariableException e) {
			e.printStackTrace();
		}

	}

	private static void doAssert(double dExpectedResult)
	{
		fc.clearLog();
		iTestCount++;
		double dResult = fc.getResult();
		boolean bPass =  dResult == dExpectedResult;
		String sResult = "FAIL["+iTestCount+"]: (" + dResult + "!=" + dExpectedResult + "): [ ";
		if(bPass) sResult = "PASS["+iTestCount+"]: [ ";
		
		if(m_bPrintPasses || !bPass) System.out.println(sResult + fc.getCurrentFormula() + " ] RESULT: " + dResult);

		if(!bPass) 
		{
			iErrorCount++;
			System.out.println("Error log:****************\r\n"+fc.toString());
			arrErrorNumbers.add(""+iTestCount);
		}
	}

}//class
