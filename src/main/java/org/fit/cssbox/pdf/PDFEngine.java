/*
 * PDFEngine.java
 * Copyright (c) 2019 Radek Burget
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
 * Created on 17. 11. 2019, 16:57:03 by burgetr
 */

package org.fit.cssbox.pdf;

import java.io.OutputStream;
import java.net.URL;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.fit.cssbox.css.DOMAnalyzer;
import org.fit.cssbox.css.FontTable;
import org.fit.cssbox.layout.BrowserConfig;
import org.fit.cssbox.layout.Dimension;
import org.fit.cssbox.layout.Engine;
import org.fit.cssbox.layout.VisualContext;
import org.fit.cssbox.render.BoxRenderer;
import org.w3c.dom.Element;

/**
 * 
 * @author burgetr
 */
public class PDFEngine extends Engine
{
    private PDDocument doc;
    private PDPage page;
    private PDRectangle pageFormat;
    

    public PDFEngine(String pageFormat, Element root, DOMAnalyzer decoder, Dimension dim, URL baseurl)
    {
        super(root, decoder, dim, baseurl);
        initDocument(pageFormat);
    }

    public PDFEngine(String pageFormat, Element root, DOMAnalyzer decoder, URL baseurl)
    {
        super(root, decoder, baseurl);
        initDocument(pageFormat);
    }

    public PDDocument getDocument()
    {
        return doc;
    }

    public PDPage getPage()
    {
        return page;
    }

    public PDRectangle getPageFormat()
    {
        return pageFormat;
    }

    @Override
    protected VisualContext createVisualContext(BrowserConfig config, FontTable fontTable)
    {
        return new PDFVisualContext(getDocument(), null, config, fontTable);
    }

    @Override
    public BoxRenderer getRenderer()
    {
        //obtain the viewport bounds depending on whether we are clipping to viewport size or using the whole page
        float w = getViewport().getClippedContentBounds().width;
        float h = getViewport().getClippedContentBounds().height;
        return new PDFRenderer(w, h, doc);
    }

    //========================================================================================
    
    protected void initDocument(String format)
    {
        try
        {
            pageFormat = decodePageFormat(format);
            doc = new PDDocument();
            page = new PDPage(pageFormat);
            doc.addPage(page);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves the PDF document to the given output stream.
     */
    public void saveDocument(OutputStream out)
    {
        try
        {
            doc.save(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Closes the document.
     */
    public void closeDocument()
    {
        try
        {
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    protected PDRectangle decodePageFormat(String format)
    {
        PDRectangle ret;
        switch (format)
        {
            case "A0":
                ret = PDRectangle.A0;
                break;
            case "A1":
                ret = PDRectangle.A1;
                break;
            case "A2":
                ret = PDRectangle.A2;
                break;
            case "A3":
                ret = PDRectangle.A3;
                break;
            case "A4":
                ret = PDRectangle.A4;
                break;
            case "A5":
                ret = PDRectangle.A5;
                break;
            case "A6":
                ret = PDRectangle.A6;
                break;
            case "LETTER":
                ret = PDRectangle.LETTER;
                break;
            default:
                ret = PDRectangle.A4;
                break;
        }
        return ret;
    }
}
