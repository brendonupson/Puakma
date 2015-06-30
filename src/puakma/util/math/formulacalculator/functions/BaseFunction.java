package puakma.util.math.formulacalculator.functions;
import java.util.Hashtable;

import puakma.util.math.formulacalculator.FormulaCalculator;
import puakma.util.math.formulacalculator.InvalidFormulaException;
import puakma.util.math.formulacalculator.InvalidVariableException;


/**
 * General Instructions to create an external function. 
 * NOTE: Any external function will be calculated first from left to right of the formula before calculating the
 * remaining of the formula. 
 * 
 * Rules (reasons for each are described below):
 * 1) All external functions' classes must be in upper case.
 * 2) They must all extend BaseFunctions
 * 3) They must all return their calculations' results inside getResult() method and return a double value or NaN.
 * 4) They must all use m_sBlock as their input formula string
 * 5) They must all use getVariableValue(sVariable) and setVariableValue(dValue) when using variables. 
 * 6) They should try to use FormulaCalculator object whenever they have a new formula inside their input formula string
 * 7) If they choose to use FormulaCalculator, they can use the newFormulaCalculator(sFormula).
 * 8) They can change the variables internally but this wont affect the variables in the main FormulaCalculator Object.
 * 	  If its required to change the variables globally then in their methods they should set the two booleans
 *    m_bVariablesChanged and m_bUpdateGlobalVariables to true.
 * 
 * Reasons (numbered according to the rules):
 * 1) Currently I only look for upper case class names
 * 2) Otherwise a Class cast exception will be thrown as all external functions are casted as BaseFunctions.
 *    If this exception occurs then a NaN will be returned and evaluate() will fail.
 * 3) getResults() is called on every external functions and the result is added to the variable 
 *    with a unique variable name. If any thing goes wrong inside it then it should return a NaN.
 * 4) m_sBlock is set from outside.
 * 5) This will make sure the variables are FormulaCalculator compliant.
 * 6) So if recursive functions are present then they don't have to worry about it. 
 *    The FormulaCalculatior will also check for validity.
 * 7) This way the new FormulaCalculator will have all the Variables of its parents just in case inside 
 *    their formula those variables are referred to. E.g. AVERAGE({A}, {B}, SIN({A}*{B})) 
 * 8) This allows them to parse a conditional statement and then add or change the variables globally.
 *    However deleting a variable is not needed since you can simply not use it inside. And if its used outside your
 *    conditional statement and the variable is invalid the formula will fail.  
 */

/**
 * @author Shahedur Rahman gone crazy
 * @version 1.2.0
 */
public abstract class BaseFunction 
{

	protected String m_sBlock = "";
	protected Hashtable m_htFunctionVariables = new Hashtable();
	protected boolean m_bVariablesChanged = false;
	protected boolean m_bUpdateGlobalVariables = false;

	public static final String PARAMETER_SEPERATOR = ",";
	protected double m_dExternalFunctionResult = Double.NaN;
	
	protected double m_dDenominator0Replacement = 0;
	protected double m_dDenominator0Result = Double.NaN;

	/**
	 * Gets called inside the FormulaCalculator object. May be meaningless if used from outside it. 
	 */
	public BaseFunction(){}

	/**
	 * Used in the FormulaCalculator object to get all the variables in case it needs to update the variables inside it.
	 * @return
	 */
	public final Hashtable getFunctionVariables()
	{
		return m_htFunctionVariables;
	}

	/**
	 * Used to set the parameters for the external functions.
	 * @param sBlock
	 */
	public final void setFunctionParameters(String sBlock){m_sBlock = sBlock;}

	/**
	 * Used to set the functions' variable list
	 * @param htVariables
	 */
	public final void setFunctionVariables(Hashtable htVariables)
	{
		m_htFunctionVariables = htVariables;
	}

	/**
	 * External functions can set their own variables in this way. Variables must be FormulaCalculator compliant.
	 * In external functions there is no need to set up a string formula in a variable as any formula shound be 
	 * parsed by FormulaCalculator before setting up. 
	 * @param sVariable
	 * @param dValue
	 * @throws InvalidVariableException
	 */
	protected final void setVariable(String sVariable, double dValue) throws InvalidVariableException
	{
		FormulaCalculator fc = new FormulaCalculator();
		fc.setVariable(sVariable, dValue);
		if(m_htFunctionVariables.containsKey(sVariable)) 
			m_htFunctionVariables.remove(sVariable);
		m_htFunctionVariables.put(sVariable, fc.getVariableValue(sVariable));
		m_bVariablesChanged = true;
	}

	/**
	 * Gets the value of the variable. Can be a Double or a String
	 * @param sVariable
	 * @return Object
	 */
	protected final Object getVariableValue(String sVariable)
	{
		if(sVariable!=null && sVariable.length()>0)
		{
			sVariable = sVariable.toLowerCase();
			if(m_htFunctionVariables.containsKey(sVariable))
				return m_htFunctionVariables.get(sVariable);
		}
		return null;
	}

	/**
	 * This way if a conditional statement can update the global variables.
	 * If this is false then all updating of the variables are visible within the conditional statement. 
	 * @return
	 */
	public final boolean updateGlobalVariables()
	{
		return m_bVariablesChanged && m_bUpdateGlobalVariables;
	}
	
	/**
	 * Sets division by zero Stuff.
	 * @param dDenominator
	 * @param dReplacement
	 */
	public void setDenominatorAndResultZero(double dDenominator, double dReplacement)
	{
		m_dDenominator0Replacement = dDenominator;
		m_dDenominator0Result = dReplacement;
	}
	
	/**
	 * Makes a new Formula calculator with the Maths expression 
	 * @param sBlock
	 * @return
	 */
	protected FormulaCalculator newFormulaCalculator(String sBlock)
	{
		FormulaCalculator fc = new FormulaCalculator();
		try 
		{
			fc = new FormulaCalculator(sBlock, m_htFunctionVariables);
			if(m_dDenominator0Replacement!=0) fc.replaceDenominatorZero(m_dDenominator0Replacement);
			else fc.replaceDivideByZeroResult(m_dDenominator0Result);
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		return fc;
	}

	public abstract double getResult() throws InvalidFormulaException;
}//class
