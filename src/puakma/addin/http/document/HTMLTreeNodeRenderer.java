package puakma.addin.http.document;

/**
 * Objects added to the HTMLTreeNode should know how to render themselves
 * @author bupson
 *
 */
public interface HTMLTreeNodeRenderer 
{
	public String getHTML(HTMLTreeNode treeNode);
}
