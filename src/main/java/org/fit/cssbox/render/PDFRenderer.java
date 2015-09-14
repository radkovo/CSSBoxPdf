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
 */

package org.fit.cssbox.render;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;

import org.apache.pdfbox.encoding.PdfDocEncoding;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDPixelMap;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.fit.cssbox.layout.BackgroundImage;
import org.fit.cssbox.layout.Box;
import org.fit.cssbox.layout.ElementBox;
import org.fit.cssbox.layout.LengthSet;
import org.fit.cssbox.layout.ReplacedBox;
import org.fit.cssbox.layout.ReplacedContent;
import org.fit.cssbox.layout.ReplacedImage;
import org.fit.cssbox.layout.TextBox;
import org.fit.cssbox.layout.VisualContext;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.TermColor;

/**
 * A renderer that produces an PDF output using PDFBox library
 * 
 * @author Zbynek Cervinka
 */
public class PDFRenderer implements BoxRenderer
{
    private float resCoef, rootWidth, rootHeight;
    
    // PDFBox variables
    private PDDocument doc = null;
    private PDPage page = null;
    private PDPageContentStream content = null;
    private PDRectangle mediabox = null;
    private PDRectangle pageFormat = null;
    
    // page help variables
    private int pageCount;
    private float pageEnd;
    
    // TREE and LIST variables
    private Node rootNodeOfTree, recentNodeInTree, rootNodeOfList, recentNodeInList;
    private Vector<Node> nodesWithoutParent = new Vector<Node>(16); 
    
    // break/avoid tables
    private Vector <float[]> breakTable = new Vector <float[]> (2);
    private Vector <float[]> avoidTable = new Vector <float[]> (2);

    // font variables
    private String pathToTTFFonts = "/";
    private Vector <fontTableRecord> fontTable = new Vector <fontTableRecord> (2);
    
    private class fontTableRecord {
    	
        public String fontName;
        public Boolean isBold;
        public Boolean isItalic;        
        public PDFont loadedFont;
        
        fontTableRecord (String fontName,  Boolean isBold, Boolean isItalic, PDFont loadedFont) {
        	
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
    public PDFRenderer(int rootWidth, int rootHeight, OutputStream out, String pageFormat) {
        this.rootWidth = rootWidth;
        this.rootHeight = rootHeight;
        this.pathToSave = out;
        this.pageCount = 0;
        
        switch (pageFormat) {
            case "A0":
                this.pageFormat = PDPage.PAGE_SIZE_A0;
                break;
            case "A1":
                this.pageFormat = PDPage.PAGE_SIZE_A1;
                break;
            case "A2":
                this.pageFormat = PDPage.PAGE_SIZE_A2;
                break;
            case "A3":
                this.pageFormat = PDPage.PAGE_SIZE_A3;
                break;
            case "A4":
                this.pageFormat = PDPage.PAGE_SIZE_A4;
                break;
            case "A5":
                this.pageFormat = PDPage.PAGE_SIZE_A5;
                break;              
            case "A6":
                this.pageFormat = PDPage.PAGE_SIZE_A6;
                break;
            case "LETTER":
                this.pageFormat = PDPage.PAGE_SIZE_LETTER;
                break;
            default:
                this.pageFormat = PDPage.PAGE_SIZE_A4;
                break;
        }
        
        // calculate resize coefficient
        resCoef = this.pageFormat.getWidth()/rootWidth;
        
        // count the recent number of pages
        pageCount = (int)Math.ceil(rootHeight*resCoef/this.pageFormat.getHeight());
        
        // sets the top and bottom paddings for the output page
       	outputTopPadding = this.pageFormat.getHeight()/100;
        outputBottomPadding = this.pageFormat.getHeight()/100;
    }

    @Override
    public void startElementContents(ElementBox elem) {}
    
    @Override
    public void finishElementContents(ElementBox elem) {}

    /**
     * Creates 2 new Nodes with reference to elem inside
     *      - one goes to LIST
     *      - second goes to TREE
     */
    @Override
    public void renderElementBackground(ElementBox elem) {
    	
        // elem has no parent object - new Node will be root
        if (elem.getParent() == null) {
            // TREE
            rootNodeOfTree = new Node(null, elem, null, null, null);
            recentNodeInTree = rootNodeOfTree;
            // LIST
            rootNodeOfList = new Node(null, elem, null, null, rootNodeOfTree);
            recentNodeInList = rootNodeOfList; 
        }
        // add new Node with reference to elem inside to TREE and to LIST to right place
        else {
            // TREE
            Node targetNode = findNodeToInsert(elem.getParent().getOrder(), elem.getOrder());
            if (targetNode == null) {
                Node tmpNode = new Node(null, elem, null, null, null);
                tmpNode.setParentIDOfNoninsertedNode(elem.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            } else {
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
    public void renderTextContent(TextBox text) {
        
    	// elem has no parent object - new Node will be root
        if (text.getParent() == null) {
            rootNodeOfTree = new Node(null, null, text, null, null);
            recentNodeInTree = rootNodeOfTree;
            rootNodeOfList = new Node(null, null, text, null, rootNodeOfTree);
            recentNodeInList = rootNodeOfList;  
        }
        // add new Node with reference to elem inside to TREE and to LIST to right place
        else {
            // TREE
            Node targetNode = findNodeToInsert(text.getParent().getOrder(), text.getOrder());
            if (targetNode == null) {
                Node tmpNode = new Node(null, null, text, null, null);
                tmpNode.setParentIDOfNoninsertedNode(text.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            } else {
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
    public void renderReplacedContent(ReplacedBox box) {

        Box convertedBox = (Box) box;
        // elem has no parent object - new Node will be root
        if (convertedBox.getParent() == null) {
            rootNodeOfTree = new Node(null, null, null, box, null);
            recentNodeInTree = rootNodeOfTree;
            rootNodeOfList = new Node(null, null, null, box, rootNodeOfTree);
            recentNodeInList = rootNodeOfList; 
        }
        // add new Node with reference to elem inside to TREE and to LIST to right place
        else {
            // TREE
            Node targetNode = findNodeToInsert(convertedBox.getParent().getOrder(), convertedBox.getOrder());

            if (targetNode == null) {
                Node tmpNode = new Node(null, null, null, box, null);
                tmpNode.setParentIDOfNoninsertedNode(convertedBox.getParent().getOrder());
                nodesWithoutParent.add(tmpNode);
            } else {
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
    public void close() {
    	
        // FINISH STEP B - process the nodesWithoutParent table and insert nodes to TREE, if possible
        tryToInsertNotInsertedNodes();
        
        // STEP C - creates breakTable and avoidTable tables from data structure
        //			and modifies them to contain only records that are not causing conflicts
        //			or unacceptable page break appearance
        createAndProcessBreakAndAvoidTables();
        
        // STEP D - makes paging in the TREE data structure according to data
        //			in breakTable, avoidTable and the ends determined by the size of document page
        makePaging();
  
        // STEP E - transforms all data from LIST data structure to Apache PDFBox format
        //			and using Apache PDFBox functions creates PDF document containing transformed data       
        makePDF();
    }
    
    //////////////////////////////////////////////////////////////////////
    // FUNCTIONS FULFILLING THE B - E STEP
    //////////////////////////////////////////////////////////////////////
    
    /**
     * FINISH STEP B - process the nodesWithoutParent table and insert nodes to TREE, if possible
     */
    private void tryToInsertNotInsertedNodes() {
    	// repeats until the table is empty
        while (nodesWithoutParent.size() > 0) {         
            int sizeBefore = nodesWithoutParent.size();
            // goes through table and tries to find at least one record to add to TREE
            for (int i=0; i<nodesWithoutParent.size(); i++) {
                Node findMyParent = nodesWithoutParent.get(i);
                
                Node nodeToInsert = findNodeToInsert(findMyParent.getParentIDOfNoninsertedNode(), findMyParent.getID());
                // inserts the node, if parent node found in the tree
                if (nodeToInsert != null) {
                    nodeToInsert.insertNewNode(findMyParent);
                    nodesWithoutParent.remove(i);
                }
            }
            // if non of the records can not bee added to the TREE, it breaks the cycle
            if (sizeBefore == nodesWithoutParent.size()) break;
        }
    }
    
    /**
     * STEP C - creates breakTable and avoidTable tables from data structure
     *			and modifies them to contain only records that are not causing conflicts
     *			or unacceptable page break appearance
     */
    private void createAndProcessBreakAndAvoidTables() {
 
        // creates and inserts records into breakTable and avoidTable
    	//		according to CSS property of elements in TREE
        createBreakAvoidTables();
        
        // deletes all items bigger than argument*pageFormat.getHeight() in avoidTable
        deleteAvoidsBiggerThan(0.8f);
        
        // merges all records containing overlapping intervals, respects maximum size of interval
        mergeAvoids(0.8f);
    }

    /**
     * STEP D - makes paging in the TREE data structure according to data
     *			in breakTable, avoidTable and the ends determined by the size of document page 
     */
    private void makePaging() {
        
        pageEnd = pageFormat.getHeight();
        while (breakTable.size() > 0 || pageEnd < rootHeight*resCoef ) {
            // continues breaking until the breakTable is not empty
        	//		or the end of page is below the content limit
            if (breakTable.size() == 0 || pageEnd < breakTable.get(0)[0]) {
            
                // searches avoidTable for interval on the boundary between 2 pages
                boolean nalezeno = false;
                for (int i=0; i<avoidTable.size(); i++) {
                    
                    if (avoidTable.get(i)[0] < pageEnd &&
                        avoidTable.get(i)[1] > pageEnd) {
                    
                        makeBreakAt(avoidTable.get(i)[2]);
                        // sets new end of page according to height of the page in PDF document
                        this.pageEnd += this.pageFormat.getHeight();
                        nalezeno = true;
                    }
                }
            
                // not founded in avoidTable -> break normal
                if (!nalezeno) {
                	makeBreakAt(pageEnd);
                	// sets new end of page according to height of the page in PDF document
                	this.pageEnd += this.pageFormat.getHeight();
                }
            }
            // EOP is inside the interval in first record of breakTable
            else if (pageEnd > breakTable.get(0)[0] && pageEnd < breakTable.get(0)[1]) {
            
                if (breakTable.get(0)[2] > pageEnd) {
                	
                	makeBreakAt(pageEnd);
	                // sets new end of page according to height of the page in PDF document
	                this.pageEnd += this.pageFormat.getHeight();
                }
                else makeBreakAt(breakTable.get(0)[2]);
                breakTable.remove(0);               
            }
            // EOP is after the interval in first record of breakTable
            else {
                makeBreakAt(breakTable.get(0)[2]);
                breakTable.remove(0);
            }
        }
    }
    
    /**
     * STEP E - transforms all data from LIST data structure to Apache PDFBox format
     *			and using Apache PDFBox functions creates PDF document containing transformed data		 
     */
    private void makePDF() {
    	
        // creates PDF document with first blank page
        createDocPDFBox();
        
        // inserts all needed blank pages to PDF document
        insertNPagesPDFBox(pageCount);

        // transforms all data from LIST data structure to Apache PDFBox format
        //		and writes it do PDF document
        writeAllElementsToPDF();
        
        // saves current document
        saveDocPDFBox();
    }
    
    /////////////////////////////////////////////////////////////////////
    // FUNCTIONS FOR WORKING WITH LIST AND TREE DATA STRUCTURE
    /////////////////////////////////////////////////////////////////////

    /**
     * Finds the parent node to insert actual node in TREE
     * @return the Node
     */
    private Node findNodeToInsert(int parentID, int myID) {

        // there is 2x ID=0 at the root of TREE - if my parents ID is zero and I am not,
    	//		I have to insert to the second node with ID=0
        if (myID != 0 && parentID == 0)
            return rootNodeOfTree.getAllChildren().firstElement();
        
        // wanted node "to insert" is recent node
        if (recentNodeInTree.getID() == parentID)
            return recentNodeInTree;
        
        // wanted node "to insert" is parent node of recent node
        if (recentNodeInTree.getParentNode() != null && recentNodeInTree.getParentNode().getID() == parentID)
            return recentNodeInTree.getParentNode();
        
        // goes through whole tree
        Vector<Node> queueOpen = new Vector<Node>(16);
        queueOpen.add(rootNodeOfTree);
        while (queueOpen.size() > 0) {
            
            if (queueOpen.firstElement().getID() == parentID) return queueOpen.firstElement();
        
            Vector<Node> children = queueOpen.firstElement().getAllChildren();
            if (children != null) queueOpen.addAll(children);
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
    private void createBreakAvoidTables() {
        
        Vector<Node> queueOpen = new Vector<Node>(16);
        queueOpen.add(rootNodeOfTree);
        
        // goes through TREE
        while (queueOpen.size() > 0) {
            Node recNodeToInvestigate = queueOpen.firstElement();
            queueOpen.remove(0);

            if (recNodeToInvestigate.isElem()) {
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
                        tableRec[0] = recNodeToInvestigate.getParentNode().getElemY()*resCoef + recNodeToInvestigate.getParentNode().getPlusOffset();
                    else
                        tableRec[0] = getLastBottom(temp)*resCoef;
                    
                    // finds ends of the interval
                    tableRec[1] = getFirstTop(recNodeToInvestigate)*resCoef;
                    
                    // finds the break place                  
                    tableRec[2] = recNodeToInvestigate.getElem().getAbsoluteContentY()*resCoef;

                    // inserts into breakTable
                    insertIntoTable(tableRec, breakTable);
                }
                
                // element contains page-break-after: always; CSS property
                if (pgafter != null && pgafter == CSSProperty.PageBreak.ALWAYS)
                {                       
                    // creates empty record
                    float[] tableRec = new float[4];
                    
                    // finds start of the interval
                    tableRec[0] = getLastBottom(recNodeToInvestigate)*resCoef;
                 
                    // finds ends of the interval
                    Node temp = getElementBelow(recNodeToInvestigate);
                    if (temp != null) { 
                        tableRec[1] = getFirstTop(temp)*resCoef;
                    }
                    else {
                        tableRec[1] = recNodeToInvestigate.getElemY()*resCoef + recNodeToInvestigate.getElemHeight()*resCoef;
                    }
                    
                    // finds the break place
                    tableRec[2] = recNodeToInvestigate.getElem().getAbsoluteContentY()*resCoef + recNodeToInvestigate.getElem().getHeight()*resCoef;
                    
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
                    if (temp != null) { 
                        tableRec[0] = getLastBottom(temp)*resCoef;
                    }
                    else {
                        tableRec[0] = recNodeToInvestigate.getElemY()*resCoef;
                    }

                    // finds ends of the interval
                    tableRec[1] = getFirstTop(recNodeToInvestigate)*resCoef;
                    
                    // finds the break place   
                    tableRec[2] = tableRec[0]-1;

                    // inserts into avoidTable
                    insertIntoTable(tableRec, avoidTable);
                }
                
                // element contains page-break-after: avoid; CSS property
                if (pgafter != null && pgafter == CSSProperty.PageBreak.AVOID)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    tableRec[0] = getLastBottom(recNodeToInvestigate)*resCoef;

                    // finds ends of the interval
                    Node temp = getElementBelow(recNodeToInvestigate);
                    if (temp != null) { 
                        tableRec[1] = getFirstTop(temp)*resCoef;
                    }
                    else {
                        tableRec[1] = recNodeToInvestigate.getElemY()*resCoef + recNodeToInvestigate.getElemHeight()*resCoef;
                    }

                    // finds the break place   
                    tableRec[2] = tableRec[0]-1;

                    // inserts into avoidTable
                    insertIntoTable(tableRec, avoidTable);
                }
                
                // element contains page-break-inside: avoid; CSS property
                if (pginside != null && pginside == CSSProperty.PageBreakInside.AVOID)
                {
                    // creates empty record
                    float[] tableRec = new float[4];

                    // finds start of the interval
                    tableRec[0] = recNodeToInvestigate.getElem().getAbsoluteContentY()*resCoef - 1;
                    
                    // finds ends of the interval
                    tableRec[1] = tableRec[0] + recNodeToInvestigate.getElem().getHeight()*resCoef + 1;                   

                    // finds the break place 
                    tableRec[2] = tableRec[0] - 1;

                    // inserts into avoidTable
                    insertIntoTable(tableRec, avoidTable);
                }
            }
            
            // adds all children to the end of queueOpen
            if (recNodeToInvestigate.getAllChildren() != null) {
                queueOpen.addAll(recNodeToInvestigate.getAllChildren());
            }
        }
    }

    /**
     * Inserts record into breakTable or into avoidTable
     */
    private void insertIntoTable(float[] tableRec, Vector<float[]> table) {
        
        boolean inserted = false;
        for (int i=0; i<table.size(); i++) {
            if (tableRec[0] < table.get(i)[0]) {
                table.add(i, tableRec);
                inserted = true;
                break;
            }
        }
        if (!inserted) table.add(tableRec);
    }
    
    /**
     * Deletes items in Avoid table that are higher than "biggerThan" of the page height
     */
    private void deleteAvoidsBiggerThan(float biggerThan) {
        for (int i=0; i<avoidTable.size(); i++) {
            
            if (avoidTable.get(i)[1]-avoidTable.get(i)[0] > biggerThan*pageFormat.getHeight())
                avoidTable.remove(i);   
        }
    }
    
    /**
     * Merges avoid interval that are overlapping
     */
    private void mergeAvoids(float biggerThan) {
    	// goes through table
        for (int i=1; i<avoidTable.size(); i++) {
            // tests if intervals in records are overlapping
            if (avoidTable.get(i-1)[1] > avoidTable.get(i)[0]) {
                // tests size of interval if it is not larger than allowed
                if (avoidTable.get(i)[1]-avoidTable.get(i-1)[0] > biggerThan*pageFormat.getHeight()) {

                    avoidTable.remove(i);
                    i--;
                }
                // merges overlapping records
                else {
                	
                    if (avoidTable.get(i-1)[1] < avoidTable.get(i)[1]) avoidTable.get(i-1)[1] = avoidTable.get(i)[1];               
                    avoidTable.remove(i);
                    i--;
                }
            }
        }
    } 

    /**
     * Updates all tables by moving all break/avoid lines 
     */
    private void updateTables(float moveBy) {
        
        // moves all records in breakTable
        for (int i=0; i<breakTable.size(); i++) {
            
            breakTable.get(i)[0] += moveBy;
            breakTable.get(i)[1] += moveBy;          
            breakTable.get(i)[2] += moveBy;
        }
        // moves all records in avoidTable
        for (int i=0; i<avoidTable.size(); i++) {
            
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
     * @return the Node
     */
    private Node getElementAbove(Node recentNode) {
 
        if (recentNode == null) return null;
        Node nParent = recentNode.getParentNode();
        if (nParent == null) return null;
        Vector <Node> nChildren = nParent.getAllChildren();     
        if (nChildren == null) return null;
        
        Node nodeX = null;
        // goes through whole TREE
        while (nChildren.size()>0) {
            
            Node temp = nChildren.firstElement();
            nChildren.remove(0);
       
            // if recent child's ID is equal to original nod's ID - continue
            if (recentNode.getID() == temp.getID()) continue;
            
            // if the child is not above - continue
            if (temp.getElemY()*resCoef+temp.getPlusOffset()+temp.getElemHeight()*resCoef+temp.getPlusHeight() > recentNode.getElemY()*resCoef+recentNode.getPlusOffset())
                continue;
            
            if (nodeX == null) nodeX = temp;
            else if (nodeX.getElemY()*resCoef+nodeX.getPlusOffset()+nodeX.getElemHeight()*resCoef+nodeX.getPlusHeight() <= temp.getElemY()*resCoef+temp.getPlusOffset()+temp.getElemHeight()*resCoef+temp.getPlusHeight()) {
                nodeX=temp;
            }  
        }
        return nodeX;
    }

    /**
     * Finds the element below element
     * @return the Node
     */
    private Node getElementBelow(Node recentNode) {
        
        if (recentNode == null) return null;
        // gets Vector of all parents children (including the node itself)
        Node nParent = recentNode.getParentNode();
        if (nParent == null) return null;       
        Vector<Node> nChildren = nParent.getAllChildren();
        
        if (nChildren == null) return null;     
        Node wantedNode = null;
        
        // goes through all children and search for node below the node given
        while (nChildren.size()>0) {
                
            // gets first element from Vector
            Node temp = nChildren.firstElement();
            nChildren.remove(0);
            
            // continues if recent node is the same as the original node
            if (recentNode.getID() == temp.getID()) continue;
            
            // new candidate is not under recent node
            if (temp.getElemY()*resCoef+temp.getPlusOffset() < recentNode.getElemY()*resCoef+ recentNode.getElemHeight()*resCoef + recentNode.getPlusHeight() + recentNode.getPlusOffset()) { 
                continue;
            }
            
            // wantedNode gets new reference if it has not one yet or the old node
            //		contains element with lower position then new candidate
            if (wantedNode == null) wantedNode = temp;
            else if (wantedNode.getElemY()*resCoef+wantedNode.getPlusOffset() >= temp.getElemY()*resCoef+temp.getPlusOffset()) {
                wantedNode=temp;
            }  
        }
        return wantedNode;
    }
    
    /**
     * Finds the top of first child element in Node
     * @return the resized distance from top of the document or -1 for not null argument
     */
    private float getFirstTop(Node recentNode) {

        if (recentNode == null) return -1;
        Vector <Node> nChildren = recentNode.getAllChildren();      
        if (nChildren == null) return recentNode.getElemY()*resCoef+recentNode.getPlusOffset();
        
        float vysledekNeelem = Float.MAX_VALUE;
        float vysledekElem = Float.MAX_VALUE;
        
        // goes through subTREE and searches for first not-ElementBox element
        //		- in case it doesn't contain any not-ElementBox element, it would pick first ElementBox element
        Vector <Node> subTree = nChildren;
        
        while (subTree.size() > 0) {
            
            Node aktualni = subTree.firstElement();
            subTree.remove(0);
            Vector <Node> subChildren = aktualni.getAllChildren();
            if (subChildren != null) subTree.addAll(subChildren);
            
            if (aktualni.isElem()) {
                if (aktualni.getElemY()*resCoef+aktualni.getPlusOffset()<vysledekElem) {
                    vysledekElem = aktualni.getElemY()*resCoef+aktualni.getPlusOffset();
                }
            }
            else {
                if (aktualni.getElemY()*resCoef+aktualni.getPlusOffset()<vysledekNeelem) {
                    vysledekNeelem = aktualni.getElemY()*resCoef+aktualni.getPlusOffset();
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
     * @return the resized distance from top of the document
     */
    private float getLastBottom(Node recentNode) {

        if (recentNode == null) return -1;
        Vector <Node> nChildren = recentNode.getAllChildren();      
        if (nChildren == null) return recentNode.getElemY()*resCoef+recentNode.getElemHeight()*resCoef+recentNode.getPlusOffset()+recentNode.getPlusHeight();
        
        float vysledekNeelem = -Float.MAX_VALUE;
        float vysledekElem = -Float.MAX_VALUE;
        
        // goes through subTREE and searches for last not-ElementBox element
        //		- in case it doesn't contain any not-ElementBox element, it would pick last ElementBox element
        Vector <Node> subTree = nChildren;
        
        while (subTree.size() > 0) {
            
            Node aktualni = subTree.firstElement();
            subTree.remove(0);
            Vector <Node> subChildren = aktualni.getAllChildren();
            if (subChildren != null) subTree.addAll(subChildren);
            
            if (aktualni.isElem()) {
                if (aktualni.getElemY()*resCoef+aktualni.getElemHeight()*resCoef+aktualni.getPlusOffset()+aktualni.getPlusHeight()>vysledekElem)
                    vysledekElem = aktualni.getElemY()*resCoef+aktualni.getElemHeight()*resCoef+aktualni.getPlusOffset()+aktualni.getPlusHeight();
            }
            else {
                if (aktualni.getElemY()*resCoef+aktualni.getElemHeight()*resCoef+aktualni.getPlusOffset()+aktualni.getPlusHeight()>vysledekNeelem)
                    vysledekNeelem = aktualni.getElemY()*resCoef+aktualni.getElemHeight()*resCoef+aktualni.getPlusOffset()+aktualni.getPlusHeight();                
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
    private void makeBreakAt(float line1) {

        if (line1>rootHeight*resCoef || line1 < 0) return;
        
        float spaceBetweenLines = 0;
        
        line1 -= outputBottomPadding;

        // goes through TREE end finds set of all non-ElementbBox elements which are crossed by the line1
        // 		- picks one element from this set, which has the lowest distance from the top of the page
        Vector<Node> myOpen = new Vector<Node>(2);
        myOpen.add(rootNodeOfTree);
        
        float line2 = line1;
        while (myOpen.size()>0) {
        
            Node myRecentNode = myOpen.firstElement();
            myOpen.remove(0);
            Vector<Node> myChildren = myRecentNode.getAllChildren();
            if (myChildren != null) { myOpen.addAll(myChildren); }

            float startOfTheElement = myRecentNode.getElemY()*resCoef + myRecentNode.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode.getElemHeight()*resCoef + myRecentNode.getPlusHeight();
            
            // sets the line2 variable to match the top of the element from set
            //		which has the lowest distance from the top of the document
            if (!myRecentNode.isElem()) {
                if (startOfTheElement < line1 && endOfTheElement > line1) {
                    if (startOfTheElement < line2) {
                        
                        line2 = startOfTheElement;
                    }
                }
            }
        }        
        
        // counts line3
        Vector<Node> myOpen2 = new Vector<Node>(2);
        myOpen2.add(rootNodeOfTree);
        
        float line3 = line2;
        while (myOpen2.size()>0) {
        
            Node myRecentNode2 = myOpen2.firstElement();
            myOpen2.remove(0);
            Vector<Node> myChildren2 = myRecentNode2.getAllChildren();
            if (myChildren2 != null) { myOpen2.addAll(myChildren2); }

            float startOfTheElement = myRecentNode2.getElemY()*resCoef + myRecentNode2.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode2.getElemHeight()*resCoef + myRecentNode2.getPlusHeight();
            
            // counts the line3
            if (!myRecentNode2.isElem()) {
                if (startOfTheElement < line2 && endOfTheElement > line2) {
                    if (startOfTheElement < line3) {
                        
                        line3 = startOfTheElement;
                    }
                }
            }
        }

        // counts distance between lines
        spaceBetweenLines = (float) (pageFormat.getHeight()*Math.ceil((line1-1)/pageFormat.getHeight()) - line3);
        
        // goes through TREE and increases height or moves element        
        Vector<Node> myOpen3 = new Vector<Node>(2);
        myOpen3.add(rootNodeOfTree);

        while (myOpen3.size()>0) {
        
            Node myRecentNode = myOpen3.firstElement();
            myOpen3.remove(0);
            Vector<Node> myChildren = myRecentNode.getAllChildren();
            if (myChildren != null) { myOpen3.addAll(myChildren); }

            // counts start and end of the element
            float startOfTheElement = myRecentNode.getElemY()*resCoef + myRecentNode.getPlusOffset();
            float endOfTheElement = startOfTheElement + myRecentNode.getElemHeight()*resCoef + myRecentNode.getPlusHeight()-10*resCoef;
            
            // whole element if above the line2 - nothing happens
            if (endOfTheElement <= line2) {}
            // increases the height of element which:
            //		- is ElementBox
            //		- is crossed by the line2
            //		- has got at least 2 children
            else if (myRecentNode.isElem() && myRecentNode.getElemY()*resCoef + myRecentNode.getPlusOffset() < line2 && myRecentNode.getElemY()*resCoef + myRecentNode.getPlusOffset() + myRecentNode.getElemHeight()*resCoef + myRecentNode.getPlusHeight() >= line2
            		&& myRecentNode.getAllChildren() != null) {    	
            	myRecentNode.addPlusHeight(outputTopPadding + spaceBetweenLines + outputBottomPadding);      
            }
            // moves element in one of following cases:
            //			- element is completely below the line2
            //			- element is crossing line2 and is not ElementBox
            else {
                myRecentNode.addPlusOffset(outputTopPadding+spaceBetweenLines+outputBottomPadding);
            }
        }
    
        // updates height of the original document
        this.rootHeight += (outputTopPadding+spaceBetweenLines+outputBottomPadding)/resCoef;
        // updates values in all records in avoidTable and breakTable
        updateTables(outputTopPadding+spaceBetweenLines+outputBottomPadding);
        // update count the number of pages
        this.pageCount = (int)Math.ceil(rootHeight*resCoef/pageFormat.getHeight());       
    }

    ////////////////////////////////////////////////////////////////////////
    // INSERTING TO PDF
    //
    // - gets data describing each element from data structures and calls
    //		appropriate function to transform and write data to PDF document
    ////////////////////////////////////////////////////////////////////////
    
    /**
     * Writing elements to pages in PDF
     */
    private void writeAllElementsToPDF() {
        
    	// goes through all pages in PDF and inserts to all elements to current page
        for (int i=0; i<pageCount; i++) {        
            changeRecentPageToPDFBox(i);
            
            Vector<Node> elementsToWriteToPDF = new Vector<Node>(2);
            elementsToWriteToPDF.add(rootNodeOfList);
            while (elementsToWriteToPDF.size()>0) {
                
                // get first element from Vector
                Node recentNode = elementsToWriteToPDF.firstElement();
                elementsToWriteToPDF.remove(0);
                
                // get all children of recentNode and add to elementsToWriteToPDF
                Vector<Node> allChildren = recentNode.getAllChildren();
                if (allChildren != null) elementsToWriteToPDF.addAll(allChildren);
  
                // inserts elem data to PDF
                if (recentNode.isElem())  {
                    ElementBox elem = recentNode.getElem();
                    
                    // draws colored background
                    drawBgToElem(elem, i, recentNode.getTreeEq().getPlusOffset(), recentNode.getTreeEq().getPlusHeight());
                   
                    // draws background image
                    if (elem.getBackgroundImages() != null && elem.getBackgroundImages().size() > 0) {
                        insertBgImg(elem, i, recentNode.getTreeEq().getPlusOffset(), recentNode.getTreeEq().getPlusHeight());
                    }
                    
                    // draws border
                    drawBorder(elem, i, recentNode.getTreeEq().getPlusOffset(), recentNode.getTreeEq().getPlusHeight());
                }
                
                // inserts text to PDF
                if (recentNode.isText()) {
               	
                	// draws the text if it is not overlapping the parent element more then 60 %
                	//		on the right side
                	Node parent = recentNode.getTreeEq().getParentNode().getParentNode();
                	float parentRightEndOfElement = (parent.getElemX() + parent.getElemWidth())*resCoef;
                	float recentRightEndOfElement = (recentNode.getElemX() + recentNode.getElemWidth())*resCoef;
                	float widthRecentElem = recentNode.getElemWidth()*resCoef;
                	
                	if (parentRightEndOfElement-recentRightEndOfElement > -widthRecentElem*0.6) {
	                	TextBox text = recentNode.getText();
	                    if (text.isEmpty() || !text.isVisible() || !text.isDeclaredVisible() || !text.isDisplayed()) continue;
	                    insertText(text, i, recentNode.getTreeEq().getPlusOffset(), recentNode.getTreeEq().getPlusHeight());
                	}
                }
                
                // inserts box data to PDF
                if (recentNode.isBox()) {
                    ReplacedBox box = recentNode.getBox();
                    insertImg(box, i, recentNode.getTreeEq().getPlusOffset(), recentNode.getTreeEq().getPlusHeight());
                }
            } 
        }
    }
    
    /**
     * Draws image gained from <img> tag to OUTPUT
     */
    private void insertImg (ReplacedBox box, int i, float plusOffset, float plusHeight) {
        
        ReplacedContent cont = box.getContentObj();
        if (cont != null) {
            if (cont instanceof ReplacedImage) {
                BufferedImage img = ((ReplacedImage) cont).getBufferedImage();
                float pageStart = i*pageFormat.getHeight();
                float pageEnd = (i+1)*pageFormat.getHeight();
                
                Rectangle cb = ((Box) box).getAbsoluteContentBounds();
                if (img != null && cb.y*resCoef < pageEnd && (cb.y+img.getHeight())*resCoef+plusHeight+plusOffset > pageStart) {
            
                    // calculates resized coordinates in CSSBox form
                    float startX = cb.x*resCoef;
                    float startY = (cb.y*resCoef+plusOffset+plusHeight)-i*pageFormat.getHeight();
                    float width = (float)cb.getWidth()*resCoef;
                    float height = (float)cb.getHeight()*resCoef+plusHeight;                 
                    
                    // inserts image
                    insertImagePDFBox(img, startX, startY, width, height);                            
                }
            }
        }
    }    
    
    /**
     * Draws element background image to OUTPUT
     */
    private void insertBgImg (ElementBox elem, int i, float plusOffset, float plusHeight) {
    
        for (BackgroundImage bimg : elem.getBackgroundImages()) {
            BufferedImage img = bimg.getBufferedImage();
            float pageStart = i*pageFormat.getHeight();
            float pageEnd = (i+1)*pageFormat.getHeight();
            if (img != null && elem.getAbsoluteContentY()*resCoef+plusOffset < pageEnd && (elem.getAbsoluteContentY()+img.getHeight())*resCoef+plusOffset+plusHeight > pageStart) {
                
                // calculates resized coordinates in CSSBox form
                float startX = (elem.getAbsoluteContentX()-elem.getPadding().left)*resCoef;
                float startY = (elem.getAbsoluteContentY()-elem.getPadding().top)*resCoef+plusOffset-i*pageFormat.getHeight();
                float width = img.getWidth()*resCoef;
                float height = img.getHeight()*resCoef;
                
                // correction of long backgrounds
                if (height > 5*plusHeight) height += plusHeight;
                
                // inserts image
                insertImagePDFBox(img, startX, startY, width, height);
            }
        }
    }
    
    /**
     * Draws border to OUTPUT
     * 
     * @returns 0 for inserted OK, -1 for exception occurs and 1 for border out of page
     */
    private int drawBorder(ElementBox elem, int i, float plusOffset, float plusHeight) {

        final LengthSet border = elem.getBorder();
        if (border.top > 0 || border.right > 0 || border.bottom > 0 || border.right > 0)
        {
        	// counts the distance between top of the document and the start/end of the page
            final float pageStart = i*pageFormat.getHeight();
            final float pageEnd = (i+1)*pageFormat.getHeight();
            
            // checks if border is not completely out of page
            if (elem.getAbsoluteContentY()*resCoef+plusOffset > pageEnd || (elem.getAbsoluteContentY()+plusOffset+elem.getContentHeight())*resCoef+plusHeight+plusOffset < pageStart)
                return 1;
    
            // calculates resized X,Y coordinates in CSSBox form
            final float border_x = elem.getAbsoluteContentX()*resCoef;
            final float border_y = pageFormat.getHeight() - (elem.getAbsoluteContentY()*resCoef+plusOffset)+i*pageFormat.getHeight() - elem.getContentHeight()*resCoef-plusHeight;     
    
            // calculates the padding for each side
            final float paddingTop =    elem.getPadding().top*resCoef;
            final float paddingRight =  elem.getPadding().right*resCoef;
            final float paddingBottom = elem.getPadding().bottom*resCoef;
            final float paddingLeft =   elem.getPadding().left*resCoef;
            
            // calculates the border size for each side
            final float borderTopSize =    border.top*resCoef;
            final float borderRightSize =  border.right*resCoef;
            final float borderBottomSize = border.bottom*resCoef;          
            final float borderLeftSize =   border.left*resCoef;      
    
            // calculate the element size
            final float elemWidth =  elem.getContentWidth()*resCoef;
            final float elemHeight = elem.getContentHeight()*resCoef+plusHeight;
    
            float bX, bY, bWidth, bHeight;
            try {
    
                // left border
                if (borderLeftSize > 0)
                {
                    bX = border_x-borderLeftSize-paddingLeft;
                    bY = border_y-borderBottomSize-paddingBottom;
                    bWidth = borderLeftSize;
                    bHeight = elemHeight+borderTopSize+borderBottomSize+paddingTop+paddingBottom;
                    drawRectanglePDFBox(borderLeftSize, getBorderColor(elem, "left"), bX, bY, bWidth, bHeight);
                }
                
                // right border
                if (borderRightSize > 0)
                {
                    bX = border_x+elemWidth+paddingRight;
                    bY = border_y-borderBottomSize-paddingBottom;
                    bWidth = borderRightSize;
                    bHeight = elemHeight+borderTopSize+borderBottomSize+paddingTop+paddingBottom;
                    drawRectanglePDFBox(borderRightSize, getBorderColor(elem, "right"), bX, bY, bWidth, bHeight);
                }
                
                // top border
                if (borderTopSize > 0)
                {
                	bX = border_x-borderLeftSize-paddingLeft;
                	bY = border_y+elemHeight+paddingTop;
                	bWidth = elemWidth+borderLeftSize+borderRightSize+paddingLeft+paddingRight;
                	bHeight = borderTopSize;
                    drawRectanglePDFBox(borderTopSize, getBorderColor(elem, "top"), bX, bY, bWidth, bHeight);
                }
                
                // bottom border
                if (borderBottomSize > 0)
                {
                    bX = border_x-borderLeftSize-paddingLeft;
                    bY = border_y-borderBottomSize-paddingBottom;
                    bWidth = elemWidth+borderLeftSize+borderRightSize+paddingLeft+paddingRight;
                    bHeight = borderBottomSize;
                    drawRectanglePDFBox(borderBottomSize, getBorderColor(elem, "bottom"), bX, bY, bWidth, bHeight);
                }
            
            }
            catch (Exception e) {
                
                e.printStackTrace();
                return -1;
            }
        }            
        return 0; 
    }

    /**
     * Draws colored background to OUTPUT
     * 
     * @returns 0 for inserted OK, -1 for exception occurs and 1 for element completely out of page
     */
    private int drawBgToElem(ElementBox elem, int i, float plusOffset, float plusHeight) {
        
        // checks if any color available
        if (elem.getBgcolor() == null) return 0;
        
        // for root element the background color will be painted to background of whole page
        if (elem.getParent() == null)
            return drawBgToWholePagePDFBox(elem.getBgcolor());
        
        // calculates the start and the end of current page
        float pageStart = i*pageFormat.getHeight();
        float pageEnd = (i+1)*pageFormat.getHeight();
        
        // checks if the element if completely out of page
        if (elem.getAbsoluteContentY()*resCoef+plusOffset > pageEnd || (elem.getAbsoluteContentY()+elem.getContentHeight())*resCoef+plusOffset+plusHeight < pageStart)
            return 1;

        // calculates the padding
        float paddingTop =    elem.getPadding().top*resCoef;
        float paddingRight =  elem.getPadding().right*resCoef;
        float paddingBottom = elem.getPadding().bottom*resCoef;
        float paddingLeft =   elem.getPadding().left*resCoef;
        
        try {
            
            float border_x = elem.getAbsoluteContentX()*resCoef-paddingLeft;
            float border_y = pageFormat.getHeight() - (elem.getAbsoluteContentY()*resCoef+plusOffset)+i*pageFormat.getHeight() - elem.getContentHeight()*resCoef-plusHeight-paddingBottom;    
                            
            drawRectanglePDFBox (0, elem.getBgcolor(), border_x, border_y, (elem.getContentWidth())*resCoef+paddingLeft+paddingRight, elem.getContentHeight()*resCoef+paddingTop+paddingBottom+plusHeight);
            
       } catch (Exception e){
           
           	e.printStackTrace();
            return -1;
       }
       return 0;
    }
    
    /**
     * Draws text to OUTPUT
     * 
     * @returns 0 for inserted ok, -1 for exception occures and 1 for text out of page
     */
    private int insertText(TextBox text, int i, float plusOffset, float plusHeight) {
        
    	// counts the distance between top of the document and the start/end of the page
        float pageStart = i*pageFormat.getHeight();
        float pageEnd = (i+1)*pageFormat.getHeight();
        
        // checks if the whole text is out of the page
        if (text.getAbsoluteContentY()*resCoef+plusOffset > pageEnd || (text.getAbsoluteContentY()+text.getHeight())*resCoef+plusOffset < pageStart)
            return 1;
        
        // gets data describing the text
        VisualContext ctx = text.getVisualContext();
        float fontSize = ctx.getFont().getSize()*resCoef;
        boolean isBold = ctx.getFont().isBold();
        boolean isItalic = ctx.getFont().isItalic();
        boolean isUnderlined = ctx.getTextDecorationString().equals("underline");
        String fontFamily = ctx.getFont().getFamily();
        Color color = ctx.getColor();
        
        // if font is not in fontTable we load it
        PDFont font = null;
        for (int iter=0; iter<fontTable.size(); iter++) {

        	if (fontTable.get(iter).fontName.equalsIgnoreCase(fontFamily) && fontTable.get(iter).isItalic == isItalic && fontTable.get(iter).isBold == isBold) 
        		font = fontTable.get(iter).loadedFont;
        }
        if (font == null) {
        	font = setFont(fontFamily, isItalic, isBold);
        	fontTable.add(new fontTableRecord(fontFamily, isBold, isItalic, font));
        }
        
        font.setFontEncoding(new PdfDocEncoding());
        
        // replaces several characters with UNICODE encoding with UTF-8 equivalent
        // 		- not needed in Apache PDFBox 2.0.0
        String textToInsert = replaceNotSupportedUnicodeChars(text.getText());
        
        try {    	
            content.setNonStrokingColor(color);
            float leading = 2f * fontSize; 
            
            // counts the resized coordinates in CSSBox form
            float startX = text.getAbsoluteContentX()*resCoef;
            float startY = (text.getAbsoluteContentY()*resCoef+plusOffset)%pageFormat.getHeight();

            // writes to PDF
            writeTextPDFBox(startX, startY, textToInsert, font, fontSize, isUnderlined, isBold, leading);

       } catch (Exception e) {
           
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
    //		Apache PDFBox functions
    /////////////////////////////////////////////////////////////////////////
    
    /**
     * Saves the PDF document to disk using PDFBox
     */
    private int saveDocPDFBox() {
        
        try {
            content.close();
            doc.save(pathToSave);
            doc.close();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }
    
    /**
     * Creates document witch first page in it using PDFBox
     */
    private int createDocPDFBox() {

        try{
            doc = new PDDocument();
            page = new PDPage(pageFormat);
            doc.addPage(page);
            content = new PDPageContentStream(doc, page);
            mediabox = page.findMediaBox();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }
    
    /**
     * Inserts N pages to PDF document using PDFBox
     */
    private int insertNPagesPDFBox(int pageCount) {
        
        for (int i=1; i<pageCount; i++) {
            try {                 
                page = new PDPage(pageFormat);
                doc.addPage(page);
                content = new PDPageContentStream(doc, page);
                mediabox = page.findMediaBox();
            } 
            
            catch (IOException e) { 
                e.printStackTrace();
                return -1;
            }
        }
        return 0;
    }
    
    /**
     * Changes recent page using PDFBox
     */
    private int changeRecentPageToPDFBox(int i) {
        
        page = (PDPage)doc.getDocumentCatalog().getAllPages().get(i);
        
        try {
            content.close();
            content = new PDPageContentStream(doc,page, true, true);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }
    
    /**
     * Inserts background to whole recent PDF page using PDFBox
     */
    private int drawBgToWholePagePDFBox(Color bgColor) {
        
        try {
            content.setNonStrokingColor(bgColor);
            content.fillRect(0, 0, pageFormat.getWidth(), pageFormat.getHeight());
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }
    
    /**
     * Inserts rectangle to recent PDF page using PDFBox
     */
    private int drawRectanglePDFBox (float lineWidth, Color bgColor, float x, float y, float width, float height) {

    	if (bgColor == null) return 1;
        try {
            content.setLineWidth(lineWidth);
            content.setNonStrokingColor(bgColor);
            content.fillRect(x, y, width, height);
        } catch (IOException e) {

            e.printStackTrace();
            return -1;
        }
        return 0;
    }
    
    /**
     * Inserts image to recent PDF page using PDFBox
     */
    private int insertImagePDFBox (BufferedImage img, float x, float y, float width, float height) {
    	// transform X,Y coordinates to Apache PDFBox format
        y = pageFormat.getHeight() - height - y;

        try {
            PDXObjectImage ximage = new PDPixelMap(doc, img);
            // insert in PDF
            content.drawXObject(ximage, x, y, width, height);
        } catch (IOException e) {
        	e.printStackTrace();
            return -1;	
        }
        return 0;
    }
    
    /**
     * Writes String to recent PDF page using PDFBox
     */
    private int writeTextPDFBox(float x, float y, String textToInsert, PDFont font, float fontSize, boolean isUnderlined, boolean isBold, float leading) {
                   
        // transform X,Y coordinates to Apache PDFBox format
        y = pageFormat.getHeight() - y - leading*resCoef;

        try {
            content.beginText();
            content.setFont(font, fontSize);
            content.moveTextPositionByAmount(x, y);
            content.drawString(textToInsert);
            content.endText();

            // underlines text if text is set underlined
            if (isUnderlined) {
                
                content.setLineWidth(1);
                float strokeWidth = font.getStringWidth(textToInsert) / 1000 * fontSize;
                float lineHeightCalibration = 1f;
                float yOffset = fontSize/6.4f;
                if (isBold) {
                    lineHeightCalibration = 1.5f;
                    yOffset = fontSize/5.7f;
                }
                
                content.fillRect(x, y-yOffset, strokeWidth, resCoef*lineHeightCalibration);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * Creates object describing font
     * @return the font object
     */
    private PDFont setFont (String fontFamily, boolean isItalic, boolean isBold) {
        
        PDFont font;
        // tries to load font from given folder
        try {
            if (isBold && isItalic) { font = PDTrueTypeFont.loadTTF(doc, pathToTTFFonts + fontFamily + " Bold Italic.ttf"); }
            else if (isBold) { font = PDTrueTypeFont.loadTTF(doc, pathToTTFFonts + fontFamily + " Bold.ttf"); }
            else if (isItalic) { font = PDTrueTypeFont.loadTTF(doc, pathToTTFFonts + fontFamily + " Italic.ttf"); }
            else { font = PDTrueTypeFont.loadTTF(doc, pathToTTFFonts + fontFamily + ".ttf"); }
        }
        // if not successful load the font from Apache PDFBox
        catch (IOException e) {
        
        	fontFamily = fontFamily.toLowerCase();
	        switch (fontFamily) {
	        case "courier":
	        case "courier new":
	        case "lucida console":
	            if (isBold && isItalic) { font = PDType1Font.COURIER_BOLD_OBLIQUE;}
	            else if (isBold) { font = PDType1Font.COURIER_BOLD;}
	            else if (isItalic) { font = PDType1Font.COURIER_OBLIQUE;}
	            else { font = PDType1Font.COURIER;}
	            break;
	        case "times":
	        case "garamond":
	        case "georgia":
	        case "times new roman":
	        case "serif":
	            if (isBold && isItalic) { font = PDType1Font.TIMES_BOLD_ITALIC;}
	            else if (isBold) { font = PDType1Font.TIMES_BOLD;}
	            else if (isItalic) { font = PDType1Font.TIMES_ITALIC;}
	            else { font = PDType1Font.TIMES_ROMAN;}
	            break;
	        default:
	            if (isBold && isItalic) { font = PDType1Font.HELVETICA_BOLD_OBLIQUE;}
	            else if (isBold) { font = PDType1Font.HELVETICA_BOLD;}
	            else if (isItalic) { font = PDType1Font.HELVETICA_OBLIQUE;}
	            else { font = PDType1Font.HELVETICA;}
	            break;
	        }
        }
        return font;
    }

    /////////////////////////////////////////////////////////////////////
    // OTHER FUNCTIONS
    /////////////////////////////////////////////////////////////////////
    
    /**
     * Returns color of border
     */
    private Color getBorderColor(ElementBox elem, String side) {

        Color clr = null;
        // gets the color value from CSS property
        CSSProperty.BorderColor bclr = elem.getStyle().getProperty("border-"+side+"-color");
        TermColor tclr = elem.getStyle().getValue(TermColor.class, "border-"+side+"-color");
        CSSProperty.BorderStyle bst = elem.getStyle().getProperty("border-"+side+"-style");

        if (bst != CSSProperty.BorderStyle.HIDDEN && bclr != CSSProperty.BorderColor.TRANSPARENT) {
            if (tclr != null) clr = tclr.getValue();
            
            if (clr == null) {
                clr = elem.getVisualContext().getColor();
                if (clr == null) clr = Color.BLACK;
            }
        }
        else { clr = elem.getBgcolor(); }
        
        return clr;
    }
    
    /**
     * Replaces several characters with UNICODE code higher then 0xFF
     *      with similar or equivalent alternative with UNICODE code 0xFF or lower
     * @return fixed String
     */
    private String replaceNotSupportedUnicodeChars(String text) {
        
        // removes diacritics
        text = text.replace("\u010f","d"); text = text.replace("\u010e","D");
        text = text.replace("\u011b","e"); text = text.replace("\u011a","E");
        text = text.replace("\u0161","s"); text = text.replace("\u0160","S");
        text = text.replace("\u010d","c"); text = text.replace("\u010c","C");         
        text = text.replace("\u0159","r"); text = text.replace("\u0158","R"); 
        text = text.replace("\u017e","z"); text = text.replace("\u017d","Z"); 
        text = text.replace("\u016F","u"); text = text.replace("\u016E","U");
        text = text.replace("\u0148","n"); text = text.replace("\u0147","N");
        text = text.replace("\u0165","t"); text = text.replace("\u0164","T");        
        text = text.replace("\u2013","\u002D");
        // replaces apostrophe with equivalent
        text = text.replace("\u2018", "\'"); text = text.replace("\u2019", "\'");
        text = text.replace("\u201a", "\'"); text = text.replace("\u201b", "\'");
        // replaces quote-marks with equivalent
        text = text.replace("\u201c", "\""); text = text.replace("\u201d", "\"");
        text = text.replace("\u201e", "\""); text = text.replace("\u201f", "\"");
        // replaces three-dots-mark with three dots
        text = text.replace("\u2026", "...");
        // replaces vertical line with equivalent
        text = text.replace("\u2502", "\u007C");
        // replaces em dash with dash
        text = text.replace("\u2014", "-");
        // less then, more than
        text = text.replace("\u2039", "<"); text = text.replace("\u203A", ">");
        // replaces bullet point with alternative
        text = text.replace("\u2022", "\u00B7");
        // removes slovak diacritics
        text = text.replace("\u013E", "l"); text = text.replace("\u013D", "L");
        text = text.replace("\u013A", "l"); text = text.replace("\u0139", "L");        
        text = text.replace("\u0151", "o"); text = text.replace("\u0150", "O");
        text = text.replace("\u0155", "r"); text = text.replace("\u0154", "R");        
        text = text.replace("\u0171", "u"); text = text.replace("\u0170", "U");
        
        return text;
    }
    
    /**
     * Sets the pathToTTFFonts variable
     */
    public void setFontPath(String path) {
    	this.pathToTTFFonts = path;
    }
}
