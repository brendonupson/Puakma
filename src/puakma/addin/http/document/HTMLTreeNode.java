package puakma.addin.http.document;

import java.util.Collections;
import java.util.Vector;

import puakma.util.Util;

/**
 * An object to create an HTML representation of a tree for grouping and categorizing items.
 * 
 * HTMLTreeNode root = new HTMLTreeNode("root");
		root.setAttributes("class=\"mktree\" id=\"tree1\"");
		root.addLeaf(new String[]{"1", "2", "3", "4", "5"}, null);
		root.addLeaf(new String[]{"1", "A"}, null);
		root.addLeaf(new String[]{"1", "B"}, null);
		root.addLeaf(new String[]{"1", "C"}, null);

		root.addLeaf(new String[]{"1", "2", "3", "D"}, null);
		root.addLeaf(new String[]{"1", "2", "3", "E"}, null);

		root.addLeaf(new String[]{"1", "2", "4", "F"}, null);

 * Alternative example:
  		HTMLTreeNode root = new HTMLTreeNode("root");
		HTMLTreeNode node1 = new HTMLTreeNode("Node1");
		node1.addChildNode(new HTMLTreeNode("Child of Node1"));
		node1.setAttributes("id=\"node1\"");

		root.addChildNode(node1);
		root.addChildNode(new HTMLTreeNode("Node2"));
		root.addChildNode(new HTMLTreeNode("Node3"));

 * @author bupson
 *
 */
public class HTMLTreeNode implements Comparable
{
	private Vector m_vChildNodes = new Vector();
	private String m_sNodeName = "";
	private HTMLTreeNode m_ParentNode = null; //use null to designate this is a root node
	private String m_sAttributes = null;
	private HTMLTreeNodeRenderer m_target = null;
	private boolean m_bIsLastLeaf = false;


	public HTMLTreeNode(String sNodeName)
	{
		m_sNodeName = sNodeName;
	}

	public HTMLTreeNode(String sNodeName, HTMLTreeNodeRenderer param)
	{
		m_sNodeName = sNodeName;
		m_target = param;
	}

	public void addChildNode(HTMLTreeNode tree)
	{
		tree.setParentNode(this);
		m_vChildNodes.add(tree);
	}


	public Vector getChildNodes()
	{
		return m_vChildNodes;
	}

	public void setRenderer(HTMLTreeNodeRenderer param)
	{		
		m_target = param;
	}

	public void setParentNode(HTMLTreeNode parent)
	{
		m_ParentNode = parent;
	}

	public void setAttributes(String sNewAttributes)
	{
		m_sAttributes = sNewAttributes;
	}

	private String renderNodeValue()
	{
		if(m_target!=null) return m_target.getHTML(this);
		return m_sNodeName;
	}

	/**
	 * Gets the HTML representation of the tree, using <ul> and <li> tags. 
	 * @param iRenderLevel use 0 for the root node, this controls the number of tabs for indenting. -1 disables tab indents
	 * @return
	 */
	public String getHTML(int iRenderLevel)
	{
		Collections.sort(m_vChildNodes);
		String sAttributes = "";
		if(m_sAttributes!=null && m_sAttributes.length()>0) sAttributes = ' ' + Util.trimSpaces(m_sAttributes);

		String sTabs = "";//+iRenderLevel;
		for(int i=0; i<iRenderLevel; i++) sTabs += '\t';

		StringBuilder sb = new StringBuilder(256);


		if(m_vChildNodes.size()>0 || m_ParentNode==null) 
		{
			if(m_ParentNode!=null) sb.append(sTabs + "<li"+sAttributes+">" + renderNodeValue());
			sb.append("<ul");
			if(m_ParentNode==null) sb.append(sAttributes);
			sb.append(">\r\n");
		}
		else
		{
			sb.append(sTabs + "<li" + sAttributes + ">");
			sb.append(renderNodeValue());
			sb.append("</li>\r\n");
		}

		for(int i=0; i<m_vChildNodes.size(); i++)
		{
			HTMLTreeNode node = (HTMLTreeNode)m_vChildNodes.get(i);
			sb.append(node.getHTML(iRenderLevel+1));
		}

		if(m_vChildNodes.size()>0 || m_ParentNode==null) 
		{
			sb.append(sTabs + "</ul>");
			if(m_ParentNode!=null) sb.append("</li>");	
			sb.append("\r\n");
		}
		return sb.toString();
	}


	public static void main(String args[])
	{
		HTMLTreeNode root = new HTMLTreeNode("root");
		root.setAttributes("class=\"mktree\" id=\"tree1\"");
		
		root.addLeaf(new String[]{"Faxes", "Sent by frank"}, null);
		root.addLeaf(new String[]{"Image of new report"}, null);
		root.addLeaf(new String[]{"Quotes", "Version 2.0.0 quote"}, null);
		root.addLeaf(new String[]{"pmx", "Initial release"}, null);
		root.addLeaf(new String[]{"pmx", "Release 2.0.0"}, null);
		root.addLeaf(new String[]{"Requested pdf report changes"}, null);
		root.addLeaf(new String[]{"Spec for text/title colourcode changes"}, null);
		/*root.addLeaf(new String[]{"1", "B"}, null);
		root.addLeaf(new String[]{"1", "C"}, null);
		root.addLeaf(new String[]{"1", "2", "3", "4", "5"}, null);
		
		
		root.addLeaf(new String[]{"1", "2", "3", "D"}, null);
		root.addLeaf(new String[]{"1", "2", "3", "E"}, null);

		root.addLeaf(new String[]{"1", "2", "4", "F"}, null);*/
		
		/*String sLeaves[] = new String[]{"1", "2", "4", "F"};		
		HTMLTreeNode desc = root.getDescendant(sLeaves);
		if(desc!=null)	System.out.println(desc.getPathToRoot("\\"));
		System.out.println("---------------------------");
*/
		/*HTMLTreeNode node1 = new HTMLTreeNode("Node1");
		node1.addChildNode(new HTMLTreeNode("Child of Node1"));
		node1.setAttributes("id=\"node1\"");

		root.addChildNode(node1);
		root.addChildNode(new HTMLTreeNode("Node2"));
		root.addChildNode(new HTMLTreeNode("Node3"));
		 */

		System.out.println(root.getHTML(0));
	}

	/**
	 * Each item in the array is added to a new level in the tree with the last not being attached to the renderer
	 * @param sLeafPath
	 * @param object
	 */
	public void addLeaf(String[] sLeafPath, HTMLTreeNodeRenderer object) 
	{
		if(sLeafPath==null) return;
		HTMLTreeNode node = this;
		for(int i=0; i<sLeafPath.length; i++)
		{
			HTMLTreeNode nodeChild = node.getChildByName(sLeafPath[i]);
			if(nodeChild==null) 
			{				
				nodeChild = new HTMLTreeNode(sLeafPath[i]);
				//if(i==sLeafPath.length-1) nodeChild.setRenderer(object);
				if(i==sLeafPath.length-1) nodeChild.setIsLastLeaf(true);
				nodeChild.setRenderer(object); //BJU 3.Feb.2015
				node.addChildNode(nodeChild);
			}
			node = nodeChild;
		}
	}

	public boolean isLastLeaf() 
	{
		return m_bIsLastLeaf;
	}
	
	public void setIsLastLeaf(boolean bIsLastLeaf) 
	{
		m_bIsLastLeaf = bIsLastLeaf;		
	}

	public HTMLTreeNode getChildByName(String sNodeName) 
	{
		for(int i=0; i<m_vChildNodes.size(); i++)
		{
			HTMLTreeNode node = (HTMLTreeNode)m_vChildNodes.get(i);
			if(node.getNodeName().equals(sNodeName)) return node;
		}

		return null;
	}
	
	public int getDepth()
	{
		int iDepth = 0;
		while(this.getParentNode()!=null)
		{
			iDepth++;
		}
		return iDepth;
	}

	/**
	 * Finds the first matching descendant. Each "leaf" corresponds to a node in the tree
	 * @param sLeaves
	 * @return
	 */
	public HTMLTreeNode getDescendant(String sLeaves[]) 
	{
		HTMLTreeNode currentnode = this;
		for(int k=0; k<sLeaves.length; k++)
		{
			String sNodeName = sLeaves[k];
			Vector vChildNodes = currentnode.getChildNodes();
			for(int i=0; i<vChildNodes.size(); i++)
			{
				HTMLTreeNode node = (HTMLTreeNode)vChildNodes.get(i);
				if(node.getNodeName().equals(sNodeName)) 
				{
					currentnode = node;
					if(k==(sLeaves.length-1)) return node;

				}
			}

		}
		return null;
	}
	
	/**
	 * Return the path from the current node back to the root node of this tree. The path will be returned
	 * as "\root\leve1\level2" where the current node is level2.
	 * @return
	 */
	public String getPathToRoot(String sSeparator)
	{
		String sReturn = "";
		if(sSeparator==null) sSeparator="\\";
		HTMLTreeNode currentNode = this;
		while(currentNode!=null)
		{
			HTMLTreeNode parentNode = currentNode.getParentNode();
			if(parentNode!=null) sReturn = sSeparator + currentNode.getNodeName() + sReturn;
			currentNode = parentNode;
		}
		
		return sReturn;
	}
	
	/**
	 * Find this node's parent. A null value means the root of the tree
	 * @return
	 */
	public HTMLTreeNode getParentNode()
	{
		return m_ParentNode;
	}

	/**
	 * 
	 * @return
	 */
	public String getNodeName() 
	{		
		return m_sNodeName;
	}

	/**
	 * Default sorting algorithm
	 * @param compareTo
	 * @return
	 */
	public int compareTo(Object obj) 
	{		
		if(!(obj instanceof HTMLTreeNode)) return 0;
		
		HTMLTreeNode compareTo = (HTMLTreeNode)obj;
		//if(this.hasChildren() && !compareTo.hasChildren()) return -1;
		//if(!this.hasChildren() && compareTo.hasChildren()) return 1;
		
		return this.m_sNodeName.toUpperCase().compareTo(compareTo.getNodeName().toUpperCase());
	}

	/**
	 * Determines if this node has child nodes
	 * @return true if one or more children are present
	 */
	public  boolean hasChildren() 
	{
		return m_vChildNodes.size()>0;
	}

	

}
