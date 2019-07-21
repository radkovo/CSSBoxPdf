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
 */

package org.fit.cssbox.render;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType3;
import org.apache.pdfbox.util.Matrix;
import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.layout.BackgroundImage;
import org.fit.cssbox.layout.Box;
import org.fit.cssbox.layout.CSSDecoder;
import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.LengthSet;
import org.fit.cssbox.layout.ListItemBox;
import org.fit.cssbox.layout.ReplacedBox;
import org.fit.cssbox.layout.ReplacedContent;
import org.fit.cssbox.layout.ReplacedImage;
import org.fit.cssbox.layout.TextBox;
import org.fit.cssbox.layout.VisualContext;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermFunction;

import cz.vutbr.web.css.TermLengthOrPercent;
import cz.vutbr.web.css.TermList;
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
public class PDFRenderer implements BoxRenderer
{
    private float resCoef, rootHeight;

    private FontDB fontDB;

    // PDFBox variables
    private PDDocument doc = null;
    private PDPage page = null;
    private PDPageContentStream content = null;
    private PDRectangle pageFormat = null;

    // variables for rendering border radius
    private float ax, ay, bx, by, cx, cy, dx, dy, ex, ey, fx, fy, gx, gy, hx, hy;

    // page help variables
    private int pageCount;
    private float pageEnd;

    // TREE and LIST variables
    private Node rootNodeOfTree, recentNodeInTree, rootNodeOfList, recentNodeInList;
    private List<Node> nodesWithoutParent = new ArrayList<>(16);

    // break/avoid tables
    private List<float[]> breakTable = new ArrayList<>(2);
    private List<float[]> avoidTable = new ArrayList<>(2);

    // font variables
    private List<FontTableRecord> fontTable = new ArrayList<>(2);

    private class FontTableRecord
    {

        public String fontName;
        public Boolean isBold;
        public Boolean isItalic;
        public PDFont loadedFont;

        FontTableRecord(String fontName, Boolean isBold, Boolean isItalic, PDFont loadedFont)
        {
            this.fontName = fontName;
            this.isBold = isBold;
            this.isItalic = isItalic;
            this.loadedFont = loadedFont;
        }
    };

    // other variables
    private OutputStream pathToSave;
    private float outputTopPadding;
    private float outputBottomPadding;

    /**
     * Constructor
     * 
     * initialize the variables
     */
    public PDFRenderer(int rootWidth, int rootHeight, OutputStream out, String pageFormat)
    {
        this.rootHeight = rootHeight;
        this.pathToSave = out;
        this.pageCount = 0;

        switch (pageFormat)
        {
            case "A0":
                this.pageFormat = PDRectangle.A0;
                break;
            case "A1":
                this.pageFormat = PDRectangle.A1;
                break;
            case "A2":
                this.pageFormat = PDRectangle.A2;
                break;
            case "A3":
                this.pageFormat = PDRectangle.A3;
                break;
            case "A4":
                this.pageFormat = PDRectangle.A4;
                break;
            case "A5":
                this.pageFormat = PDRectangle.A5;
                break;
            case "A6":
                this.pageFormat = PDRectangle.A6;
                break;
            case "LETTER":
                this.pageFormat = PDRectangle.LETTER;
                break;
            default:
                this.pageFormat = PDRectangle.A4;
                break;
        }

        initSettings(rootWidth);
    }

    public PDFRenderer(int rootWidth, int rootHeight, OutputStream out, PDRectangle pageFormat)
    {
        this.rootHeight = rootHeight;
        this.pathToSave = out;
        this.pageCount = 0;
        this.pageFormat = pageFormat;
        initSettings(rootWidth);
    }

    private void initSettings(int rootWidth)
    {
        // calculate resize coefficient
        resCoef = this.pageFormat.getWidth() / rootWidth;

        // count the recent number of pages
        pageCount = (int) Math.ceil(rootHeight * resCoef / this.pageFormat.getHeight());

        // sets the top and bottom paddings for the output page
        outputTopPadding = this.pageFormat.getHeight() / 100;
        outputBottomPadding = this.pageFormat.getHeight() / 100;

        fontDB = new FontDB();
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
        // TODO render the markers
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
            rootNodeOfTree = new Node(null, elem, null, null, null);
            recentNodeInTree = rootNodeOfTree;
            // LIST
            rootNodeOfList = new Node(null, elem, null, null, rootNodeOfTree);
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
                Node tmpNode = new Node(null, elem, null, null, null);
                tmpNode.setParentIDOfNoninsertedNode(elem.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(elem, null, null, null);
            }

            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(elem, null, null, recentNodeInTree);
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
            rootNodeOfTree = new Node(null, null, text, null, null);
            recentNodeInTree = rootNodeOfTree;
            rootNodeOfList = new Node(null, null, text, null, rootNodeOfTree);
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
                Node tmpNode = new Node(null, null, text, null, null);
                tmpNode.setParentIDOfNoninsertedNode(text.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(null, text, null, null);
            }
            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(null, text, null, recentNodeInTree);
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
            rootNodeOfTree = new Node(null, null, null, box, null);
            recentNodeInTree = rootNodeOfTree;
            rootNodeOfList = new Node(null, null, null, box, rootNodeOfTree);
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
                Node tmpNode = new Node(null, null, null, box, null);
                tmpNode.setParentIDOfNoninsertedNode(convertedBox.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            }
            else
            {
                recentNodeInTree = targetNode.insertNewNode(null, null, box, null);
            }

            // LIST
            recentNodeInList = recentNodeInList.insertNewNode(null, null, box, recentNodeInTree);
        }
    }

    /**
     * Processing the LIST and TREE data structures and writes data to OUTPUT
     */
    @Override
    public void close()
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

        // deletes all items bigger than argument*pageFormat.getHeight() in
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

        pageEnd = pageFormat.getHeight();
        while (breakTable.size() > 0 || pageEnd < rootHeight * resCoef)
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
                        this.pageEnd += this.pageFormat.getHeight();
                        nalezeno = true;
                    }
                }

                // not founded in avoidTable -> break normal
                if (!nalezeno)
                {
                    makeBreakAt(pageEnd);
                    // sets new end of page according to height of the page in
                    // PDF document
                    this.pageEnd += this.pageFormat.getHeight();
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
                    this.pageEnd += this.pageFormat.getHeight();
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
     */
    private void makePDF()
    {

        // creates PDF document with first blank page
        createDocPDFBox();

        // inserts all needed blank pages to PDF document
        insertNPagesPDFBox(pageCount);

        // transforms all data from LIST data structure to Apache PDFBox format
        // and writes it do PDF document
        writeAllElementsToPDF();

        // saves current document
        saveDocPDFBox();
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
                        tableRec[0] = recNodeToInvestigate.getParentNode().getElemY() * resCoef
                                + recNodeToInvestigate.getParentNode().getPlusOffset();
                    else
                        tableRec[0] = getLastBottom(temp) * resCoef;

                    // finds ends of the interval
                    tableRec[1] = getFirstTop(recNodeToInvestigate) * resCoef;

                    // finds the break place
                    tableRec[2] = recNodeToInvestigate.getElem().getAbsoluteContentY() * resCoef;

                    // inserts into breakTable
                    insertIntoTable(tableRec, breakTable);
                }

                // element contains page-break-after: always; CSS property
                if (pgafter != null && pgafter == CSSProperty.PageBreak.ALWAYS)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    tableRec[0] = getLastBottom(recNodeToInvestigate) * resCoef;

                    // finds ends of the interval
                    Node temp = getElementBelow(recNodeToInvestigate);
                    if (temp != null)
                    {
                        tableRec[1] = getFirstTop(temp) * resCoef;
                    }
                    else
                    {
                        tableRec[1] = recNodeToInvestigate.getElemY() * resCoef
                                + recNodeToInvestigate.getElemHeight() * resCoef;
                    }

                    // finds the break place
                    tableRec[2] = recNodeToInvestigate.getElem().getAbsoluteContentY() * resCoef
                            + recNodeToInvestigate.getElem().getHeight() * resCoef;

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
                        tableRec[0] = getLastBottom(temp) * resCoef;
                    }
                    else
                    {
                        tableRec[0] = recNodeToInvestigate.getElemY() * resCoef;
                    }

                    // finds ends of the interval
                    tableRec[1] = getFirstTop(recNodeToInvestigate) * resCoef;

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
                    tableRec[0] = getLastBottom(recNodeToInvestigate) * resCoef;

                    // finds ends of the interval
                    Node temp = getElementBelow(recNodeToInvestigate);
                    if (temp != null)
                    {
                        tableRec[1] = getFirstTop(temp) * resCoef;
                    }
                    else
                    {
                        tableRec[1] = recNodeToInvestigate.getElemY() * resCoef
                                + recNodeToInvestigate.getElemHeight() * resCoef;
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
                    tableRec[0] = recNodeToInvestigate.getElem().getAbsoluteContentY() * resCoef - 1;

                    // finds ends of the interval
                    tableRec[1] = tableRec[0] + recNodeToInvestigate.getElem().getHeight() * resCoef + 1;

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
            if (avoidTable.get(i)[1] - avoidTable.get(i)[0] > biggerThan * pageFormat.getHeight())
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
                if (avoidTable.get(i)[1] - avoidTable.get(i - 1)[0] > biggerThan * pageFormat.getHeight())
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
            if (temp.getElemY() * resCoef + temp.getPlusOffset() + temp.getElemHeight() * resCoef
                    + temp.getPlusHeight() > recentNode.getElemY() * resCoef + recentNode.getPlusOffset())
                continue;

            if (nodeX == null)
                nodeX = temp;
            else if (nodeX.getElemY() * resCoef + nodeX.getPlusOffset() + nodeX.getElemHeight() * resCoef
                    + nodeX.getPlusHeight() <= temp.getElemY() * resCoef + temp.getPlusOffset()
                            + temp.getElemHeight() * resCoef + temp.getPlusHeight())
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
            if (temp.getElemY() * resCoef + temp.getPlusOffset() < recentNode.getElemY() * resCoef
                    + recentNode.getElemHeight() * resCoef + recentNode.getPlusHeight() + recentNode.getPlusOffset())
            {
                continue;
            }

            // wantedNode gets new reference if it has not one yet or the old
            // node
            // contains element with lower position then new candidate
            if (wantedNode == null)
                wantedNode = temp;
            else if (wantedNode.getElemY() * resCoef + wantedNode.getPlusOffset() >= temp.getElemY() * resCoef
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
            return recentNode.getElemY() * resCoef + recentNode.getPlusOffset();

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
                if (aktualni.getElemY() * resCoef + aktualni.getPlusOffset() < vysledekElem)
                {
                    vysledekElem = aktualni.getElemY() * resCoef + aktualni.getPlusOffset();
                }
            }
            else
            {
                if (aktualni.getElemY() * resCoef + aktualni.getPlusOffset() < vysledekNeelem)
                {
                    vysledekNeelem = aktualni.getElemY() * resCoef + aktualni.getPlusOffset();
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
            return recentNode.getElemY() * resCoef + recentNode.getElemHeight() * resCoef
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
                if (aktualni.getElemY() * resCoef + aktualni.getElemHeight() * resCoef + aktualni.getPlusOffset()
                        + aktualni.getPlusHeight() > vysledekElem)
                    vysledekElem = aktualni.getElemY() * resCoef + aktualni.getElemHeight() * resCoef
                            + aktualni.getPlusOffset() + aktualni.getPlusHeight();
            }
            else
            {
                if (aktualni.getElemY() * resCoef + aktualni.getElemHeight() * resCoef + aktualni.getPlusOffset()
                        + aktualni.getPlusHeight() > vysledekNeelem)
                    vysledekNeelem = aktualni.getElemY() * resCoef + aktualni.getElemHeight() * resCoef
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
        if (line1 > rootHeight * resCoef || line1 < 0)
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

            float startOfTheElement = myRecentNode.getElemY() * resCoef + myRecentNode.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode.getElemHeight() * resCoef
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

            float startOfTheElement = myRecentNode2.getElemY() * resCoef + myRecentNode2.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode2.getElemHeight() * resCoef
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
        spaceBetweenLines = (float) (pageFormat.getHeight() * Math.ceil((line1 - 1) / pageFormat.getHeight()) - line3);

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
            float startOfTheElement = myRecentNode.getElemY() * resCoef + myRecentNode.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode.getElemHeight() * resCoef
                    + myRecentNode.getPlusHeight() - 10 * resCoef;

            // whole element if above the line2 - nothing happens
            if (endOfTheElement <= line2)
            {
            }
            // increases the height of element which:
            // - is ElementBox
            // - is crossed by the line2
            // - has got at least 2 children
            else if (myRecentNode.isElem() && myRecentNode.getElemY() * resCoef + myRecentNode.getPlusOffset() < line2
                    && myRecentNode.getElemY() * resCoef + myRecentNode.getPlusOffset()
                            + myRecentNode.getElemHeight() * resCoef + myRecentNode.getPlusHeight() >= line2
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
        this.rootHeight += (outputTopPadding + spaceBetweenLines + outputBottomPadding) / resCoef;
        // updates values in all records in avoidTable and breakTable
        updateTables(outputTopPadding + spaceBetweenLines + outputBottomPadding);
        // update count the number of pages
        this.pageCount = (int) Math.ceil(rootHeight * resCoef / pageFormat.getHeight());
    }

    ////////////////////////////////////////////////////////////////////////
    // INSERTING TO PDF
    //
    // - gets data describing each element from data structures and calls
    // appropriate function to transform and write data to PDF document
    ////////////////////////////////////////////////////////////////////////

    /**
     * Writing elements and their CSS property to pages in PDF
     */
    private int writeAllElementsToPDF()
    {
        // goes through all pages in PDF and inserts to all elements to current page
        Filter pdfFilter = new Filter(null, 0, 0, 1.0f, 1.0f);
        BorderRadius borRad = new BorderRadius();
        boolean isBorderRad = false;
        boolean transf = false;
        for (int i = 0; i < pageCount; i++)
        {
            changeCurrentPageToPDFBox(i);

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
                        {
                            try
                            {
                                content.restoreGraphicsState();
                            } catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }
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
                            borRad.setCornerRadius(value2, value1, value4, value3, elem, resCoef);
                        }
                        else
                        {
                            isBorderRad = false;
                            borRad = new BorderRadius(); // calculating corner radiuses
                        }
                        
                        CSSProperty.BackgroundImage backgrd = elem.getStyle().getProperty("background-image");

                        if (backgrd == CSSProperty.BackgroundImage.gradient)
                        {
                            TermFunction.Gradient values = elem.getStyle().getValue(TermFunction.Gradient.class,
                                    "background-image");

                            if (values instanceof TermFunction.LinearGradient)
                            {
                                linearGrad = true;

                                double radAgl;
                                float degAgl;

                                // get angle of gradient line
                                if (((TermFunction.LinearGradient) values).getAngle() != null)
                                {
                                    radAgl = dec.getAngle(((TermFunction.LinearGradient) values).getAngle());
                                    degAgl = (float) Math.toDegrees(radAgl);
                                }
                                else
                                    degAgl = 180; // implicitne je 180deg
                                LinearGradient linGrad = new LinearGradient();
                                // calcutaling coordinates of starting points
                                // and ending points
                                linGrad.createGradLinePoints(degAgl, bounds.width, bounds.height);
                                // create color stops
                                List<ColorStop> colorstops = ((TermFunction.LinearGradient) values).getColorStops();
                                if (colorstops != null)
                                {
                                    linGrad.createColorStopsLength(colorstops, dec,
                                            ((TermFunction.LinearGradient) values).isRepeating());

                                    float[] newStartXY = new float[2];
                                    newStartXY = transXYtoPDF(elem, (float) (linGrad.x1 * resCoef),
                                            (float) (linGrad.y1 * resCoef), currentNode.getTreeEq().getPlusOffset(),
                                            currentNode.getTreeEq().getPlusHeight(), i);

                                    float[] newEndXY = new float[2];
                                    newEndXY = transXYtoPDF(elem, (float) (linGrad.x2 * resCoef),
                                            (float) (linGrad.y2 * resCoef), currentNode.getTreeEq().getPlusOffset(),
                                            currentNode.getTreeEq().getPlusHeight(), i);
                                    // create linear gradient
                                    shading = linGrad.createLinearGrad(newStartXY[0], newStartXY[1], newEndXY[0],
                                            newEndXY[1]);
                                }
                            }
                            else if (values instanceof TermFunction.RadialGradient)
                            {
                                radialGrad = true;

                                RadialGradient radGrad = new RadialGradient();
                                // gradient shape
                                radGrad.setShape(((TermFunction.RadialGradient) values).getShape());
                                // center point of gradient
                                radGrad.setGradientCenter(((TermFunction.RadialGradient) values).getPosition(), dec,
                                        bounds.width, bounds.height);
                                // radius
                                TermLengthOrPercent[] size = ((TermFunction.RadialGradient) values).getSize();
                                if (size != null)
                                {
                                    radGrad.setRadiusFromSizeValue(size, dec, bounds.x, bounds.y, bounds.width,
                                            bounds.height);
                                }

                                TermIdent sizeIdent = ((TermFunction.RadialGradient) values).getSizeIdent();
                                if (sizeIdent != null)
                                {
                                    radGrad.setRadiusFromSizeIdent(sizeIdent, bounds.width, bounds.height);
                                }
                                // color stops
                                List<ColorStop> colorstops = ((TermFunction.RadialGradient) values).getColorStops();
                                if (colorstops != null)
                                {
                                    radGrad.createColorStopsLength(colorstops, dec);

                                    float[] newXY = new float[2];
                                    newXY = transXYtoPDF(elem, radGrad.cx * resCoef, radGrad.cy * resCoef,
                                            currentNode.getTreeEq().getPlusOffset(),
                                            currentNode.getTreeEq().getPlusHeight(), i);

                                    AffineTransform moveToCenter = new AffineTransform();

                                    if (radGrad.shape.equals("ellipse"))
                                    {
                                        moveToCenter = radGrad.createTransformForEllipseGradient(newXY[0], newXY[1]);
                                    }

                                    radMatrix = new Matrix(moveToCenter);
                                    if (radGrad.err)
                                        shading = null;
                                    else // creating radial gradient
                                        shading = radGrad.createRadialGrad(newXY[0], newXY[1],
                                                (float) (radGrad.radc * resCoef));
                                } // end if colorstops != null
                            } // end radial-gradient
                        } // end gradient
                    }

                    // draws colored background
                    if (!isBorderRad)
                        drawBgToElem(elem, i, currentNode.getTreeEq().getPlusOffset(),
                            currentNode.getTreeEq().getPlusHeight(), radialGrad, linearGrad, shading, radMatrix);

                    // draws background image
                    if (elem.getBackgroundImages() != null && elem.getBackgroundImages().size() > 0)
                    {
                        insertBgImg(elem, i, currentNode.getTreeEq().getPlusOffset(),
                                currentNode.getTreeEq().getPlusHeight(), pdfFilter, isBorderRad, borRad);
                    }

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
                    float parentRightEndOfElement = (parent.getElemX() + parent.getElemWidth()) * resCoef;
                    float recentRightEndOfElement = (currentNode.getElemX() + currentNode.getElemWidth()) * resCoef;
                    float widthRecentElem = currentNode.getElemWidth() * resCoef;

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
            }
        }
        return 0;
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
     * Draw an background, which is represented by radial-gradient() function
     * 
     * @author Hoang Duong Nguyen
     * @returns 0 for inserted OK, -1 for exception occurs
     * @param lineWidth
     *            the width of the border line
     * @param shading
     *            radial gradient to paint
     * @param x
     *            x-axis of the element, which contains radial gradient
     *            background
     * @param y
     *            y-axis of the element
     * @param width
     *            width of the element
     * @param height
     *            height of the element
     * @param matrix
     *            matrix for the transformation from circle to eclipse gradient
     */
    private int drawBgGrad(float lineWidth, PDShadingType3 shading, float x, float y, float width, float height,
            Matrix matrix)
    {
        if (shading == null)
            return 1;
        try
        {
            content.saveGraphicsState();
            content.setLineWidth(lineWidth);
            content.addRect(x, y, width, height);
            content.clip();
            content.transform(matrix);
            content.shadingFill(shading);
            content.fill();
            content.restoreGraphicsState();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
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

        float paddingBottom = elem.getPadding().bottom * resCoef;
        float paddingLeft = elem.getPadding().left * resCoef;

        xy[0] = elem.getAbsoluteContentX() * resCoef - paddingLeft + x;
        xy[1] = (pageFormat.getHeight() - (elem.getAbsoluteContentY() * resCoef + plusOffset)
                + i * pageFormat.getHeight() - elem.getContentHeight() * resCoef - plusHeight - paddingBottom) + y;
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
     */
    private boolean insertTransform(Node recentNode, ElementBox elem, int i, boolean transf)
    {
        if (elem.isBlock() || elem.isReplaced())
        {
            CSSDecoder dec = new CSSDecoder(elem.getVisualContext());
            Rectangle bounds = elem.getAbsoluteContentBounds();
            // decode the origin
            int ox, oy;
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

            float newXY[] = transXYtoPDF(elem, ox * resCoef, oy * resCoef, recentNode.getTreeEq().getPlusOffset(),
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
                        int tx = dec.getLength(((TermFunction.Translate) term).getTranslateX(), false, 0, 0,
                                bounds.width);
                        int ty = dec.getLength(((TermFunction.Translate) term).getTranslateY(), false, 0, 0,
                                bounds.height);
                        ret.translate(tx * resCoef, -ty * resCoef); // - because
                                                                    // of the
                                                                    // different
                                                                    // coordinate
                                                                    // system in
                                                                    // PDF; *
                                                                    // rescoef
                                                                    // becaouse
                                                                    // fo the
                                                                    // page
                                                                    // ratio
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.TranslateX)
                    {
                        int tx = dec.getLength(((TermFunction.TranslateX) term).getTranslate(), false, 0, 0,
                                bounds.width);
                        ret.translate(tx * resCoef, 0.0);
                        transformed = true;
                    }
                    else if (term instanceof TermFunction.TranslateY)
                    {
                        int ty = dec.getLength(((TermFunction.TranslateY) term).getTranslate(), false, 0, 0,
                                bounds.height);
                        ret.translate(0.0, -ty * resCoef);
                        transformed = true;
                    }
                }

                if (transformed)
                {
                    try
                    {
                        if (transf) content.restoreGraphicsState();
                        content.saveGraphicsState();
                    } catch (IOException e)
                    {
                    }
                    drawTransformPDF(ret, ox, oy);
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
     * Draw the transformation to PDF.
     * 
     * @author Hoang Duong Nguyen
     * @returns 0 for inserted OK, -1 for exception occurs
     * @param aff
     *            class with information of transform
     * @param ox
     *            x-axis of the element
     * @param oy
     *            y-axis of the element
     * @param transf
     *            flag, which determines if the transform is set for the element
     * @param type
     *            type of the transform property
     */
    private int drawTransformPDF(AffineTransform aff, int ox, int oy)
    {
        try
        {
            Matrix matrix = new Matrix(aff);
            content.transform(Matrix.getTranslateInstance(ox, oy));
            content.transform(matrix);
            content.transform(Matrix.getTranslateInstance(-ox, -oy));
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Draws image gained from <img> tag to OUTPUT
     */
    private void insertImg(ReplacedBox box, int i, float plusOffset, float plusHeight, Filter filter,
            boolean isBorderRad, BorderRadius borRad)
    {
        ReplacedContent cont = box.getContentObj();
        if (cont != null)
        {
            if (cont instanceof ReplacedImage)
            {
                BufferedImage img = ((ReplacedImage) cont).getBufferedImage();
                float pageStart = i * pageFormat.getHeight();
                float pageEnd = (i + 1) * pageFormat.getHeight();

                Rectangle cb = ((Box) box).getAbsoluteContentBounds();
                if (img != null && cb.y * resCoef < pageEnd
                        && (cb.y + img.getHeight()) * resCoef + plusHeight + plusOffset > pageStart)
                {
                    img = filter.filterImg(img);
                    // calculates resized coordinates in CSSBox form
                    float startX = cb.x * resCoef;
                    float startY = (cb.y * resCoef + plusOffset + plusHeight) - i * pageFormat.getHeight(); // y position in the page
                    float width = (float) cb.getWidth() * resCoef;
                    float height = (float) cb.getHeight() * resCoef + plusHeight;
                    if (isBorderRad)
                    { // if border radius is set
                        float radiusX = Math.max(Math.max(borRad.topLeftX, borRad.topRightX),
                                Math.max(borRad.botLeftX, borRad.botRightX));
                        float radiusY = Math.max(Math.max(borRad.topLeftY, borRad.topRightY),
                                Math.max(borRad.botLeftY, borRad.botRightY));
                        img = makeImgRadiusCorner(img, radiusX, radiusY);
                    }
                    // inserts image
                    insertImagePDFBox(img, startX, startY, width, height);
                }
            }
        }
    }

    /**
     * Draws element background image to OUTPUT
     */
    private void insertBgImg(ElementBox elem, int i, float plusOffset, float plusHeight, Filter filter,
            boolean isBorderRad, BorderRadius borRad)
    {
        for (BackgroundImage bimg : elem.getBackgroundImages())
        {
            BufferedImage img = bimg.getBufferedImage();
            float pageStart = i * pageFormat.getHeight();
            float pageEnd = (i + 1) * pageFormat.getHeight();
            if (img != null && elem.getAbsoluteContentY() * resCoef + plusOffset < pageEnd
                    && (elem.getAbsoluteContentY() + img.getHeight()) * resCoef + plusOffset + plusHeight > pageStart)
            {
                img = filter.filterImg(img);
                // calculates resized coordinates in CSSBox form
                float startX = (elem.getAbsoluteContentX() - elem.getPadding().left) * resCoef;
                float startY = (elem.getAbsoluteContentY() - elem.getPadding().top) * resCoef + plusOffset
                        - i * pageFormat.getHeight();
                float width = img.getWidth() * resCoef;
                float height = img.getHeight() * resCoef;

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
    }

    /**
     * Draws border to OUTPUT
     * 
     * @returns 0 for inserted OK, -1 for exception occurs and 1 for border out
     *          of page
     */
    private int drawBorder(ElementBox elem, int i, float plusOffset, float plusHeight, boolean isBorderRad,
            BorderRadius borRad)
    {
        final LengthSet border = elem.getBorder();
        if (border.top > 0 || border.right > 0 || border.bottom > 0 || border.right > 0 || isBorderRad)
        {
            // counts the distance between top of the document and the start/end
            // of the page
            final float pageStart = i * pageFormat.getHeight();
            final float pageEnd = (i + 1) * pageFormat.getHeight();

            // checks if border is not completely out of page
            if (elem.getAbsoluteContentY() * resCoef + plusOffset > pageEnd
                    || (elem.getAbsoluteContentY() + plusOffset + elem.getContentHeight()) * resCoef + plusHeight
                            + plusOffset < pageStart)
                return 1;

            // calculates resized X,Y coordinates in CSSBox form
            final float border_x = elem.getAbsoluteContentX() * resCoef;
            final float border_y = pageFormat.getHeight() - (elem.getAbsoluteContentY() * resCoef + plusOffset)
                    + i * pageFormat.getHeight() - elem.getContentHeight() * resCoef - plusHeight;

            // calculates the padding for each side
            final float paddingTop = elem.getPadding().top * resCoef;
            final float paddingRight = elem.getPadding().right * resCoef;
            final float paddingBottom = elem.getPadding().bottom * resCoef;
            final float paddingLeft = elem.getPadding().left * resCoef;

            // calculates the border size for each side
            final float borderTopSize = border.top * resCoef;
            final float borderRightSize = border.right * resCoef;
            final float borderBottomSize = border.bottom * resCoef;
            final float borderLeftSize = border.left * resCoef;

            // calculate the element size
            final float elemWidth = elem.getContentWidth() * resCoef;
            final float elemHeight = elem.getContentHeight() * resCoef + plusHeight;

            float bX, bY, bWidth, bHeight;
            // calculating points for creating border radius
            if (isBorderRad)
            {
                ax = border_x + borRad.topLeftX - paddingLeft - borderLeftSize;
                ay = border_y + elem.getAbsoluteBorderBounds().height * resCoef - paddingTop - borderTopSize;
                bx = border_x + elem.getAbsoluteBorderBounds().width * resCoef - borRad.topRightX - paddingRight
                        - borderRightSize;
                by = ay;
                cx = border_x + elem.getAbsoluteBorderBounds().width * resCoef - paddingRight - borderRightSize;
                cy = border_y + elem.getAbsoluteBorderBounds().height * resCoef - borRad.topRightY - paddingTop
                        - borderTopSize;
                dx = cx;
                dy = border_y + borRad.botRightY - paddingBottom - borderBottomSize;
                ex = border_x + elem.getAbsoluteBorderBounds().width * resCoef - borRad.botRightX - paddingRight
                        - borderRightSize;
                ey = border_y - paddingBottom - borderBottomSize;
                fx = border_x + borRad.botLeftX - paddingLeft - borderLeftSize;
                fy = ey;
                gx = border_x - paddingLeft - borderLeftSize;
                gy = border_y + borRad.botLeftY - paddingBottom - borderBottomSize;
                hx = gx;
                hy = border_y + elem.getAbsoluteBorderBounds().height * resCoef - borRad.topLeftY - borderLeftSize
                        - paddingLeft;
                if (elem.getBgcolor() != null) drawBgInsideBorderRadius(elem,
                        borderTopSize == 0 ? 0 : borderTopSize + 1, borderRightSize == 0 ? 0 : borderRightSize + 1,
                        borderBottomSize == 0 ? 0 : borderBottomSize + 1, borderLeftSize == 0 ? 0 : borderLeftSize + 1,
                        borRad);
                drawBorderRadius(elem, borderTopSize, borderRightSize, borderBottomSize, borderLeftSize, borRad);
            }
            else
            {
                try
                {
                    // left border
                    if (borderLeftSize > 0)
                    {
                        bX = border_x - borderLeftSize - paddingLeft;
                        bY = border_y - borderBottomSize - paddingBottom;
                        bWidth = borderLeftSize;
                        bHeight = elemHeight + borderTopSize + borderBottomSize + paddingTop + paddingBottom;
                        drawRectanglePDFBox(borderLeftSize, getBorderColor(elem, "left"), bX, bY, bWidth, bHeight);
                    }

                    // right border
                    if (borderRightSize > 0)
                    {
                        bX = border_x + elemWidth + paddingRight;
                        bY = border_y - borderBottomSize - paddingBottom;
                        bWidth = borderRightSize;
                        bHeight = elemHeight + borderTopSize + borderBottomSize + paddingTop + paddingBottom;
                        drawRectanglePDFBox(borderRightSize, getBorderColor(elem, "right"), bX, bY, bWidth, bHeight);
                    }

                    // top border
                    if (borderTopSize > 0)
                    {
                        bX = border_x - borderLeftSize - paddingLeft;
                        bY = border_y + elemHeight + paddingTop;
                        bWidth = elemWidth + borderLeftSize + borderRightSize + paddingLeft + paddingRight;
                        bHeight = borderTopSize;
                        drawRectanglePDFBox(borderTopSize, getBorderColor(elem, "top"), bX, bY, bWidth, bHeight);
                    }

                    // bottom border
                    if (borderBottomSize > 0)
                    {
                        bX = border_x - borderLeftSize - paddingLeft;
                        bY = border_y - borderBottomSize - paddingBottom;
                        bWidth = elemWidth + borderLeftSize + borderRightSize + paddingLeft + paddingRight;
                        bHeight = borderBottomSize;
                        drawRectanglePDFBox(borderBottomSize, getBorderColor(elem, "bottom"), bX, bY, bWidth, bHeight);
                    }

                } catch (Exception e)
                {

                    e.printStackTrace();
                    return -1;
                }
            }
        }
        return 0;
    }

    /*
     * https://stackoverflow.com/questions/7603400/how-to-make-a-rounded-corner-
     * image-in-java
     */
    public BufferedImage makeImgRadiusCorner(BufferedImage image, float cornerRadiusX, float cornerRadiusY)
    {
        int w = (int) (image.getWidth());
        int h = (int) (image.getHeight());
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
        g2.setColor(Color.WHITE);
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
     */
    private int drawBgInsideBorderRadius(ElementBox elem, float bTopSize, float bRightSize, float bBotSize,
            float bLeftSize, BorderRadius borRad)
    {
        try
        {
            float bezier = 0.551915024494f;
            content.setLineWidth(1);
            content.setNonStrokingColor(elem.getBgcolor());
            content.setStrokingColor(elem.getBgcolor());
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
            content.fillAndStroke(); // insert background and colour of border
        } catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
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
     */
    private int drawBorderRadius(ElementBox elem, float bTopSize, float bRightSize, float bBotSize, float bLeftSize,
            BorderRadius borRad)
    {
        try
        {
            float bezier = 0.551915024494f;
            if (bTopSize != 0)
            { // drawing top border
                content.setLineWidth(bTopSize);
                content.setStrokingColor(getBorderColor(elem, "top"));
                content.moveTo(ax, ay);
                content.curveTo1((ax + bx) / 2, (ay + by) / 2, bx, by);
                content.curveTo(bx + bezier * borRad.topRightX, by, cx, cy + bezier * borRad.topRightY, cx, cy);
                content.stroke();
            }
            if (bRightSize != 0)
            { // drawing right border
                content.setLineWidth(bRightSize);
                content.setStrokingColor(getBorderColor(elem, "right"));
                content.moveTo(cx, cy);
                content.curveTo1((cx + dx) / 2, (cy + dy) / 2, dx, dy);
                content.curveTo(dx, dy - bezier * borRad.botRightY, ex + bezier * borRad.botRightX, ey, ex, ey);
                content.stroke();
            }
            if (bBotSize != 0)
            { // drawing bot border
                content.setLineWidth(bBotSize);
                content.setStrokingColor(getBorderColor(elem, "bottom"));
                content.moveTo(ex, ey);
                content.curveTo1((ex + fx) / 2, (ey + fy) / 2, fx, fy);
                content.curveTo(fx - bezier * borRad.botLeftX, fy, gx, gy - bezier * borRad.botLeftY, gx, gy);
                content.stroke();
            }
            if (bLeftSize != 0)
            { // drawing left border
                content.setLineWidth(bLeftSize);
                content.setStrokingColor(getBorderColor(elem, "left"));
                content.moveTo(gx, gy);
                content.curveTo1((gx + hx) / 2, (gy + hy) / 2, hx, hy);
                content.curveTo(hx, hy + bezier * borRad.topLeftY, ax - bezier * borRad.topLeftX, ay, ax, ay);
                content.stroke();
            }
        } catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Draws colored background to OUTPUT
     * 
     * @returns 0 for inserted OK, -1 for exception occurs and 1 for element
     *          completely out of page
     */
    private int drawBgToElem(ElementBox elem, int i, float plusOffset, float plusHeight, boolean radialGrad,
            boolean linearGrad, PDShadingType3 shading, Matrix matrix)
    {
        // checks if any color available
        if ((elem.getBgcolor() == null) && (!radialGrad) && (!linearGrad)) 
            return 0;

        // for root element the background color will be painted to background
        // of whole page
        if ((elem.getParent() == null) && (!radialGrad) && (!linearGrad)) // TODO gradient in the whole page?
            return drawBgToWholePagePDFBox(elem.getBgcolor());

        // calculates the start and the end of current page
        float pageStart = i * pageFormat.getHeight();
        float pageEnd = (i + 1) * pageFormat.getHeight();

        // checks if the element if completely out of page
        if (elem.getAbsoluteContentY() * resCoef + plusOffset > pageEnd
                || (elem.getAbsoluteContentY() + elem.getContentHeight()) * resCoef + plusOffset
                        + plusHeight < pageStart)
            return 1;

        // calculates the padding
        float paddingTop = elem.getPadding().top * resCoef;
        float paddingRight = elem.getPadding().right * resCoef;
        float paddingBottom = elem.getPadding().bottom * resCoef;
        float paddingLeft = elem.getPadding().left * resCoef;

        try
        {
            float border_x = elem.getAbsoluteContentX() * resCoef - paddingLeft;
            float border_y = pageFormat.getHeight() - (elem.getAbsoluteContentY() * resCoef + plusOffset)
                    + i * pageFormat.getHeight() - elem.getContentHeight() * resCoef - plusHeight - paddingBottom;
            if (radialGrad)
            { // if the background is radial gradient
                drawBgGrad(0, shading, border_x, border_y,
                        (elem.getContentWidth()) * resCoef + paddingLeft + paddingRight,
                        elem.getContentHeight() * resCoef + paddingTop + paddingBottom + plusHeight, matrix);

            }
            else if (linearGrad)
            { // if is linear gradient
                drawBgGrad(0, shading, border_x, border_y,
                        (elem.getContentWidth()) * resCoef + paddingLeft + paddingRight,
                        elem.getContentHeight() * resCoef + paddingTop + paddingBottom + plusHeight, matrix);

            }
            else
            {
                drawRectanglePDFBox(0, elem.getBgcolor(), border_x, border_y,
                        (elem.getContentWidth()) * resCoef + paddingLeft + paddingRight,
                        elem.getContentHeight() * resCoef + paddingTop + paddingBottom + plusHeight);
            }

        } catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Draws text to OUTPUT
     * 
     * @returns 0 for inserted ok, -1 for exception occures and 1 for text out
     *          of page
     */
    private int insertText(TextBox text, int i, float plusOffset, float plusHeight)
    {

        // counts the distance between top of the document and the start/end of
        // the page
        float pageStart = i * pageFormat.getHeight();
        float pageEnd = (i + 1) * pageFormat.getHeight();

        // checks if the whole text is out of the page
        if (text.getAbsoluteContentY() * resCoef + plusOffset > pageEnd
                || (text.getAbsoluteContentY() + text.getHeight()) * resCoef + plusOffset < pageStart)
            return 1;

        // gets data describing the text
        VisualContext ctx = text.getVisualContext();
        float fontSize = ctx.getFont().getSize() * resCoef;
        boolean isBold = ctx.getFont().isBold();
        boolean isItalic = ctx.getFont().isItalic();
        boolean isUnderlined = ctx.getTextDecorationString().equals("underline");
        String fontFamily = ctx.getFont().getFamily();
        Color color = ctx.getColor();

        // if font is not in fontTable we load it
        PDFont font = null;
        for (int iter = 0; iter < fontTable.size(); iter++)
        {

            if (fontTable.get(iter).fontName.equalsIgnoreCase(fontFamily) && fontTable.get(iter).isItalic == isItalic
                    && fontTable.get(iter).isBold == isBold)
                font = fontTable.get(iter).loadedFont;
        }
        if (font == null)
        {
            font = setFont(fontFamily, isItalic, isBold);
            fontTable.add(new FontTableRecord(fontFamily, isBold, isItalic, font));
        }

        // font.setFontEncoding(new PdfDocEncoding()); //TODO is this useful?
        // String textToInsert = filterUnicode(text.getText());
        String textToInsert = text.getText();

        try
        {
            content.setNonStrokingColor(color);
            float leading = 2f * fontSize;

            // counts the resized coordinates in CSSBox form
            float startX = text.getAbsoluteContentX() * resCoef;
            float startY = (text.getAbsoluteContentY() * resCoef + plusOffset) % pageFormat.getHeight();

            // writes to PDF
            writeTextPDFBox(startX, startY, textToInsert, font, fontSize, isUnderlined, isBold, leading);

        } catch (Exception e)
        {

            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /////////////////////////////////////////////////////////////////////////
    // USES APACHE PDFBOX FUNCTIONS TO CREATE PDF DOCUMENT
    //
    // - all parameters are in CSSBox form (y coordinate is distance from top)
    // - transforms data to Apache PDFBox form and creates PDF document using
    // Apache PDFBox functions
    /////////////////////////////////////////////////////////////////////////

    /**
     * Saves the PDF document to disk using PDFBox
     */
    private int saveDocPDFBox()
    {
        try
        {
            content.close();
            doc.save(pathToSave);
            doc.close();
        } catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Creates document witch first page in it using PDFBox
     */
    private int createDocPDFBox()
    {
        try
        {
            doc = new PDDocument();
            page = new PDPage(pageFormat);
            doc.addPage(page);
            content = new PDPageContentStream(doc, page);
        } catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Inserts N pages to PDF document using PDFBox
     */
    private int insertNPagesPDFBox(int pageCount)
    {
        for (int i = 1; i < pageCount; i++)
        {
            PDPage page = new PDPage(pageFormat);
            doc.addPage(page);
        }
        return 0;
    }

    /**
     * Changes recent page using PDFBox
     */
    private int changeCurrentPageToPDFBox(int i)
    {
        page = (PDPage) doc.getDocumentCatalog().getPages().get(i);

        try
        {
            content.close();
            content = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Inserts background to whole recent PDF page using PDFBox
     */
    private int drawBgToWholePagePDFBox(Color bgColor)
    {

        try
        {
            content.setNonStrokingColor(bgColor);
            content.addRect(0, 0, pageFormat.getWidth(), pageFormat.getHeight());
            content.fill();
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Inserts rectangle to recent PDF page using PDFBox
     */
    private int drawRectanglePDFBox(float lineWidth, Color bgColor, float x, float y, float width, float height)
    {
        if (bgColor == null)
            return 1;
        try
        {
            content.setLineWidth(lineWidth);
            content.setNonStrokingColor(bgColor);

            content.addRect(x, y, width, height);
            content.fill();
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Inserts image to recent PDF page using PDFBox
     */
    private int insertImagePDFBox(BufferedImage img, float x, float y, float width, float height)
    {
        // transform X,Y coordinates to Apache PDFBox format
        y = pageFormat.getHeight() - height - y;

        try
        {
            // PDXObjectImage ximage = new PDPixelMap(doc, img);
            // content.drawXObject(ximage, x, y, width, height);
            PDImageXObject ximage = LosslessFactory.createFromImage(doc, img);
            content.drawImage(ximage, x, y, width, height);
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Writes String to recent PDF page using PDFBox
     */
    private int writeTextPDFBox(float x, float y, String textToInsert, PDFont font, float fontSize,
            boolean isUnderlined, boolean isBold, float leading)
    {
        // transform X,Y coordinates to Apache PDFBox format
        y = pageFormat.getHeight() - y - leading * resCoef;

        try
        {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(x, y);
            try
            {
                content.showText(textToInsert);
            } catch (IllegalArgumentException e)
            {
                // NOTE: seems to happen for embedded icon fonts like glyphicons
                // and fa, add space so there is some text otherwise PDFBox
                // throws IllegalStateException: subset is empty; these work
                // with SVGRenderer
                content.showText(" ");
                System.err.println("Error: " + e.getMessage());
            }
            content.endText();

            // underlines text if text is set underlined
            if (isUnderlined)
            {
                content.setLineWidth(1);
                float strokeWidth = font.getStringWidth(textToInsert) / 1000 * fontSize;
                float lineHeightCalibration = 1f;
                float yOffset = fontSize / 6.4f;
                if (isBold)
                {
                    lineHeightCalibration = 1.5f;
                    yOffset = fontSize / 5.7f;
                }

                content.addRect(x, y - yOffset, strokeWidth, resCoef * lineHeightCalibration);
                content.fill();
            }
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Creates object describing font
     * 
     * @return the font object
     */
    private PDFont setFont(String fontFamily, boolean isItalic, boolean isBold)
    {
        PDFont font = loadTTF(fontFamily, isItalic, isBold);
        // try some fallbacks when not found
        if (font == null) font = tryTTFFallback(fontFamily, isItalic, isBold);
        if (font == null) font = tryBuiltinFallback(fontFamily, isItalic, isBold);
        return font;
    }

    private PDFont tryBuiltinFallback(String fontFamily, boolean isItalic, boolean isBold)
    {
        PDFont font;

        fontFamily = fontFamily.toLowerCase();
        switch (fontFamily)
        {
            case "courier":
            case "courier new":
            case "lucida console":
                if (isBold && isItalic)
                {
                    font = PDType1Font.COURIER_BOLD_OBLIQUE;
                }
                else if (isBold)
                {
                    font = PDType1Font.COURIER_BOLD;
                }
                else if (isItalic)
                {
                    font = PDType1Font.COURIER_OBLIQUE;
                }
                else
                {
                    font = PDType1Font.COURIER;
                }
                break;
            case "times":
            case "garamond":
            case "georgia":
            case "times new roman":
            case "serif":
                if (isBold && isItalic)
                {
                    font = PDType1Font.TIMES_BOLD_ITALIC;
                }
                else if (isBold)
                {
                    font = PDType1Font.TIMES_BOLD;
                }
                else if (isItalic)
                {
                    font = PDType1Font.TIMES_ITALIC;
                }
                else
                {
                    font = PDType1Font.TIMES_ROMAN;
                }
                break;
            default:
                if (isBold && isItalic)
                {
                    font = PDType1Font.HELVETICA_BOLD_OBLIQUE;
                }
                else if (isBold)
                {
                    font = PDType1Font.HELVETICA_BOLD;
                }
                else if (isItalic)
                {
                    font = PDType1Font.HELVETICA_OBLIQUE;
                }
                else
                {
                    font = PDType1Font.HELVETICA;
                }
                break;
        }
        return font;
    }

    private PDFont tryTTFFallback(String fontFamily, boolean isItalic, boolean isBold)
    {
        fontFamily = fontFamily.toLowerCase();
        switch (fontFamily)
        {
            case "courier":
            case "courier new":
            case "lucida console":
            case "monotype":
                return loadTTFAlternatives(new String[] { "freemono", "DejaVuSansMono" }, isItalic, isBold);
            case "times":
            case "garamond":
            case "georgia":
            case "times new roman":
            case "serif":
                return loadTTFAlternatives(new String[] { "freeserif" }, isItalic, isBold);
            default:
                return loadTTFAlternatives(new String[] { "freesans" }, isItalic, isBold);
        }
    }

    /**
     * Tries to load a font from the system database.
     * 
     * @param fontFamily
     * @param isItalic
     * @param isBold
     * @return the font or {@code null} when not found
     */
    private PDFont loadTTF(String fontFamily, boolean isItalic, boolean isBold)
    {
        PDFont font = null;
        try
        {
            URI uri = fontDB.findFontURI(fontFamily, isBold, isItalic);
            if (uri != null) font = PDType0Font.load(doc, new File(uri));
        } catch (IOException e)
        {
            font = null;
        }
        return font;
    }

    private PDFont loadTTFAlternatives(String[] fontFamilies, boolean isItalic, boolean isBold)
    {
        for (String fontFamily : fontFamilies)
        {
            PDFont font = loadTTF(fontFamily, isItalic, isBold);
            if (font != null) return font;
        }
        return null;
    }

    /////////////////////////////////////////////////////////////////////
    // OTHER FUNCTIONS
    /////////////////////////////////////////////////////////////////////

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
                clr = CSSUnits.convertColor(tclr.getValue());
            }
            if (clr == null)
            {
                if (elem.getBgcolor() != null)
                    clr = elem.getBgcolor();
                else
                    clr = Color.WHITE;
            }
        }
        else
        {
            clr = elem.getBgcolor();
        }
        return clr;
    }

}
