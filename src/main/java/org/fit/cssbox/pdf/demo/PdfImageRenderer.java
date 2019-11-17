/**
 * PdfImageRenderer.java
 * Copyright (c) 2015 Zbynek Cervinka
 *
 * PdfImageRenderer is an extension to CSSBox library.
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
 * Created on 16.5.2015, 8:26:29 by Zbynek Cervinka
 */
package org.fit.cssbox.pdf.demo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.fit.cssbox.css.CSSNorm;
import org.fit.cssbox.css.DOMAnalyzer;
import org.fit.cssbox.io.DOMSource;
import org.fit.cssbox.io.DefaultDOMSource;
import org.fit.cssbox.io.DefaultDocumentSource;
import org.fit.cssbox.io.DocumentSource;
import org.fit.cssbox.layout.BrowserConfig;
import org.fit.cssbox.layout.Dimension;
import org.fit.cssbox.pdf.PDFEngine;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import cz.vutbr.web.css.MediaSpec;

/**
 * This class provides a rendering interface for obtaining the document image
 * form an URL.
 * 
 * @author burgetr
 */
public class PdfImageRenderer
{
    private String mediaType = "screen";
    private Dimension windowSize;
    private boolean cropWindow = false;
    private boolean loadImages = true;
    private boolean loadBackgroundImages = true;

    public PdfImageRenderer()
    {
        windowSize = new Dimension(1200, 600);
    }
    
    public void setMediaType(String media)
    {
        mediaType = new String(media);
    }
    
    public void setWindowSize(Dimension size, boolean crop)
    {
        windowSize = new Dimension(size);
        cropWindow = crop;
    }
    
    public void setLoadImages(boolean content, boolean background)
    {
        loadImages = content;
        loadBackgroundImages = background;
    }
    
    /**
     * Renders the URL and prints the result to the specified output stream in the specified
     * format.
     * @param urlstring the source URL
     * @param out output stream
     * @return true in case of success, false otherwise
     * @throws SAXException 
     */
    public boolean renderURL(String urlstring, OutputStream out, String pageFormat) throws IOException, SAXException
    {
        if (!urlstring.startsWith("http:") &&
            !urlstring.startsWith("https:") &&
            !urlstring.startsWith("ftp:") &&
            !urlstring.startsWith("file:"))
                urlstring = "http://" + urlstring;
        
        //Open the network connection 
        DocumentSource docSource = new DefaultDocumentSource(urlstring);
      
        //Parse the input document
        DOMSource parser = new DefaultDOMSource(docSource);
        Document doc = parser.parse();
       
        //create the media specification
        MediaSpec media = new MediaSpec(mediaType);
        media.setDimensions(windowSize.width, windowSize.height);
        media.setDeviceDimensions(windowSize.width, windowSize.height);

        //Create the CSS analyzer
        DOMAnalyzer da = new DOMAnalyzer(doc, docSource.getURL());
        da.setMediaSpec(media);
        
        da.attributesToStyles(); //convert the HTML presentation attributes to inline styles
        da.addStyleSheet(null, CSSNorm.stdStyleSheet(), DOMAnalyzer.Origin.AGENT); //use the standard style sheet
        da.addStyleSheet(null, CSSNorm.userStyleSheet(), DOMAnalyzer.Origin.AGENT); //use the additional style sheet
        da.addStyleSheet(null, CSSNorm.formsStyleSheet(), DOMAnalyzer.Origin.AGENT); //render form fields using css
        da.getStyleSheets(); //load the author style sheets

        PDFEngine engine = new PDFEngine(pageFormat, da.getRoot(), da, docSource.getURL());
        engine.setAutoMediaUpdate(false); //we have a correct media specification, do not update
        engine.getConfig().setClipViewport(cropWindow);
        engine.getConfig().setLoadImages(loadImages);
        engine.getConfig().setLoadBackgroundImages(loadBackgroundImages);
        defineLogicalFonts(engine.getConfig());
        
        engine.createLayout(windowSize);
        engine.saveDocument(out);
        engine.closeDocument();

        docSource.close();

        return true;
    }
    
    /**
     * Sets some common fonts as the defaults for generic font families.
     */
    protected void defineLogicalFonts(BrowserConfig config)
    {
        config.setLogicalFont(BrowserConfig.SERIF, Arrays.asList("Times", "Times New Roman"));
        config.setLogicalFont(BrowserConfig.SANS_SERIF, Arrays.asList("Arial", "Helvetica"));
        config.setLogicalFont(BrowserConfig.MONOSPACE, Arrays.asList("Courier New", "Courier"));
    }
    
    //=================================================================================
    
    public static void main(String[] args)
    {
        
        if (args.length != 3 && !(args.length == 4 && args[2].equalsIgnoreCase("pdf"))) {
            
            System.err.println("Usage: PdfImageRenderer <url> <output_file> <pdf_page_format>");
            System.err.println();
            System.err.println("Renders a document at the specified URL and stores the resulting PDF");
            System.err.println("to the specified file.");
            System.err.println("pdf_page_format: optional parameter and only with argument pdf");
            System.err.println("Supported page formats: A0, A1, A2, A3, A4, A5, A6 or LETTER");
            System.exit(0);
        }
        
        try {
            FileOutputStream os = new FileOutputStream(args[1]);           
            PdfImageRenderer r = new PdfImageRenderer();
            
            if (args.length == 3)
                r.renderURL(args[0], os, args[2]);
            else
                r.renderURL(args[0], os, "");
   
            os.close();
            System.err.println("Done.");
        } catch (Exception e) {
            //System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
