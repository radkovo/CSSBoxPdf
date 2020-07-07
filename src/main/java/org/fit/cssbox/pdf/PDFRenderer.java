/*
 * PDFRenderer.java
 * Copyright (c) 2015 Zbynek Cervinka
 *
 * PDFRenderer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * PDFRenderer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License
 * along with PDFRenderer. If not, see <http://www.gnu.org/licenses/>.
 *
 * Created on 10.5.2015, 22:29:10 by Zbynek Cervinka
 * Improved on 3.5.2019, 12:11:15 by Nguyen Hoang Duong
 * Reworked by Radek Burget
 */

package org.fit.cssbox.pdf;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType3;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.util.Matrix;
import org.fit.cssbox.awt.BackgroundBitmap;
import org.fit.cssbox.awt.BitmapImage;
import org.fit.cssbox.css.BackgroundDecoder;
import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.layout.BackgroundImage;
import org.fit.cssbox.layout.Box;
import org.fit.cssbox.layout.CSSDecoder;
import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.LengthSet;
import org.fit.cssbox.layout.ListItemBox;
import org.fit.cssbox.layout.Rectangle;
import org.fit.cssbox.layout.ReplacedBox;
import org.fit.cssbox.layout.ReplacedContent;
import org.fit.cssbox.layout.ReplacedImage;
import org.fit.cssbox.layout.TextBox;
import org.fit.cssbox.render.BackgroundImageImage;
import org.fit.cssbox.render.BoxRenderer;
import org.fit.cssbox.render.StructuredRenderer;
import org.w3c.dom.Element;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermFunction;

import cz.vutbr.web.css.TermLengthOrPercent;
import cz.vutbr.web.css.TermList;
import cz.vutbr.web.csskit.Color;
import cz.vutbr.web.css.TermFunction.Gradient.ColorStop;
import cz.vutbr.web.css.TermIdent;

/**
 * A renderer that produces an PDF output using PDFBox library. It can also
 * render some of advanced CSS3 properties.
 * 
 * @author Zbynek Cervinka
 * @author Nguyen Hoang Duong
 * @author Radek Burget
 */
public class PDFRenderer extends StructuredRenderer
{
    private float rootWidth;
    private float rootHeight;
    private PDDocument doc = null;
    
    private PDFOutput pdf;

    // top and bottom padding in pixels
    private float outputTopPadding;
    private float outputBottomPadding;
    
    // variables for rendering border radius
    private float ax, ay, bx, by, cx, cy, dx, dy, ex, ey, fx, fy, gx, gy, hx, hy;

    // page help variables
    //private float pageEnd;

    // TREE and LIST variables
    private Node rootNodeOfTree, recentNodeInTree, rootNodeOfList, recentNodeInList;
    private List<Node> nodesWithoutParent = new ArrayList<>(16);

    // break/avoid tables
    private List<float[]> breakTable = new ArrayList<>(2);
    private List<float[]> avoidTable = new ArrayList<>(2);


    public PDFRenderer(float rootWidth, float rootHeight, PDDocument doc)
    {
        this.rootWidth = rootWidth;
        this.rootHeight = rootHeight;
        this.doc = doc;
        this.pdf = new PDFOutput(rootWidth, rootHeight, doc);
        
        // sets the default top and bottom paddings for the output page
        outputTopPadding = 50;
        outputBottomPadding = 50;
    }

    @Override
    public void startElementContents(ElementBox elem)
    {
    }

    @Override
    public void finishElementContents(ElementBox elem)
    {
    }

    @Override
    public void renderMarker(ListItemBox elem)
    {
        // elem has no parent object - new Node will be root
        if (elem.getParent() == null)
        {
            // TREE
            rootNodeOfTree = new Node(null, null, null, null, elem, null);
            recentNodeInTree = rootNodeOfTree;
            // LIST
            rootNodeOfList = new Node(null, null, null, null, elem, rootNodeOfTree);
            recentNodeInList = rootNodeOfList;
        }
        // add new Node with reference to elem inside to TREE and to LIST to
        // right place
        else
        {
            // TREE
            Node targetNode = findNodeToInsert(elem.getParent().getOrder(), elem.getOrder());
            if (targetNode == null)
            {
                Node tmpNode = new Node(null, null, null, null, elem, null);
                tmpNode.setParentIDOfNoninsertedNode(elem.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(null, null, null, elem, null);
            }

            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(null, null, null, elem, recentNodeInTree);
        }
    }

    /**
     * Creates 2 new Nodes with reference to elem inside - one goes to LIST -
     * second goes to TREE
     */
    @Override
    public void renderElementBackground(ElementBox elem)
    {

        // elem has no parent object - new Node will be root
        if (elem.getParent() == null)
        {
            // TREE
            rootNodeOfTree = new Node(null, elem, null, null, null, null);
            recentNodeInTree = rootNodeOfTree;
            // LIST
            rootNodeOfList = new Node(null, elem, null, null, null, rootNodeOfTree);
            recentNodeInList = rootNodeOfList;
        }
        // add new Node with reference to elem inside to TREE and to LIST to
        // right place
        else
        {
            // TREE
            Node targetNode = findNodeToInsert(elem.getParent().getOrder(), elem.getOrder());
            if (targetNode == null)
            {
                Node tmpNode = new Node(null, elem, null, null, null, null);
                tmpNode.setParentIDOfNoninsertedNode(elem.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(elem, null, null, null, null);
            }

            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(elem, null, null, null, recentNodeInTree);
        }
    }

    /**
     * Creates 2 Nodes for LIST and TREE with the same content - TEXT to insert
     * Inserts object to right place
     */
    @Override
    public void renderTextContent(TextBox text)
    {
        // elem has no parent object - new Node will be root
        if (text.getParent() == null)
        {
            rootNodeOfTree = new Node(null, null, text, null, null, null);
            recentNodeInTree = rootNodeOfTree;
            rootNodeOfList = new Node(null, null, text, null, null, rootNodeOfTree);
            recentNodeInList = rootNodeOfList;
        }
        // add new Node with reference to elem inside to TREE and to LIST to
        // right place
        else
        {
            // TREE
            Node targetNode = findNodeToInsert(text.getParent().getOrder(), text.getOrder());
            if (targetNode == null)
            {
                Node tmpNode = new Node(null, null, text, null, null, null);
                tmpNode.setParentIDOfNoninsertedNode(text.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(null, text, null, null, null);
            }
            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(null, text, null, null, recentNodeInTree);
        }
    }

    /**
     * Creates 2 Nodes for LIST and TREE with the same content - BOX to insert
     * Inserts object to right place
     */
    @Override
    public void renderReplacedContent(ReplacedBox box)
    {

        Box convertedBox = (Box) box;
        // elem has no parent object - new Node will be root
        if (convertedBox.getParent() == null)
        {
            rootNodeOfTree = new Node(null, null, null, box, null, null);
            recentNodeInTree = rootNodeOfTree;
            rootNodeOfList = new Node(null, null, null, box, null, rootNodeOfTree);
            recentNodeInList = rootNodeOfList;
        }
        // add new Node with reference to elem inside to TREE and to LIST to
        // right place
        else
        {
            // TREE
            Node targetNode = findNodeToInsert(convertedBox.getParent().getOrder(), convertedBox.getOrder());

            if (targetNode == null)
            {
                Node tmpNode = new Node(null, null, null, box, null, null);
                tmpNode.setParentIDOfNoninsertedNode(convertedBox.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(null, null, box, null, null);
            }

            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(null, null, box, null, recentNodeInTree);
        }
    }

    /**
     * Processing the LIST and TREE data structures and writes data to OUTPUT
     * @throws IOException 
     */
    @Override
    public void close() throws IOException
    {
        // FINISH STEP B - process the nodesWithoutParent table and insert nodes
        // to TREE, if possible
        tryToInsertNotInsertedNodes();

        // STEP C - creates breakTable and avoidTable tables from data structure
        // and modifies them to contain only records that are not causing
        // conflicts
        // or unacceptable page break appearance
        createAndProcessBreakAndAvoidTables();

        // STEP D - makes paging in the TREE data structure according to data
        // in breakTable, avoidTable and the ends determined by the size of
        // document page
        makePaging();

        // STEP E - transforms all data from LIST data structure to Apache
        // PDFBox format
        // and using Apache PDFBox functions creates PDF document containing
        // transformed data
        makePDF();
    }

    //////////////////////////////////////////////////////////////////////
    // FUNCTIONS FULFILLING THE B - E STEP
    //////////////////////////////////////////////////////////////////////

    /**
     * FINISH STEP B - process the nodesWithoutParent table and insert nodes to
     * TREE, if possible
     */
    private void tryToInsertNotInsertedNodes()
    {
        // repeats until the table is empty
        while (nodesWithoutParent.size() > 0)
        {
            int sizeBefore = nodesWithoutParent.size();
            // goes through table and tries to find at least one record to add
            // to TREE
            for (int i = 0; i < nodesWithoutParent.size(); i++)
            {
                Node findMyParent = nodesWithoutParent.get(i);

                Node nodeToInsert = findNodeToInsert(findMyParent.getParentIDOfNoninsertedNode(), findMyParent.getID());
                // inserts the node, if parent node found in the tree
                if (nodeToInsert != null)
                {
                    nodeToInsert.insertNewNode(findMyParent);
                    nodesWithoutParent.remove(i);
                }
            }
            // if non of the records can not bee added to the TREE, it breaks
            // the cycle
            if (sizeBefore == nodesWithoutParent.size()) break;
        }
    }

    /**
     * STEP C - creates breakTable and avoidTable tables from data structure and
     * modifies them to contain only records that are not causing conflicts or
     * unacceptable page break appearance
     */
    private void createAndProcessBreakAndAvoidTables()
    {

        // creates and inserts records into breakTable and avoidTable
        // according to CSS property of elements in TREE
        createBreakAvoidTables();

        // deletes all items bigger than argument*pdf.getPageHeight() in
        // avoidTable
        deleteAvoidsBiggerThan(0.8f);

        // merges all records containing overlapping intervals, respects maximum
        // size of interval
        mergeAvoids(0.8f);
    }

    /**
     * STEP D - makes paging in the TREE data structure according to data in
     * breakTable, avoidTable and the ends determined by the size of document
     * page
     */
    private void makePaging()
    {
        float pageEnd = pdf.getPageHeight();
        while (breakTable.size() > 0 || pageEnd < rootHeight)
        {
            // continues breaking until the breakTable is not empty
            // or the end of page is below the content limit
            if (breakTable.size() == 0 || pageEnd < breakTable.get(0)[0])
            {
                // searches avoidTable for interval on the boundary between 2
                // pages
                boolean nalezeno = false;
                for (int i = 0; i < avoidTable.size(); i++)
                {
                    if (avoidTable.get(i)[0] < pageEnd && avoidTable.get(i)[1] > pageEnd)
                    {

                        makeBreakAt(avoidTable.get(i)[2]);
                        // sets new end of page according to height of the page
                        // in PDF document
                        pageEnd += pdf.getPageHeight();
                        nalezeno = true;
                    }
                }

                // not founded in avoidTable -> break normal
                if (!nalezeno)
                {
                    makeBreakAt(pageEnd);
                    // sets new end of page according to height of the page in
                    // PDF document
                    pageEnd += pdf.getPageHeight();
                }
            }
            // EOP is inside the interval in first record of breakTable
            else if (pageEnd > breakTable.get(0)[0] && pageEnd < breakTable.get(0)[1])
            {
                if (breakTable.get(0)[2] > pageEnd)
                {

                    makeBreakAt(pageEnd);
                    // sets new end of page according to height of the page in
                    // PDF document
                    pageEnd += pdf.getPageHeight();
                }
                else
                    makeBreakAt(breakTable.get(0)[2]);
                breakTable.remove(0);
            }
            // EOP is after the interval in first record of breakTable
            else
            {
                makeBreakAt(breakTable.get(0)[2]);
                breakTable.remove(0);
            }
        }
    }

    /**
     * STEP E - transforms all data from LIST data structure to Apache PDFBox
     * format and using Apache PDFBox functions creates PDF document containing
     * transformed data
     * @throws IOException 
     */
    private void makePDF() throws IOException
    {
        pdf.openStream();
        writeAllElementsToPDF();
        pdf.close();
    }

    /////////////////////////////////////////////////////////////////////
    // FUNCTIONS FOR WORKING WITH LIST AND TREE DATA STRUCTURE
    /////////////////////////////////////////////////////////////////////

    /**
     * Finds the parent node to insert actual node in TREE
     * 
     * @return the Node
     */
    private Node findNodeToInsert(int parentID, int myID)
    {
        // there is 2x ID=0 at the root of TREE - if my parents ID is zero and I
        // am not,
        // I have to insert to the second node with ID=0
        if (myID != 0 && parentID == 0)
            return rootNodeOfTree.getAllChildren().firstElement();

        // wanted node "to insert" is recent node
        if (recentNodeInTree.getID() == parentID)
            return recentNodeInTree;

        // wanted node "to insert" is parent node of recent node
        if (recentNodeInTree.getParentNode() != null && recentNodeInTree.getParentNode().getID() == parentID)
            return recentNodeInTree.getParentNode();

        // goes through whole tree
        List<Node> queueOpen = new ArrayList<>(16);
        queueOpen.add(rootNodeOfTree);
        while (queueOpen.size() > 0)
        {
            if (queueOpen.get(0).getID() == parentID)
                return queueOpen.get(0);

            List<Node> children = queueOpen.get(0).getAllChildren();
            if (children != null)
                queueOpen.addAll(children);
            queueOpen.remove(0);
        }
        return null;
    }

    /////////////////////////////////////////////////////////////////////
    // FUNCTIONS FOR WORKING WITH BREAKTABLE AND AVOIDTABLE
    /////////////////////////////////////////////////////////////////////

    /**
     * Goes throw TREE and inserts items into breakTable and into avoidTable
     */
    private void createBreakAvoidTables()
    {
        List<Node> queueOpen = new ArrayList<>(16);
        queueOpen.add(rootNodeOfTree);

        // goes through TREE
        while (queueOpen.size() > 0)
        {
            Node recNodeToInvestigate = queueOpen.get(0);
            queueOpen.remove(0);

            if (recNodeToInvestigate.isElem())
            {
                // gets CSS property for further classification
                NodeData style = recNodeToInvestigate.getElem().getStyle();
                CSSProperty.PageBreak pgbefore = style.getProperty("page-break-before");
                CSSProperty.PageBreak pgafter = style.getProperty("page-break-after");
                CSSProperty.PageBreakInside pginside = style.getProperty("page-break-inside");

                // element contains page-break-before: always; CSS property
                if (pgbefore != null && pgbefore == CSSProperty.PageBreak.ALWAYS)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    Node temp = getElementAbove(recNodeToInvestigate);
                    if (temp == null)
                        tableRec[0] = recNodeToInvestigate.getParentNode().getElemY()
                                + recNodeToInvestigate.getParentNode().getPlusOffset();
                    else
                        tableRec[0] = getLastBottom(temp);

                    // finds ends of the interval
                    tableRec[1] = getFirstTop(recNodeToInvestigate);

                    // finds the break place
                    tableRec[2] = recNodeToInvestigate.getElem().getAbsoluteContentY();

                    // inserts into breakTable
                    insertIntoTable(tableRec, breakTable);
                }

                // element contains page-break-after: always; CSS property
                if (pgafter != null && pgafter == CSSProperty.PageBreak.ALWAYS)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    tableRec[0] = getLastBottom(recNodeToInvestigate);

                    // finds ends of the interval
                    Node temp = getElementBelow(recNodeToInvestigate);
                    if (temp != null)
                    {
                        tableRec[1] = getFirstTop(temp);
                    }
                    else
                    {
                        tableRec[1] = recNodeToInvestigate.getElemY()
                                + recNodeToInvestigate.getElemHeight();
                    }

                    // finds the break place
                    tableRec[2] = recNodeToInvestigate.getElem().getAbsoluteContentY()
                            + recNodeToInvestigate.getElem().getHeight();

                    // inserts into breakTable
                    insertIntoTable(tableRec, breakTable);
                }

                // element contains page-break-before: avoid; CSS property
                if (pgbefore != null && pgbefore == CSSProperty.PageBreak.AVOID)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    Node temp = getElementAbove(recNodeToInvestigate);
                    if (temp != null)
                    {
                        tableRec[0] = getLastBottom(temp);
                    }
                    else
                    {
                        tableRec[0] = recNodeToInvestigate.getElemY();
                    }

                    // finds ends of the interval
                    tableRec[1] = getFirstTop(recNodeToInvestigate);

                    // finds the break place
                    tableRec[2] = tableRec[0] - 1;

                    // inserts into avoidTable
                    insertIntoTable(tableRec, avoidTable);
                }

                // element contains page-break-after: avoid; CSS property
                if (pgafter != null && pgafter == CSSProperty.PageBreak.AVOID)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    tableRec[0] = getLastBottom(recNodeToInvestigate);

                    // finds ends of the interval
                    Node temp = getElementBelow(recNodeToInvestigate);
                    if (temp != null)
                    {
                        tableRec[1] = getFirstTop(temp);
                    }
                    else
                    {
                        tableRec[1] = recNodeToInvestigate.getElemY()
                                + recNodeToInvestigate.getElemHeight();
                    }

                    // finds the break place
                    tableRec[2] = tableRec[0] - 1;

                    // inserts into avoidTable
                    insertIntoTable(tableRec, avoidTable);
                }

                // element contains page-break-inside: avoid; CSS property
                if (pginside != null && pginside == CSSProperty.PageBreakInside.AVOID)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    tableRec[0] = recNodeToInvestigate.getElem().getAbsoluteContentY() - 1;

                    // finds ends of the interval
                    tableRec[1] = tableRec[0] + recNodeToInvestigate.getElem().getHeight() + 1;

                    // finds the break place
                    tableRec[2] = tableRec[0] - 1;

                    // inserts into avoidTable
                    insertIntoTable(tableRec, avoidTable);
                }
            }

            // adds all children to the end of queueOpen
            if (recNodeToInvestigate.getAllChildren() != null)
            {
                queueOpen.addAll(recNodeToInvestigate.getAllChildren());
            }
        }
    }

    /**
     * Inserts record into breakTable or into avoidTable
     */
    private void insertIntoTable(float[] tableRec, List<float[]> table)
    {

        boolean inserted = false;
        for (int i = 0; i < table.size(); i++)
        {
            if (tableRec[0] < table.get(i)[0])
            {
                table.add(i, tableRec);
                inserted = true;
                break;
            }
        }
        if (!inserted)
            table.add(tableRec);
    }

    /**
     * Deletes items in Avoid table that are higher than "biggerThan" of the
     * page height
     */
    private void deleteAvoidsBiggerThan(float biggerThan)
    {
        for (int i = 0; i < avoidTable.size(); i++)
        {
            if (avoidTable.get(i)[1] - avoidTable.get(i)[0] > biggerThan * pdf.getPageHeight())
                avoidTable.remove(i);
        }
    }

    /**
     * Merges avoid interval that are overlapping
     */
    private void mergeAvoids(float biggerThan)
    {
        // goes through table
        for (int i = 1; i < avoidTable.size(); i++)
        {
            // tests if intervals in records are overlapping
            if (avoidTable.get(i - 1)[1] > avoidTable.get(i)[0])
            {
                // tests size of interval if it is not larger than allowed
                if (avoidTable.get(i)[1] - avoidTable.get(i - 1)[0] > biggerThan * pdf.getPageHeight())
                {
                    avoidTable.remove(i);
                    i--;
                }
                // merges overlapping records
                else
                {
                    if (avoidTable.get(i - 1)[1] < avoidTable.get(i)[1])
                        avoidTable.get(i - 1)[1] = avoidTable.get(i)[1];
                    avoidTable.remove(i);
                    i--;
                }
            }
        }
    }

    /**
     * Updates all tables by moving all break/avoid lines
     */
    private void updateTables(float moveBy)
    {
        // moves all records in breakTable
        for (int i = 0; i < breakTable.size(); i++)
        {
            breakTable.get(i)[0] += moveBy;
            breakTable.get(i)[1] += moveBy;
            breakTable.get(i)[2] += moveBy;
        }
        // moves all records in avoidTable
        for (int i = 0; i < avoidTable.size(); i++)
        {
            avoidTable.get(i)[0] += moveBy;
            avoidTable.get(i)[1] += moveBy;
            avoidTable.get(i)[2] += moveBy;
        }
    }

    /////////////////////////////////////////////////////////////////////
    // FUNCTIONS FOR MAKING PAGING
    /////////////////////////////////////////////////////////////////////

    /**
     * Finds the element above element
     * 
     * @return the Node
     */
    private Node getElementAbove(Node recentNode)
    {
        if (recentNode == null)
            return null;

        Node nParent = recentNode.getParentNode();
        if (nParent == null)
            return null;

        List<Node> nChildren = nParent.getAllChildren();
        if (nChildren == null)
            return null;

        Node nodeX = null;
        // goes through whole TREE
        while (nChildren.size() > 0)
        {
            Node temp = nChildren.get(0);
            nChildren.remove(0);

            // if recent child's ID is equal to original nod's ID - continue
            if (recentNode.getID() == temp.getID())
                continue;

            // if the child is not above - continue
            if (temp.getElemY() + temp.getPlusOffset() + temp.getElemHeight()
                    + temp.getPlusHeight() > recentNode.getElemY() + recentNode.getPlusOffset())
                continue;

            if (nodeX == null)
                nodeX = temp;
            else if (nodeX.getElemY() + nodeX.getPlusOffset() + nodeX.getElemHeight()
                    + nodeX.getPlusHeight() <= temp.getElemY() + temp.getPlusOffset()
                            + temp.getElemHeight() + temp.getPlusHeight())
            {
                nodeX = temp;
            }
        }
        return nodeX;
    }

    /**
     * Finds the element below element
     * 
     * @return the Node
     */
    private Node getElementBelow(Node recentNode)
    {
        if (recentNode == null)
            return null;
        // gets Vector of all parents children (including the node itself)
        Node nParent = recentNode.getParentNode();
        if (nParent == null)
            return null;
        List<Node> nChildren = nParent.getAllChildren();

        if (nChildren == null)
            return null;
        Node wantedNode = null;

        // goes through all children and search for node below the node given
        while (nChildren.size() > 0)
        {
            // gets first element from Vector
            Node temp = nChildren.get(0);
            nChildren.remove(0);

            // continues if recent node is the same as the original node
            if (recentNode.getID() == temp.getID())
                continue;

            // new candidate is not under recent node
            if (temp.getElemY() + temp.getPlusOffset() < recentNode.getElemY()
                    + recentNode.getElemHeight() + recentNode.getPlusHeight() + recentNode.getPlusOffset())
            {
                continue;
            }

            // wantedNode gets new reference if it has not one yet or the old
            // node
            // contains element with lower position then new candidate
            if (wantedNode == null)
                wantedNode = temp;
            else if (wantedNode.getElemY() + wantedNode.getPlusOffset() >= temp.getElemY()
                    + temp.getPlusOffset())
            {
                wantedNode = temp;
            }
        }
        return wantedNode;
    }

    /**
     * Finds the top of first child element in Node
     * 
     * @return the resized distance from top of the document or -1 for not null
     *         argument
     */
    private float getFirstTop(Node recentNode)
    {
        if (recentNode == null)
            return -1;

        List<Node> nChildren = recentNode.getAllChildren();
        if (nChildren == null)
            return recentNode.getElemY() + recentNode.getPlusOffset();

        float vysledekNeelem = Float.MAX_VALUE;
        float vysledekElem = Float.MAX_VALUE;

        // goes through subTREE and searches for first not-ElementBox element
        // - in case it doesn't contain any not-ElementBox element, it would
        // pick first ElementBox element
        List<Node> subTree = nChildren;

        while (subTree.size() > 0)
        {
            Node aktualni = subTree.get(0);
            subTree.remove(0);
            List<Node> subChildren = aktualni.getAllChildren();
            if (subChildren != null)
                subTree.addAll(subChildren);

            if (aktualni.isElem())
            {
                if (aktualni.getElemY() + aktualni.getPlusOffset() < vysledekElem)
                {
                    vysledekElem = aktualni.getElemY() + aktualni.getPlusOffset();
                }
            }
            else
            {
                if (aktualni.getElemY() + aktualni.getPlusOffset() < vysledekNeelem)
                {
                    vysledekNeelem = aktualni.getElemY() + aktualni.getPlusOffset();
                }
            }
        }
        if (vysledekNeelem != Float.MAX_VALUE)
            return vysledekNeelem;
        if (vysledekElem != Float.MAX_VALUE)
            return vysledekElem;

        return -2;
    }

    /**
     * Finds the bottom of last child element in Node
     * 
     * @return the resized distance from top of the document
     */
    private float getLastBottom(Node recentNode)
    {

        if (recentNode == null) return -1;
        List<Node> nChildren = recentNode.getAllChildren();
        if (nChildren == null)
            return recentNode.getElemY() + recentNode.getElemHeight()
                + recentNode.getPlusOffset() + recentNode.getPlusHeight();

        float vysledekNeelem = -Float.MAX_VALUE;
        float vysledekElem = -Float.MAX_VALUE;

        // goes through subTREE and searches for last not-ElementBox element
        // - in case it doesn't contain any not-ElementBox element, it would
        // pick last ElementBox element
        List<Node> subTree = nChildren;

        while (subTree.size() > 0)
        {
            Node aktualni = subTree.get(0);
            subTree.remove(0);
            List<Node> subChildren = aktualni.getAllChildren();
            if (subChildren != null) subTree.addAll(subChildren);

            if (aktualni.isElem())
            {
                if (aktualni.getElemY() + aktualni.getElemHeight() + aktualni.getPlusOffset()
                        + aktualni.getPlusHeight() > vysledekElem)
                    vysledekElem = aktualni.getElemY() + aktualni.getElemHeight()
                            + aktualni.getPlusOffset() + aktualni.getPlusHeight();
            }
            else
            {
                if (aktualni.getElemY() + aktualni.getElemHeight() + aktualni.getPlusOffset()
                        + aktualni.getPlusHeight() > vysledekNeelem)
                    vysledekNeelem = aktualni.getElemY() + aktualni.getElemHeight()
                            + aktualni.getPlusOffset() + aktualni.getPlusHeight();
            }
        }

        if (vysledekNeelem != -Float.MAX_VALUE)
            return vysledekNeelem;
        if (vysledekElem != -Float.MAX_VALUE)
            return vysledekElem;

        return -2;
    }

    /**
     * Makes end of page by moving elements in TREE according to line1
     */
    private void makeBreakAt(float line1)
    {
        if (line1 > rootHeight || line1 < 0)
            return;

        float spaceBetweenLines = 0;

        line1 -= outputBottomPadding;

        // goes through TREE end finds set of all non-ElementbBox elements which
        // are crossed by the line1
        // - picks one element from this set, which has the lowest distance from
        // the top of the page
        List<Node> myOpen = new ArrayList<>(2);
        myOpen.add(rootNodeOfTree);

        float line2 = line1;
        while (myOpen.size() > 0)
        {
            Node myRecentNode = myOpen.get(0);
            myOpen.remove(0);
            List<Node> myChildren = myRecentNode.getAllChildren();
            if (myChildren != null)
            {
                myOpen.addAll(myChildren);
            }

            float startOfTheElement = myRecentNode.getElemY() + myRecentNode.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode.getElemHeight()
                    + myRecentNode.getPlusHeight();

            // sets the line2 variable to match the top of the element from set
            // which has the lowest distance from the top of the document
            if (!myRecentNode.isElem())
            {
                if (startOfTheElement < line1 && endOfTheElement > line1)
                {
                    if (startOfTheElement < line2)
                    {
                        line2 = startOfTheElement;
                    }
                }
            }
        }

        // counts line3
        List<Node> myOpen2 = new ArrayList<>(2);
        myOpen2.add(rootNodeOfTree);

        float line3 = line2;
        while (myOpen2.size() > 0)
        {
            Node myRecentNode2 = myOpen2.get(0);
            myOpen2.remove(0);
            List<Node> myChildren2 = myRecentNode2.getAllChildren();
            if (myChildren2 != null)
            {
                myOpen2.addAll(myChildren2);
            }

            float startOfTheElement = myRecentNode2.getElemY() + myRecentNode2.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode2.getElemHeight()
                    + myRecentNode2.getPlusHeight();

            // counts the line3
            if (!myRecentNode2.isElem())
            {
                if (startOfTheElement < line2 && endOfTheElement > line2)
                {
                    if (startOfTheElement < line3)
                    {
                        line3 = startOfTheElement;
                    }
                }
            }
        }

        // counts distance between lines
        spaceBetweenLines = (float) (pdf.getPageHeight() * Math.ceil((line1 - 1) / pdf.getPageHeight()) - line3);

        // goes through TREE and increases height or moves element
        List<Node> myOpen3 = new ArrayList<>(2);
        myOpen3.add(rootNodeOfTree);

        while (myOpen3.size() > 0)
        {
            Node myRecentNode = myOpen3.get(0);
            myOpen3.remove(0);
            List<Node> myChildren = myRecentNode.getAllChildren();
            if (myChildren != null)
            {
                myOpen3.addAll(myChildren);
            }

            // counts start and end of the element
            float startOfTheElement = myRecentNode.getElemY() + myRecentNode.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode.getElemHeight()
                    + myRecentNode.getPlusHeight() - 10; //TODO

            // whole element if above the line2 - nothing happens
            if (endOfTheElement <= line2)
            {
            }
            // increases the height of element which:
            // - is ElementBox
            // - is crossed by the line2
            // - has got at least 2 children
            else if (myRecentNode.isElem() && myRecentNode.getElemY() + myRecentNode.getPlusOffset() < line2
                    && myRecentNode.getElemY() * myRecentNode.getPlusOffset()
                            + myRecentNode.getElemHeight() * myRecentNode.getPlusHeight() >= line2
                    && myRecentNode.getAllChildren() != null)
            {
                myRecentNode.addPlusHeight(outputTopPadding + spaceBetweenLines + outputBottomPadding);
            }
            // moves element in one of following cases:
            // - element is completely below the line2
            // - element is crossing line2 and is not ElementBox
            else
            {
                myRecentNode.addPlusOffset(outputTopPadding + spaceBetweenLines + outputBottomPadding);
            }
        }

        // updates height of the original document
        rootHeight += outputTopPadding + spaceBetweenLines + outputBottomPadding;
        pdf.setRootHeight(rootHeight);
        // updates values in all records in avoidTable and breakTable
        updateTables(outputTopPadding + spaceBetweenLines + outputBottomPadding);
    }

    ////////////////////////////////////////////////////////////////////////
    // INSERTING TO PDF
    //
    // - gets data describing each element from data structures and calls
    // appropriate function to transform and write data to PDF document
    ////////////////////////////////////////////////////////////////////////

    /**
     * Writing elements and their CSS property to pages in PDF
     * @throws IOException 
     */
    private void writeAllElementsToPDF() throws IOException
    {
        // goes through all pages in PDF and inserts to all elements to current page
        Filter pdfFilter = new Filter(null, 0, 0, 1.0f, 1.0f);
        BorderRadius borRad = new BorderRadius();
        boolean isBorderRad = false;
        boolean transf = false;
        for (int i = 0; i < pdf.getPageCount(); i++)
        {
            pdf.setCurrentPage(i);

            List<Node> elementsToWriteToPDF = new ArrayList<>(2);
            elementsToWriteToPDF.add(rootNodeOfList);
            while (elementsToWriteToPDF.size() > 0)
            {
                // get first element from Vector
                Node currentNode = elementsToWriteToPDF.get(0);
                elementsToWriteToPDF.remove(0);

                // get all children of recentNode and add to
                // elementsToWriteToPDF
                List<Node> allChildren = currentNode.getAllChildren();
                if (allChildren != null) elementsToWriteToPDF.addAll(allChildren);

                // inserts elem data to PDF
                if (currentNode.isElem())
                {
                    ElementBox elem = currentNode.getElem();
                    if (insertTransform(currentNode, elem, i, transf)) // if elemen has transform property and succesfully inserted
                    {
                        transf = true;
                    }
                    else
                    {
                        if (transf)
                            pdf.restoreGraphicsState();
                        transf = false;
                    }

                    boolean radialGrad = false;
                    boolean linearGrad = false;
                    Matrix radMatrix = new Matrix();
                    PDShadingType3 shading = null;

                    if (elem.isBlock() || elem.isReplaced())
                    {
                        // filter
                        CSSDecoder dec = new CSSDecoder(elem.getVisualContext());
                        Rectangle bounds = elem.getAbsoluteBorderBounds();
                        CSSProperty.Filter filter = elem.getStyle().getProperty("filter");
                        if (filter == CSSProperty.Filter.list_values)
                        {
                            pdfFilter = createFilter(elem);
                        }

                        // border-radius
                        TermList value1 = elem.getStyle().getValue(TermList.class, "border-top-right-radius");
                        TermList value2 = elem.getStyle().getValue(TermList.class, "border-top-left-radius");
                        TermList value3 = elem.getStyle().getValue(TermList.class, "border-bottom-right-radius");
                        TermList value4 = elem.getStyle().getValue(TermList.class, "border-bottom-left-radius");
                        if (value1 != null || value2 != null || value3 != null || value4 != null)
                        {
                            isBorderRad = true;
                            borRad.setCornerRadius(value2, value1, value4, value3, elem);
                        }
                        else
                        {
                            isBorderRad = false;
                            borRad = new BorderRadius(); // calculating corner radiuses
                        }
                        
                    }

                    BackgroundDecoder bg = findBackgroundSource(elem);
                    if (bg != null)
                    {
                        // color background
                        if (bg.getBgcolor() != null) 
                        {
                        }
                    }
                    
                    // draws colored background
                    if (!isBorderRad)
                        drawBgToElem(elem, i, currentNode.getTreeEq().getPlusOffset(),
                            currentNode.getTreeEq().getPlusHeight(), radialGrad, linearGrad, shading, radMatrix);

                    // draws background image
                    /*if (elem.getBackgroundImages() != null && elem.getBackgroundImages().size() > 0)
                    {
                        insertBgImg(elem, i, currentNode.getTreeEq().getPlusOffset(),
                                currentNode.getTreeEq().getPlusHeight(), pdfFilter, isBorderRad, borRad);
                    }*/

                    // draws border
                    drawBorder(elem, i, currentNode.getTreeEq().getPlusOffset(),
                            currentNode.getTreeEq().getPlusHeight(), isBorderRad, borRad);
                }

                // inserts text to PDF
                if (currentNode.isText())
                {
                    // draws the text if it is not overlapping the parent
                    // element more then 60 %
                    // on the right side
                    Node parent = currentNode.getTreeEq().getParentNode().getParentNode();
                    float parentRightEndOfElement = (parent.getElemX() + parent.getElemWidth());
                    float recentRightEndOfElement = (currentNode.getElemX() + currentNode.getElemWidth());
                    float widthRecentElem = currentNode.getElemWidth();

                    if (parentRightEndOfElement - recentRightEndOfElement > -widthRecentElem * 0.6)
                    {
                        TextBox text = currentNode.getText();
                        if (text.isEmpty() || !text.isVisible() || !text.isDeclaredVisible() || !text.isDisplayed())
                            continue;
                        insertText(text, i, currentNode.getTreeEq().getPlusOffset(),
                                currentNode.getTreeEq().getPlusHeight());
                    }
                }

                // inserts box data to PDF
                if (currentNode.isBox())
                {
                    ReplacedBox box = currentNode.getBox();
                    insertImg(box, i, currentNode.getTreeEq().getPlusOffset(), currentNode.getTreeEq().getPlusHeight(),
                            pdfFilter, isBorderRad, borRad);
                }
                
                // inserts list markers
                if (currentNode.isItem())
                {
                    insertMarker(currentNode.getItem(), i, currentNode.getTreeEq().getPlusOffset(), currentNode.getTreeEq().getPlusHeight());
                }
            }
        }
    }

    /**
     * Creates a filter structure based on the element style.
     * @param elem the element
     * @return a filter structure
     */
    private Filter createFilter(ElementBox elem)
    {
        TermList values = elem.getStyle().getValue(TermList.class, "filter");
        int n = 0;
        String[] filterType = new String[10];
        float invert = 0;
        float grayscale = 0;
        float opacity = 1;
        float brightness = 1;
        for (Term<?> term : values)
        {
            if (term instanceof TermFunction.Invert)
            {
                filterType[n] = "invert";
                invert = ((TermFunction.Invert) term).getAmount();
            }
            else if (term instanceof TermFunction.Brightness)
            {
                filterType[n] = "bright";
                brightness = ((TermFunction.Brightness) term).getAmount();
            }
            else if (term instanceof TermFunction.Opacity)
            {
                filterType[n] = "opacity";
                opacity = ((TermFunction.Opacity) term).getAmount();
            }
            else if (term instanceof TermFunction.Grayscale)
            {
                filterType[n] = "grayscale";
                grayscale = 1;
            }
            else
                filterType[n] = "";

            n++;
        }
        return new Filter(filterType, invert, grayscale, opacity, brightness);
    }

    /**
     * Transform the CSS coordinates to PDF coordinates.
     * 
     * @return an array, which contains new x-axis([0]) and y-axis([1])
     * @author Hoang Duong Nguyen
     * @param elem
     *            element, which has the CSS coordinates
     * @param x
     *            x-axis of the element
     * @param y
     *            y-axis of the element
     * @param plusOffset
     *            Offset of the element content
     * @param plusHeight
     *            height outside the element content
     * @param i
     *            number of the current page
     */
    private float[] transXYtoPDF(ElementBox elem, float x, float y, float plusOffset, float plusHeight, int i)
    {
        float[] xy = new float[2];

        float paddingBottom = elem.getPadding().bottom;
        float paddingLeft = elem.getPadding().left;

        xy[0] = elem.getAbsoluteContentX() * - paddingLeft + x;
        xy[1] = (pdf.getPageHeight() - (elem.getAbsoluteContentY() + plusOffset)
                + i * pdf.getPageHeight() - elem.getContentHeight() - plusHeight - paddingBottom) + y;
        return xy;
    }

    /**
     * Set the CSS3 transform properties values.
     * 
     * @param recentnode
     *            represents current element in the TREE structure
     * @param elem
     *            element, which contains transform property
     * @param i
     *            number of the current page
     * @param transf
     *            was there a previous transformation to restore after?
     * @returns {@code true} for some transformation inserted OK, {@code false}
     *          for no transformation found
     * @author Hoang Duong Nguyen
     * @throws IOException 
     */
    private boolean insertTransform(Node recentNode, ElementBox elem, int i, boolean transf) throws IOException
    {
        if (elem.isBlock() || elem.isReplaced())
        {
            CSSDecoder dec = new CSSDecoder(elem.getVisualContext());
            Rectangle bounds = elem.getAbsoluteContentBounds();
            // decode the origin
            float ox, oy;
            CSSProperty.TransformOrigin origin = elem.getStyle().getProperty("transform-origin");
            if (origin == CSSProperty.TransformOrigin.list_values)
            {
                TermList values = elem.getStyle().getValue(TermList.class, "transform-origin");
                ox = dec.getLength((TermLengthOrPercent) values.get(0), false, bounds.width / 2, 0, bounds.width);
                oy = dec.getLength((TermLengthOrPercent) values.get(1), false, bounds.height / 2, 0, bounds.height);
            }
            else
            {
                ox = bounds.width / 2;
                oy = bounds.height / 2;
            }

            float newXY[] = transXYtoPDF(elem, ox, oy, recentNode.getTreeEq().getPlusOffset(),
                    recentNode.getTreeEq().getPlusHeight(), i);
            ox = (int) newXY[0];
            oy = (int) newXY[1];

            // compute the transformation matrix
            CSSProperty.Transform trans = elem.getStyle().getProperty("transform");

            if (trans == CSSProperty.Transform.list_values)
            {
                boolean transformed = false;
                AffineTransform ret = new AffineTransform();
                TermList values = elem.getStyle().getValue(TermList.class, "transform");
                for (Term<?> term : values)
                {
                    if (term instanceof TermFunction.Rotate)
                    {
                        final double theta = dec.getAngle(((TermFunction.Rotate) term).getAngle());
                        ret.rotate(-theta);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.Scale)
                    {
                        float sx = ((TermFunction.Scale) term).getScaleX();
                        float sy = ((TermFunction.Scale) term).getScaleY();
                        ret.scale(sx, sy);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.ScaleX)
                    {
                        float sx = ((TermFunction.ScaleX) term).getScale();
                        ret.scale(sx, 1.0f);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.ScaleY)
                    {
                        float sy = ((TermFunction.ScaleY) term).getScale();
                        ret.scale(1.0f, sy);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.Skew)
                    {
                        double anx = dec.getAngle(((TermFunction.Skew) term).getSkewX());
                        double any = dec.getAngle(((TermFunction.Skew) term).getSkewY());
                        ret.shear(Math.tan(-anx), Math.tan(-any));
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.SkewX)
                    {
                        double anx = dec.getAngle(((TermFunction.SkewX) term).getSkew());
                        ret.shear(Math.tan(-anx), 0.0);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.SkewY)
                    {
                        double any = dec.getAngle(((TermFunction.SkewY) term).getSkew());
                        ret.shear(0.0, -any);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.Matrix)
                    {
                        float[] vals = new float[6];
                        vals = ((TermFunction.Matrix) term).getValues();
                        vals[1] = -vals[1]; // must be inverted because of
                                            // coordinate system in PDF
                        vals[2] = -vals[2];
                        vals[5] = -vals[5];
                        ret.concatenate(new AffineTransform(vals));
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.Translate)
                    {
                        float tx = dec.getLength(((TermFunction.Translate) term).getTranslateX(), false, 0, 0,
                                bounds.width);
                        float ty = dec.getLength(((TermFunction.Translate) term).getTranslateY(), false, 0, 0,
                                bounds.height);
                        ret.translate(tx, -ty); // - because of the different coordinate system in PDF
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.TranslateX)
                    {
                        float tx = dec.getLength(((TermFunction.TranslateX) term).getTranslate(), false, 0, 0,
                                bounds.width);
                        ret.translate(tx, 0.0);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.TranslateY)
                    {
                        float ty = dec.getLength(((TermFunction.TranslateY) term).getTranslate(), false, 0, 0,
                                bounds.height);
                        ret.translate(0.0, -ty);
                        transformed = true;
                    }
                }

                if (transformed)
                {
                    if (transf)
                        pdf.restoreGraphicsState();
                    pdf.saveGraphicsState();
                    pdf.addTransform(ret, ox, oy);
                    return true;
                }
                else
                    return false; // no transformation applied
            }
            else
                return false; // no transformation declared
        }
        else
            return false; // not applicable for this element type
    }

    /**
     * Draws image gained from <img> tag to OUTPUT
     * @throws IOException 
     */
    private void insertImg(ReplacedBox box, int i, float plusOffset, float plusHeight, Filter filter,
            boolean isBorderRad, BorderRadius borRad) throws IOException
    {
        ReplacedContent cont = box.getContentObj();
        if (cont != null)
        {
            if (cont instanceof ReplacedImage)
            {
                final ReplacedImage rimg = (ReplacedImage) cont;
                if (rimg.getImage() != null && rimg.getImage() instanceof BitmapImage)
                {
                    BufferedImage img = ((BitmapImage) rimg.getImage()).getBufferedImage();
                    float pageStart = i * pdf.getPageHeight();
                    float pageEnd = (i + 1) * pdf.getPageHeight();
    
                    Rectangle cb = ((Box) box).getAbsoluteContentBounds();
                    if (img != null && cb.y < pageEnd
                            && (cb.y + img.getHeight()) + plusHeight + plusOffset > pageStart)
                    {
                        img = filter.filterImg(img);
                        // calculates resized coordinates in CSSBox form
                        float startX = cb.x;
                        float startY = (cb.y + plusOffset + plusHeight) - i * pdf.getPageHeight(); // y position in the page
                        float width = (float) cb.getWidth();
                        float height = (float) cb.getHeight() + plusHeight;
                        if (isBorderRad)
                        { // if border radius is set
                            float radiusX = Math.max(Math.max(borRad.topLeftX, borRad.topRightX),
                                    Math.max(borRad.botLeftX, borRad.botRightX));
                            float radiusY = Math.max(Math.max(borRad.topLeftY, borRad.topRightY),
                                    Math.max(borRad.botLeftY, borRad.botRightY));
                            img = makeImgRadiusCorner(img, radiusX, radiusY);
                        }
                        // inserts image
                        pdf.insertImage(img, startX, startY, width, height);
                    }
                }
            }
        }
    }

    /**
     * Draws element background image to OUTPUT
     * @throws IOException 
     */
    private void insertBgImg(ElementBox elem, int i, float plusOffset, float plusHeight, Filter filter,
            boolean isBorderRad, BorderRadius borRad) throws IOException
    {
        /*if (elem.getBackgroundImages() != null)
        {
            final BackgroundBitmap bitmap = new BackgroundBitmap(elem);
            for (BackgroundImage img : elem.getBackgroundImages())
            {
                if (img instanceof BackgroundImageImage)
                {
                    bitmap.addBackgroundImage((BackgroundImageImage) img);
                }
            }
            if (bitmap.getBufferedImage() != null)
            {
                //final Rectangle bg = elem.getAbsoluteBorderBounds();
                //g.drawImage(bitmap.getBufferedImage(), Math.round(bg.x), Math.round(bg.y), null);
                BufferedImage img = bitmap.getBufferedImage();
                float pageStart = i * pdf.getPageHeight();
                float pageEnd = (i + 1) * pdf.getPageHeight();
                if (img != null && elem.getAbsoluteContentY() + plusOffset < pageEnd
                        && (elem.getAbsoluteContentY() + img.getHeight()) + plusOffset + plusHeight > pageStart)
                {
                    img = filter.filterImg(img);
                    // calculates resized coordinates in CSSBox form
                    Rectangle bb = elem.getAbsoluteBorderBounds();
                    float startX = bb.x;
                    float startY = bb.y + plusOffset - i * pdf.getPageHeight();
                    float width = img.getWidth();
                    float height = img.getHeight();

                    // correction of long backgrounds
                    if (height > 5 * plusHeight) height += plusHeight;

                    // if corner radius is set
                    if (isBorderRad)
                    { // if border radius is set
                        float radiusX = Math.max(Math.max(borRad.topLeftX, borRad.topRightX),
                                Math.max(borRad.botLeftX, borRad.botRightX));
                        float radiusY = Math.max(Math.max(borRad.topLeftY, borRad.topRightY),
                                Math.max(borRad.botLeftY, borRad.botRightY));
                        img = makeImgRadiusCorner(img, radiusX * 2, radiusY * 2);
                    }

                    // inserts image
                    insertImagePDFBox(img, startX, startY, width, height);
                }
            }
        }*/
    }

    /**
     * Draws border to OUTPUT
     * @throws IOException 
     * 
     * @returns 0 for inserted OK, -1 for exception occurs and 1 for border out
     *          of page
     */
    private void drawBorder(ElementBox elem, int i, float plusOffset, float plusHeight, boolean isBorderRad,
            BorderRadius borRad) throws IOException
    {
        final LengthSet border = elem.getBorder();
        if (border.top > 0 || border.right > 0 || border.bottom > 0 || border.right > 0 || isBorderRad)
        {
            // counts the distance between top of the document and the start/end
            // of the page
            final float pageStart = i * pdf.getPageHeight();
            final float pageEnd = (i + 1) * pdf.getPageHeight();

            // checks if border is not completely out of page
            if (elem.getAbsoluteContentY() + plusOffset > pageEnd
                    || (elem.getAbsoluteContentY() + plusOffset + elem.getContentHeight()) + plusHeight
                            + plusOffset < pageStart)
                return;

            // calculates resized X,Y coordinates in CSSBox form
            final float border_x = elem.getAbsoluteContentX();
            final float border_y = pdf.getPageHeight() - (elem.getAbsoluteContentY() + plusOffset)
                    + i * pdf.getPageHeight() - elem.getContentHeight() - plusHeight;

            // calculates the padding for each side
            final float paddingTop = elem.getPadding().top;
            final float paddingRight = elem.getPadding().right;
            final float paddingBottom = elem.getPadding().bottom;
            final float paddingLeft = elem.getPadding().left;

            // calculates the border size for each side
            final float borderTopSize = border.top;
            final float borderRightSize = border.right;
            final float borderBottomSize = border.bottom;
            final float borderLeftSize = border.left;

            // calculate the element size
            final float elemWidth = elem.getContentWidth();
            final float elemHeight = elem.getContentHeight() + plusHeight;

            float bX, bY, bWidth, bHeight;
            // calculating points for creating border radius
            if (isBorderRad)
            {
                ax = border_x + borRad.topLeftX - paddingLeft - borderLeftSize;
                ay = border_y + elem.getAbsoluteBorderBounds().height - paddingTop - borderTopSize;
                bx = border_x + elem.getAbsoluteBorderBounds().width - borRad.topRightX - paddingRight
                        - borderRightSize;
                by = ay;
                cx = border_x + elem.getAbsoluteBorderBounds().width - paddingRight - borderRightSize;
                cy = border_y + elem.getAbsoluteBorderBounds().height - borRad.topRightY - paddingTop
                        - borderTopSize;
                dx = cx;
                dy = border_y + borRad.botRightY - paddingBottom - borderBottomSize;
                ex = border_x + elem.getAbsoluteBorderBounds().width - borRad.botRightX - paddingRight
                        - borderRightSize;
                ey = border_y - paddingBottom - borderBottomSize;
                fx = border_x + borRad.botLeftX - paddingLeft - borderLeftSize;
                fy = ey;
                gx = border_x - paddingLeft - borderLeftSize;
                gy = border_y + borRad.botLeftY - paddingBottom - borderBottomSize;
                hx = gx;
                hy = border_y + elem.getAbsoluteBorderBounds().height - borRad.topLeftY - borderLeftSize
                        - paddingLeft;
                /*if (elem.getBgcolor() != null) drawBgInsideBorderRadius(elem,
                        borderTopSize == 0 ? 0 : borderTopSize + 1, borderRightSize == 0 ? 0 : borderRightSize + 1,
                        borderBottomSize == 0 ? 0 : borderBottomSize + 1, borderLeftSize == 0 ? 0 : borderLeftSize + 1,
                        borRad);*/
                drawBorderRadius(elem, borderTopSize, borderRightSize, borderBottomSize, borderLeftSize, borRad);
            }
            else
            {
                // left border
                if (borderLeftSize > 0)
                {
                    bX = border_x - borderLeftSize - paddingLeft;
                    bY = border_y - borderBottomSize - paddingBottom;
                    bWidth = borderLeftSize;
                    bHeight = elemHeight + borderTopSize + borderBottomSize + paddingTop + paddingBottom;
                    pdf.drawRectangle(borderLeftSize, getBorderColor(elem, "left"), bX, bY, bWidth, bHeight);
                }

                // right border
                if (borderRightSize > 0)
                {
                    bX = border_x + elemWidth + paddingRight;
                    bY = border_y - borderBottomSize - paddingBottom;
                    bWidth = borderRightSize;
                    bHeight = elemHeight + borderTopSize + borderBottomSize + paddingTop + paddingBottom;
                    pdf.drawRectangle(borderRightSize, getBorderColor(elem, "right"), bX, bY, bWidth, bHeight);
                }

                // top border
                if (borderTopSize > 0)
                {
                    bX = border_x - borderLeftSize - paddingLeft;
                    bY = border_y + elemHeight + paddingTop;
                    bWidth = elemWidth + borderLeftSize + borderRightSize + paddingLeft + paddingRight;
                    bHeight = borderTopSize;
                    pdf.drawRectangle(borderTopSize, getBorderColor(elem, "top"), bX, bY, bWidth, bHeight);
                }

                // bottom border
                if (borderBottomSize > 0)
                {
                    bX = border_x - borderLeftSize - paddingLeft;
                    bY = border_y - borderBottomSize - paddingBottom;
                    bWidth = elemWidth + borderLeftSize + borderRightSize + paddingLeft + paddingRight;
                    bHeight = borderBottomSize;
                    pdf.drawRectangle(borderBottomSize, getBorderColor(elem, "bottom"), bX, bY, bWidth, bHeight);
                }
            }
        }
    }

    /*
     * https://stackoverflow.com/questions/7603400/how-to-make-a-rounded-corner-
     * image-in-java
     */
    public BufferedImage makeImgRadiusCorner(BufferedImage image, float cornerRadiusX, float cornerRadiusY)
    {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();

        // This is what we want, but it only does hard-clipping, i.e. aliasing
        // g2.setClip(new RoundRectangle2D ...)

        // so instead fake soft-clipping by first drawing the desired clip shape
        // in fully opaque white with antialiasing enabled...
        g2.setComposite(AlphaComposite.Src);
        RenderingHints qualityHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        qualityHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHints(qualityHints);
        g2.setColor(java.awt.Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, cornerRadiusX, cornerRadiusY));

        // ... then compositing the image on top,
        // using the white shape from above as alpha source
        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(image, 0, 0, null);

        g2.dispose();

        return output;
    }

    /**
     * Draw the element background inside the border, it mays be deformed to the
     * shape of the border.
     * 
     * @author Hoang Duong Nguyen
     * @returns 0 for inserted OK, -1 for exception occurs
     * @param elem
     *            element with border-radius property
     * @param bTopSize
     *            line width of top border side
     * @param bRightSize
     *            line width of right border side
     * @param bBotSize
     *            line width of bottom border side
     * @param bLeftSize
     *            line width of left border side
     * @param borRad
     *            class, which contains border radiuses of each corner
     * @throws IOException 
     */
    private void drawBgInsideBorderRadius(ElementBox elem, float bTopSize, float bRightSize, float bBotSize,
            float bLeftSize, BorderRadius borRad) throws IOException
    {
        /*final float bezier = 0.551915024494f;
        content.setLineWidth(1);
        setNonStrokingColor(elem.getBgcolor());
        setStrokingColor(elem.getBgcolor());
        // drawing inside border
        content.moveTo(ax, ay - bTopSize);
        content.curveTo1((ax + bx) / 2, (ay + bTopSize + by - bTopSize) / 2, bx, by - bTopSize);
        content.curveTo(bx + bezier * borRad.topRightX, by - bTopSize, cx - bRightSize,
                cy + bezier * borRad.topRightY, cx - bRightSize, cy);
        content.curveTo1((cx - bRightSize + dx - bRightSize) / 2, (cy + dy) / 2, dx - bRightSize, dy);
        content.curveTo(dx - bRightSize, dy - bezier * borRad.botRightY, ex + bezier * borRad.botRightX,
                ey + bBotSize, ex, ey + bBotSize);
        content.curveTo1((ex + fx) / 2, (ey + bBotSize + fy + bBotSize) / 2, fx, fy + bBotSize);
        content.curveTo(fx - bezier * borRad.botLeftX, fy + bBotSize, gx + bLeftSize, gy - bezier * borRad.botLeftY,
                gx + bLeftSize, gy);
        content.curveTo1((gx + bLeftSize + hx + bLeftSize) / 2, (gy + hy) / 2, hx + bLeftSize, hy);
        content.curveTo(hx + bLeftSize, hy + bezier * borRad.topLeftY, ax - bezier * borRad.topLeftX, ay - bTopSize,
                ax, ay - bTopSize);
        content.fillAndStroke(); // insert background and colour of border*/
    }

    /**
     * Draw the border with rounded corner.
     * 
     * @author Hoang Duong Nguyen
     * @returns 0 for inserted OK, -1 for exception occurs
     * @param elem
     *            element with border-radius property
     * @param bTopSize
     *            line width of top border side
     * @param bRightSize
     *            line width of right border side
     * @param bBotSize
     *            line width of bottom border side
     * @param bLeftSize
     *            line width of left border side
     * @param borRad
     *            class, which contains border radiuses of each corner
     * @throws IOException 
     */
    private void drawBorderRadius(ElementBox elem, float bTopSize, float bRightSize, float bBotSize, float bLeftSize,
            BorderRadius borRad) throws IOException
    {
        float bezier = 0.551915024494f;
        if (bTopSize != 0)
        { // drawing top border
            content.setLineWidth(bTopSize);
            setStrokingColor(getBorderColor(elem, "top"));
            content.moveTo(ax, ay);
            content.curveTo1((ax + bx) / 2, (ay + by) / 2, bx, by);
            content.curveTo(bx + bezier * borRad.topRightX, by, cx, cy + bezier * borRad.topRightY, cx, cy);
            content.stroke();
        }
        if (bRightSize != 0)
        { // drawing right border
            content.setLineWidth(bRightSize);
            setStrokingColor(getBorderColor(elem, "right"));
            content.moveTo(cx, cy);
            content.curveTo1((cx + dx) / 2, (cy + dy) / 2, dx, dy);
            content.curveTo(dx, dy - bezier * borRad.botRightY, ex + bezier * borRad.botRightX, ey, ex, ey);
            content.stroke();
        }
        if (bBotSize != 0)
        { // drawing bot border
            content.setLineWidth(bBotSize);
            setStrokingColor(getBorderColor(elem, "bottom"));
            content.moveTo(ex, ey);
            content.curveTo1((ex + fx) / 2, (ey + fy) / 2, fx, fy);
            content.curveTo(fx - bezier * borRad.botLeftX, fy, gx, gy - bezier * borRad.botLeftY, gx, gy);
            content.stroke();
        }
        if (bLeftSize != 0)
        { // drawing left border
            content.setLineWidth(bLeftSize);
            setStrokingColor(getBorderColor(elem, "left"));
            content.moveTo(gx, gy);
            content.curveTo1((gx + hx) / 2, (gy + hy) / 2, hx, hy);
            content.curveTo(hx, hy + bezier * borRad.topLeftY, ax - bezier * borRad.topLeftX, ay, ax, ay);
            content.stroke();
        }
    }

    /**
     * Draws colored background to OUTPUT
     * @throws IOException 
     */
    private void drawBgToElem(ElementBox elem, int i, float plusOffset, float plusHeight, boolean radialGrad,
            boolean linearGrad, PDShadingType3 shading, Matrix matrix) throws IOException
    {
        // checks if any color available
        if ((elem.getBgcolor() == null) && (!radialGrad) && (!linearGrad)) 
            return;

        // for root element the background color will be painted to background
        // of whole page
        if ((elem.getParent() == null) && (!radialGrad) && (!linearGrad)) // TODO gradient in the whole page?
        {
            drawBgToWholePagePDFBox(elem.getBgcolor());
            return;
        }

        // calculates the start and the end of current page
        float pageStart = i * pdf.getPageHeight();
        float pageEnd = (i + 1) * pdf.getPageHeight();

        // checks if the element if completely out of page
        if (elem.getAbsoluteContentY() + plusOffset > pageEnd
                || (elem.getAbsoluteContentY() + elem.getContentHeight()) + plusOffset
                        + plusHeight < pageStart)
            return;

        // calculates the padding
        float paddingTop = elem.getPadding().top;
        float paddingRight = elem.getPadding().right;
        float paddingBottom = elem.getPadding().bottom;
        float paddingLeft = elem.getPadding().left;

        float border_x = elem.getAbsoluteContentX() - paddingLeft;
        float border_y = pdf.getPageHeight() - (elem.getAbsoluteContentY() + plusOffset)
                + i * pdf.getPageHeight() - elem.getContentHeight() - plusHeight - paddingBottom;
        if (radialGrad)
        { // if the background is radial gradient
            drawBgGrad(0, shading, border_x, border_y,
                    (elem.getContentWidth()) + paddingLeft + paddingRight,
                    elem.getContentHeight() + paddingTop + paddingBottom + plusHeight, matrix);

        }
        else if (linearGrad)
        { // if is linear gradient
            drawBgGrad(0, shading, border_x, border_y,
                    (elem.getContentWidth()) + paddingLeft + paddingRight,
                    elem.getContentHeight() + paddingTop + paddingBottom + plusHeight, matrix);

        }
        else
        {
            pdf.drawRectangle(0, elem.getBgcolor(), border_x, border_y,
                    (elem.getContentWidth()) + paddingLeft + paddingRight,
                    elem.getContentHeight() + paddingTop + paddingBottom + plusHeight);
        }
    }

    /**
     * Draws text to OUTPUT
     * @throws IOException 
     */
    private void insertText(TextBox text, int i, float plusOffset, float plusHeight) throws IOException
    {
        // counts the distance between top of the document and the start/end of
        // the page
        float pageStart = i * pdf.getPageHeight();
        float pageEnd = (i + 1) * pdf.getPageHeight();

        // checks if the whole text is out of the page
        if (text.getAbsoluteContentY() + plusOffset > pageEnd
                || (text.getAbsoluteContentY() + text.getHeight()) + plusOffset < pageStart)
            return;

        // gets data describing the text
        PDFVisualContext ctx = (PDFVisualContext) text.getVisualContext();
        float fontSize = CSSUnits.pixels(ctx.getFontInfo().getSize());
        boolean isBold = ctx.getFontInfo().isBold();
        boolean isUnderlined = text.getEfficientTextDecoration().contains(CSSProperty.TextDecoration.UNDERLINE);
        float letterSpacing = CSSUnits.pixels(ctx.getLetterSpacing());
        Color color = ctx.getColor();
        PDFont font = ctx.getFont();
        
        setNonStrokingColor(color);
        final float leading = 2f * fontSize;

        // compute the resized coordinates
        float startX = text.getAbsoluteContentX();
        float startY = (text.getAbsoluteContentY() + plusOffset) % pdf.getPageHeight();

        // write to PDF
        if (text.getWordSpacing() == null && text.getExtraWidth() == 0)
            writeTextPDFBox(startX, startY, text.getText(), font, fontSize, isUnderlined, isBold, letterSpacing, leading);
        else
            writeTextByWords(startX, startY, text, font, fontSize, isUnderlined, isBold, letterSpacing, leading);
        
        // render links
        String href = getLinkURL(text);
        if (href != null)
        {
            URL base = text.getViewport().getFactory().getBaseURL();
            URL url = null;
            try {
                url = new URL(base, href);
            } catch (MalformedURLException e) {
            }
            
            PDAnnotationLink link = new PDAnnotationLink();
            PDActionURI actionURI = new PDActionURI();
            actionURI.setURI(url != null ? url.toString() : href);
            link.setAction(actionURI);
            
            PDBorderStyleDictionary borderULine = new PDBorderStyleDictionary();
            borderULine.setStyle(PDBorderStyleDictionary.STYLE_UNDERLINE);
            borderULine.setWidth(0);
            link.setBorderStyle(borderULine);
            
            final float sy = pdf.getPageHeight() - startY;
            PDRectangle pdRectangle = new PDRectangle();
            pdRectangle.setLowerLeftX(startX);
            pdRectangle.setLowerLeftY(sy - text.getContentHeight());
            pdRectangle.setUpperRightX(startX + text.getContentWidth());
            pdRectangle.setUpperRightY(sy);
            link.setRectangle(pdRectangle);
            page.getAnnotations().add(link);
        }
    }

    private void insertMarker(ListItemBox item, int i, float plusOffset, float plusHeight) throws IOException
    {
        // counts the distance between top of the document and the start/end of
        // the page
        float pageStart = i * pdf.getPageHeight();
        float pageEnd = (i + 1) * pdf.getPageHeight();

        // checks if the whole text is out of the page
        if (item.getAbsoluteContentY() + plusOffset > pageEnd
                || (item.getAbsoluteContentY() + item.getHeight()) + plusOffset < pageStart)
            return;

        PDFVisualContext ctx = (PDFVisualContext) item.getVisualContext();
        final float fontSize = CSSUnits.pixels(ctx.getFontInfo().getSize());
        final float leading = 2f * fontSize;
        
        // write to PDF
        writeBullet(item, plusOffset, leading);
    }
    
    /**
     * 
     * @param lb
     * @param plusOffset
     * @param leading
     * @throws IOException
     */
    private void writeBullet(ListItemBox lb, float plusOffset, float leading) throws IOException
    {
        if (lb.hasVisibleBullet())
        {
            PDFVisualContext ctx = (PDFVisualContext) lb.getVisualContext();
            float x = (lb.getAbsoluteContentX() - 1.2f * ctx.getEm());
            float y = ((lb.getAbsoluteContentY() - 0.5f * ctx.getEm()) + plusOffset) % pdf.getPageHeight();
            y = pdf.getPageHeight() - y - leading;
            float r = (0.4f * ctx.getEm());
            final Color color = ctx.getColor();

            switch (lb.getListStyleType())
            {
                case "circle":
                    pdf.drawCircle(1.0f, color, x + r / 2, y - r / 2, r / 2, false);
                    break;
                case "square":
                    pdf.drawRectangle(1, color, x, y - r, r, r);
                    break;
                case "disc":
                    pdf.drawCircle(1.0f, color, x + r / 2, y - r / 2, r / 2, true);
                    break;
                default:
                    float fontSize = CSSUnits.pixels(ctx.getFontInfo().getSize());
                    boolean isBold = ctx.getFontInfo().isBold();
                    float letterSpacing = CSSUnits.pixels(ctx.getLetterSpacing());
                    PDFont font = ctx.getFont();
                    float xofs = ctx.stringWidth(lb.getMarkerText());
                    float tx = (lb.getAbsoluteContentX() - xofs);
                    float yofs = lb.getFirstInlineBoxBaseline() - ctx.getBaselineOffset(); //to align the font baseline with the item box baseline
                    float ty = ((lb.getAbsoluteContentY() + yofs) + plusOffset) % pdf.getPageHeight();
                    pdf.writeText(tx, ty, lb.getMarkerText(), font, fontSize, false, isBold, letterSpacing, leading);
                    break;
            }
        }
    }
    
    /**
     * Writes a text box to PDF by individual words while cosidering the word X offsets available in the text box.
     * @param x
     * @param y
     * @param text
     * @param font
     * @param fontSize
     * @param isUnderlined
     * @param isBold
     * @param letterSpacing
     * @param leading
     * @throws IOException 
     */
    private void writeTextByWords(float x, float y, TextBox text, PDFont font, float fontSize,
            boolean isUnderlined, boolean isBold, float letterSpacing, float leading) throws IOException
    {
        final String[] words = text.getText().split(" ");
        if (words.length > 0)
        {
            final float[][] offsets = text.getWordOffsets(words);
            for (int i = 0; i < words.length; i++)
                pdf.writeText(x + offsets[i][0], y, words[i], font, fontSize, isUnderlined, isBold, letterSpacing, leading);
        }
        else
            pdf.writeText(x, y, text.getText(), font, fontSize, isUnderlined, isBold, letterSpacing, leading);
    }
    
    //==================================================================================================
    
    /**
     * Returns color of border
     */
    private Color getBorderColor(ElementBox elem, String side)
    {
        Color clr = null;
        // gets the color value from CSS property
        TermColor tclr = elem.getStyle().getSpecifiedValue(TermColor.class, "border-" + side + "-color");
        CSSProperty.BorderStyle bst = elem.getStyle().getProperty("border-" + side + "-style");

        if (bst != CSSProperty.BorderStyle.HIDDEN && (tclr == null || !tclr.isTransparent()))
        {
            if (tclr != null)
            {
                clr = tclr.getValue();
            }
            if (clr == null)
            {
                if (elem.getBgcolor() != null)
                    clr = elem.getBgcolor();
                else
                    clr = new Color(255, 255, 255);
            }
        }
        else
        {
            clr = elem.getBgcolor();
        }
        if (clr == null)
            clr = new Color(255, 255, 255);
        return clr;
    }

    /**
     * Examines the given element and all its parent elements in order to find the "a" element.
     * @param e the child element to start with
     * @return the "a" element found or null if it is not present
     */
    private org.w3c.dom.Element findAnchorElement(org.w3c.dom.Element e)
    {
        final String href = e.getAttribute("href");
        if ("a".equalsIgnoreCase(e.getTagName().trim()) && href != null && !href.isEmpty())
            return e;
        else if (e.getParentNode() != null && e.getParentNode().getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
            return findAnchorElement((org.w3c.dom.Element) e.getParentNode());
        else
            return null;
    }

    private String getLinkURL(Element elem)
    {
        Element el = findAnchorElement(elem);
        if (el != null)
            return el.getAttribute("href").trim();
        else
            return null;
    }
    
    private String getLinkURL(ElementBox elem)
    {
        return getLinkURL(elem.getElement());
    }
    
    private String getLinkURL(TextBox text)
    {
        org.w3c.dom.Node parent = text.getNode().getParentNode();
        if (parent != null && parent instanceof Element)
            return getLinkURL((Element) parent);
        else
            return null;
    }
}
