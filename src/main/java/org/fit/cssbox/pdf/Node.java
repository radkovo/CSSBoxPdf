/**
 * A class representing node in TREE and LIST data structures
 * 
 * @author Zbynek Cervinka
 */

package org.fit.cssbox.pdf;

import java.util.Vector;

import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.ListItemBox;
import org.fit.cssbox.layout.Rectangle;
import org.fit.cssbox.layout.ReplacedBox;
import org.fit.cssbox.layout.TextBox;
import org.fit.cssbox.layout.Box;

public class Node
{
    private Node nodeParent;
    private Vector<Node> nodeChildren = new Vector<Node>(8);

    private ElementBox elem;
    private TextBox text;
    private ReplacedBox box;
    private ListItemBox item;

    private float plusHeight, plusOffset;

    private int parentIDOfNoninsertedNode;
    private Node refToTreeEquivalent;

    /**
     * Constructor
     */
    public Node(Node nodeParent, ElementBox elem, TextBox text, ReplacedBox box, ListItemBox item, Node refToTreeEquivalent)
    {

        this.nodeParent = nodeParent;
        this.elem = elem;
        this.text = text;
        this.box = box;
        this.item = item;
        this.plusHeight = 0;
        this.plusOffset = 0;
        this.refToTreeEquivalent = refToTreeEquivalent;
        this.parentIDOfNoninsertedNode = -1;
    }

    /////////////////////////////////////////////////////////////////////
    // Node and data structure management functions
    /////////////////////////////////////////////////////////////////////

    /**
     * Returns the distance of stored ELEM/TEXT/BOX from top of the page
     */
    public Node getTreeEq()
    {
        return this.refToTreeEquivalent;
    }

    /**
     * Returns the parent ID
     */
    public int getParentIDOfNoninsertedNode()
    {
        return this.parentIDOfNoninsertedNode;
    }

    /**
     * Sets the variable parent ID
     */
    public void setParentIDOfNoninsertedNode(int parentID)
    {
        this.parentIDOfNoninsertedNode = parentID;
    }

    /**
     * Returns the reference to Parent Node of this Node
     */
    public Node getParentNode()
    {
        return this.nodeParent;
    }

    /**
     * Returns Vector with all children
     */
    public Vector<Node> getAllChildren()
    {

        if (this.nodeChildren.size() == 0) return null;
        // return a copy and original stays untouched
        return new Vector<Node>(this.nodeChildren);
    }

    /**
     * Insert new Node to right place in the children Vector
     */
    public Node insertNewNode(ElementBox elem, TextBox text, ReplacedBox box, ListItemBox item, Node refToTreeEquivalent)
    {

        // gets the distance of new element from the top of the page
        float y = 0;

        if (elem != null)
        {
            y = elem.getAbsoluteContentY();
        }
        else if (text != null)
        {
            y = text.getAbsoluteContentY();
        }
        else if (box != null)
        {
            Rectangle cb = ((Box) box).getAbsoluteContentBounds();
            y = cb.y;
        }
        else if (item != null)
        {
            y = item.getAbsoluteContentY();
        }
        Node newNode = new Node(this, elem, text, box, item, refToTreeEquivalent);

        // goes through child node and inserts new node to right place
        for (int x = 0; x < nodeChildren.size(); x++)
        {

            if (nodeChildren.elementAt(x).getElemY() > y)
            {
                nodeChildren.add(x, newNode);
                return newNode;
            }
        }
        nodeChildren.add(newNode);
        return newNode;
    }

    /**
     * Insert new Node to right place in the children Vector
     */
    public Node insertNewNode(Node newChild)
    {

        if (newChild == null) return null;

        // gets the distance of new element from the top of the page
        float y = newChild.getElemY();

        // goes through child node and inserts new node to right place
        for (int x = 0; x < nodeChildren.size(); x++)
        {

            if (nodeChildren.elementAt(x).getElemY() > y)
            {
                nodeChildren.add(x, newChild);
                return newChild;
            }
        }
        nodeChildren.add(newChild);
        return newChild;
    }

    /**
     * Returns the ID of ELEM/TEXT/BOX stored in this object
     */
    public int getID()
    {

        if (this.elem != null)
        {
            return elem.getOrder();
        }
        else if (this.text != null)
        {
            return text.getOrder();
        }
        else if (this.box != null)
        {

            Box converted_box = (Box) box;
            return converted_box.getOrder();
        }
        return -1;
    }

    /////////////////////////////////////////////////////////////////////
    // Functions for working with THIS node
    /////////////////////////////////////////////////////////////////////

    /**
     * Returns the distance of stored ELEM/TEXT/BOX from top of the page
     */
    public float getElemY()
    {

        if (this.elem != null)
        {
            return elem.getAbsoluteContentY();
        }
        else if (this.text != null)
        {
            return text.getAbsoluteContentY();
        }
        else if (this.box != null)
        {
            Rectangle cb = ((Box) box).getAbsoluteContentBounds();
            return cb.y;
        }
        return -1;
    }

    /**
     * Returns the distance of stored ELEM/TEXT/BOX from left side of the page
     */
    public float getElemX()
    {

        if (this.elem != null)
        {
            return elem.getAbsoluteContentX();
        }
        else if (this.text != null)
        {
            return text.getAbsoluteContentX();
        }
        else if (this.box != null)
        {
            Rectangle cb = ((Box) box).getAbsoluteContentBounds();
            return cb.x;
        }
        return -1;
    }

    /**
     * Returns height of stored ELEM/TEXT/BOX
     */
    public float getElemHeight()
    {

        if (this.elem != null)
        {
            return elem.getHeight();
        }
        else if (this.text != null)
        {
            return text.getHeight();
        }
        else if (this.box != null)
        {
            Rectangle cb = ((Box) box).getAbsoluteContentBounds();
            return cb.height;
        }
        return -1;
    }

    /**
     * Returns width of stored ELEM/TEXT/BOX
     */
    public float getElemWidth()
    {

        if (this.elem != null)
        {
            return elem.getWidth();
        }
        else if (this.text != null)
        {
            return text.getWidth();
        }
        else if (this.box != null)
        {
            Rectangle cb = ((Box) box).getAbsoluteContentBounds();
            return cb.width;
        }
        return -1;
    }

    /**
     * Returns true if this object stores ELEM
     */
    public boolean isElem()
    {
        return (this.elem != null);
    }

    /**
     * Returns true if this object stores TEXT
     */
    public boolean isText()
    {
        return (this.text != null);
    }

    /**
     * Returns true if this object stores BOX
     */
    public boolean isBox()
    {
        return (this.box != null);
    }

    /**
     * Returns true if this object stores BOX
     */
    public boolean isItem()
    {
        return (this.item != null);
    }

    /**
     * Returns the ELEM stored in this object
     */
    public ElementBox getElem()
    {
        return this.elem;
    }

    /**
     * Returns the TEXT stored in this object
     */
    public TextBox getText()
    {
        return this.text;
    }

    /**
     * Returns the BOX stored in this object
     */
    public ReplacedBox getBox()
    {
        return this.box;
    }

    /**
     * Returns the LIST ITEM stored in this object
     */
    public ListItemBox getItem()
    {
        return this.item;
    }

    /////////////////////////////////////////////////////////////////////
    // Functions for managing OFFSETs and HEIGHTs changes in THIS node
    /////////////////////////////////////////////////////////////////////

    /**
     * Adds an offset of this object
     */
    public void addPlusOffset(float newPlusOffset)
    {
        this.plusOffset += newPlusOffset;
    }

    /**
     * Returns the offset of this object
     */
    public float getPlusOffset()
    {
        return this.plusOffset;
    }

    /**
     * Add an increment to height for this object
     */
    public void addPlusHeight(float newPlusHeight)
    {
        this.plusHeight += newPlusHeight;
    }

    /**
     * Returns the increment to height for this object
     */
    public float getPlusHeight()
    {
        return this.plusHeight;
    }
}
