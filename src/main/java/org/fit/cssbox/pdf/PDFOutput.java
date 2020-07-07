/*
 * PDFOutput.java
 * Copyright (c) 2020 Radek Burget
 *
 * CSSBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CSSBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License
 * along with CSSBox. If not, see <http://www.gnu.org/licenses/>.
 *
 * Created on 18. 4. 2020, 18:49:12 by burgetr
 */

package org.fit.cssbox.pdf;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

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
import org.apache.pdfbox.util.Matrix;

import cz.vutbr.web.csskit.Color;

/**
 * A representation of the output PDF document.
 * Extrernally, it works with CSSBox coordinates. Internally, it recomputes the coordinates to the PDF box scale.
 * 
 * @author burgetr
 */
public class PDFOutput implements Closeable
{
    private PDDocument doc;
    private PDPage page;
    private PDPageContentStream content;
    private PDRectangle pageFormat;
    
    /** The number of pages necessary for rendering the output */
    private int pageCount;
    
    /** Rendering area height in CSSBox units */
    private float rootHeight;
    
    /** The resolution ratio between the CSSBox units and the PDFBox units */
    private float resCoef;

    //========================================================================================
    
    /**
     * Creates a PDF output to a given document.
     * 
     * @param rootWidth the rendered area width
     * @param rootHeight the rendered area height
     * @param doc the output document to use for output
     */
    public PDFOutput(float rootWidth, float rootHeight, PDDocument doc)
    {
        this.doc = doc;
        this.page = doc.getPage(0);
        this.pageFormat = page.getMediaBox();
        // calculate resize coefficient
        resCoef = this.pageFormat.getWidth() / rootWidth;
        setRootHeight(rootHeight);
    }
    
    public int getPageCount()
    {
        return pageCount;
    }

    public void setPageCount(int pageCount)
    {
        this.pageCount = pageCount;
    }

    public float getRootHeight()
    {
        return rootHeight;
    }

    public void setRootHeight(float rootHeight)
    {
        this.rootHeight = rootHeight;
        // update the required number of pages
        pageCount = (int) Math.ceil(rootHeight * resCoef / this.pageFormat.getHeight());
    }

    /**
     * Creates an empty set of pages and starts the output.
     * 
     * @throws IOException
     */
    public void openStream() throws IOException
    {
        content = new PDPageContentStream(doc, page);
        insertPages(pageCount);
    }
    
    /**
     * Closes the output document.
     * 
     * @throws IOException 
     */
    @Override
    public void close() throws IOException
    {
        content.close();
    }

    /**
     * Inserts N pages to PDF document
     */
    private void insertPages(int pageCount)
    {
        for (int i = 1; i < pageCount; i++)
        {
            PDPage page = new PDPage(pageFormat);
            doc.addPage(page);
        }
    }
    
    /**
     * Computes the height of a single page in CSSBox scale.
     * 
     * @return the page height
     */
    public float getPageHeight()
    {
        return pageFormat.getHeight() / resCoef;
    }

    /**
     * Changes the current page.
     * @param pageIndex the index of the page to use
     * @throws IOException 
     */
    public void setCurrentPage(int pageIndex) throws IOException
    {
        page = (PDPage) doc.getDocumentCatalog().getPages().get(pageIndex);
        content.close();
        content = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
    }
    
    //========================================================================================
    
    public void drawRectangle(float lineWidth, Color bgColor, float x, float y, float width, float height)
            throws IOException
    {
        if (bgColor != null)
        {
            content.setLineWidth(lineWidth);
            setNonStrokingColor(bgColor);
            content.addRect(x * resCoef, y * resCoef, width * resCoef, height * resCoef);
            content.fill();
        }
    }

    public void drawCircle(float lineWidth, Color color, float cx, float cy, float r, boolean fill)
            throws IOException 
    {
        cx = cx * resCoef;
        cy = cy * resCoef;
        r = r * resCoef;
        
        final float k = 0.552284749831f;
        if (fill)
        {
            setNonStrokingColor(color);
        }
        else
        {
            content.setLineWidth(lineWidth);
            setStrokingColor(color);
        }
        content.moveTo(cx - r, cy);
        content.curveTo(cx - r, cy + k * r, cx - k * r, cy + r, cx, cy + r);
        content.curveTo(cx + k * r, cy + r, cx + r, cy + k * r, cx + r, cy);
        content.curveTo(cx + r, cy - k * r, cx + k * r, cy - r, cx, cy - r);
        content.curveTo(cx - k * r, cy - r, cx - r, cy - k * r, cx - r, cy);
        if (fill)
            content.fill();
        else
            content.stroke();
    }
    
    /**
     * Writes String to current PDF page using PDFBox.
     * 
     * @param x
     * @param y
     * @param textToInsert
     * @param font
     * @param fontSize
     * @param isUnderlined
     * @param isBold
     * @param letterSpacing
     * @param leading
     * @throws IOException 
     */
    public void writeText(float x, float y, String textToInsert, PDFont font, float fontSize,
            boolean isUnderlined, boolean isBold, float letterSpacing, float leading) throws IOException
    {
        // transform X,Y coordinates to Apache PDFBox format
        x = x * resCoef;
        y = y * resCoef;
        y = pageFormat.getHeight() - y - leading * resCoef;

        content.beginText();
        content.setFont(font, fontSize);
        content.setCharacterSpacing(letterSpacing);
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
    }
    
    /**
     * Inserts image to recent PDF page using PDFBox
     * @throws IOException 
     */
    public void insertImage(BufferedImage img, float x, float y, float width, float height) throws IOException
    {
        // transform X,Y coordinates to Apache PDFBox format
        x = x * resCoef;
        y = y * resCoef;
        width = width * resCoef;
        height = height * resCoef;
        y = pageFormat.getHeight() - height - y;

        // PDXObjectImage ximage = new PDPixelMap(doc, img);
        // content.drawXObject(ximage, x, y, width, height);
        PDImageXObject ximage = LosslessFactory.createFromImage(doc, img);
        content.drawImage(ximage, x, y, width, height);
    }
    
    /**
     * Inserts background to whole recent PDF page using PDFBox
     * @throws IOException 
     */
    private void fillPage(Color bgColor) throws IOException
    {
        setNonStrokingColor(bgColor);
        content.addRect(0, 0, pageFormat.getWidth(), pageFormat.getHeight());
        content.fill();
    }
    
    //========================================================================================
    
    public void saveGraphicsState() throws IOException
    {
        content.saveGraphicsState();
    }
    
    public void restoreGraphicsState() throws IOException
    {
        content.restoreGraphicsState();
    }
    
    public void addTransform(AffineTransform aff, float ox, float oy) throws IOException
    {
        Matrix matrix = new Matrix(aff);
        content.transform(Matrix.getScaleInstance(resCoef, resCoef)); //TODO check?
        content.transform(Matrix.getTranslateInstance(ox, oy));
        content.transform(matrix);
        content.transform(Matrix.getTranslateInstance(-ox, -oy));
    }
    
    //========================================================================================
    
    /**
     * Sets the stroking color for the content stream including the alpha channel.
     * @param color a CSS color to set
     * @throws IOException
     */
    private void setStrokingColor(Color color) throws IOException
    {
        content.setStrokingColor(toPDColor(color));
        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        graphicsState.setStrokingAlphaConstant(color.getAlpha() / 255.0f);
        content.setGraphicsStateParameters(graphicsState);
    }
    
    /**
     * Sets the non-stroking color for the content stream including the alpha channel.
     * @param color a CSS color to set
     * @throws IOException
     */
    private void setNonStrokingColor(Color color) throws IOException
    {
        content.setNonStrokingColor(toPDColor(color));
        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        graphicsState.setNonStrokingAlphaConstant(color.getAlpha() / 255.0f);
        content.setGraphicsStateParameters(graphicsState);
    }
    
    /**
     * Convetrs a CSSBox color to a PDFBox color.
     * @param color
     * @return
     */
    private PDColor toPDColor(Color color)
    {
        if (color == null)
            return null;
        float[] components = new float[] {
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f };
        return new PDColor(components, PDDeviceRGB.INSTANCE);
    }

    //========================================================================================

    private void drawBgGrad(float lineWidth, PDShadingType3 shading, float x, float y, float width, float height,
            Matrix matrix) throws IOException
    {
        if (shading == null)
            return;
        content.saveGraphicsState();
        content.setLineWidth(lineWidth);
        content.addRect(x, y, width, height);
        content.clip();
        content.transform(matrix);
        content.shadingFill(shading);
        content.fill();
        content.restoreGraphicsState();
    }    
}
