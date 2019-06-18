package org.fit.cssbox.render;

import java.awt.Rectangle;

import org.fit.cssbox.layout.CSSDecoder;
import org.fit.cssbox.layout.ElementBox;

import cz.vutbr.web.css.TermLengthOrPercent;
import cz.vutbr.web.css.TermList;

/**
 * This class contains all the radiuses of each corners of the element's border.
 *  
 * @author Nguyen Hoang Duong
 */
public class BorderRadius {
	// corner radiuses
	public float topRightX;
	public float topRightY;
	public float topLeftX;
	public float topLeftY;
	public float botRightX;
	public float botRightY;
	public float botLeftX;
	public float botLeftY;
	
	/**
	 * The constructor, which sets the radiuses to the default 0 value.
	 * with an additional support for the data: URLs.
	 * 
	 */
	public BorderRadius() {
		topRightX = 0;
		topRightY = 0;
		topLeftX = 0;
		topLeftY = 0;
		botRightX = 0;
		botRightY = 0;
		botLeftX = 0;
		botLeftY = 0;
	}
	
	/**
	 * This function get the radius values from CSSBox engine, which are extracted from the CSS code 
	 * and set these values to each specific corner.
	 * @param topLeftCorner represents 2 values for x and y radius of top left corner of the border
	 * @param topRightCorner represents 2 values for x and y radius of top right corner of the border
	 * @param botLeftCorner represents 2 values for x and y radius of bottom left corner of the border
	 * @param botRightCorner represents 2 values for x and y radius of bottom right corner of the border
	 * @param elem the element with border-radius property
	 * @param resCoef the value which is used to resize the elements in different PDF page format
	 */
	public void setCornerRadius(TermList topLeftCorner, TermList topRightCorner, TermList botLeftCorner, TermList botRightCorner, ElementBox elem, float resCoef) {
		CSSDecoder dec = new CSSDecoder(elem.getVisualContext());
	    Rectangle bounds = elem.getAbsoluteBorderBounds();
	    if (topRightCorner != null) {
       		topRightX = dec.getLength((TermLengthOrPercent)topRightCorner.get(0), false, 0, 0, bounds.width / 2);
       		topRightY = dec.getLength((TermLengthOrPercent)topRightCorner.get(1), false, 0, 0, bounds.height / 2);
       		boolean isCycle = false;
       		if (topRightX == topRightY)
       			isCycle = true;
       		if (topRightX > bounds.width / 2) // radiuses must not be bigger than width and height of the element
       			topRightX = bounds.width / 2;
       		if (topRightY > bounds.height / 2)
       			topRightY = bounds.height / 2;
       		if (isCycle){ // when corner has cycle shape then radius will be the smaller one if one of them is bigger than width/height of the element 
       			if (topRightX < topRightY)
       				topRightY = topRightX;
       			else
       				topRightX = topRightY;
       		}
       		topRightX = topRightX * resCoef; // transform radiuses with ratio of page
       		topRightY = topRightY * resCoef;
       	}
       	if (topLeftCorner != null) {
       		topLeftX = dec.getLength((TermLengthOrPercent)topLeftCorner.get(0), false, 0, 0, bounds.width / 2);
       		topLeftY = dec.getLength((TermLengthOrPercent)topLeftCorner.get(1), false, 0, 0, bounds.height / 2);
       		boolean isCycle = false;
       		if (topLeftX == topLeftY)
       			isCycle = true;
       		if (topLeftX > bounds.width / 2)
       			topLeftX = bounds.width / 2;
       		if (topLeftY > bounds.height / 2)
       			topLeftY = bounds.height / 2;
       		if (isCycle){
       			if (topLeftX < topLeftY)
       				topLeftY = topLeftX;
       			else
       				topLeftX = topLeftY;
       		}
       		topLeftX = topLeftX * resCoef;
       		topLeftY = topLeftY * resCoef;
       	}
       	if (botRightCorner != null) {
       		botRightX = dec.getLength((TermLengthOrPercent)botRightCorner.get(0), false, 0, 0, bounds.width / 2);
       		botRightY = dec.getLength((TermLengthOrPercent)botRightCorner.get(1), false, 0, 0, bounds.height / 2);
       		boolean isCycle = false;
       		if (botRightX == botRightY)
       			isCycle = true;
       		if (botRightX > bounds.width / 2)
       			botRightX = bounds.width / 2;
       		if (botRightY > bounds.height / 2)
       			botRightY = bounds.height / 2;
       		if (isCycle){
       			if (botRightX < botRightY)
       				botRightY = botRightX;
       			else
       				botRightX = botRightY;
       		}
       		botRightX = botRightX * resCoef;
       		botRightY = botRightY * resCoef;
       	}
       	if (botLeftCorner != null) {
       		botLeftX = dec.getLength((TermLengthOrPercent)botLeftCorner.get(0), false, 0, 0, bounds.width / 2);
       		botLeftY = dec.getLength((TermLengthOrPercent)botLeftCorner.get(1), false, 0, 0, bounds.height / 2);
       		boolean isCycle = false;
       		if (botLeftX == botLeftY)
       			isCycle = true;
       		if (botLeftX > bounds.width / 2)
       			botLeftX = bounds.width / 2;
       		if (botLeftY > bounds.height / 2)
       			botLeftY = bounds.height / 2;
       		botLeftX = botLeftX * resCoef;
       		botLeftY = botLeftY * resCoef;
       		if (isCycle){
       			if (botLeftX < botLeftY)
       				botLeftY = botLeftX;
       			else
       				botLeftX = botLeftY;
       		}
       	}
	}
}
