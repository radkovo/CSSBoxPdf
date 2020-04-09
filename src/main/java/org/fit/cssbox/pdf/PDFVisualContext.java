/*
 * PDFVisualContext.java
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
 * Created on 17. 11. 2019, 15:58:44 by burgetr
 */

package org.fit.cssbox.pdf;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.fit.cssbox.awt.GraphicsImageLoader;
import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.css.FontSpec;
import org.fit.cssbox.css.FontTable;
import org.fit.cssbox.layout.BrowserConfig;
import org.fit.cssbox.layout.FontInfo;
import org.fit.cssbox.layout.ImageLoader;
import org.fit.cssbox.layout.VisualContext;

import cz.vutbr.web.css.CSSProperty.FontStyle;
import cz.vutbr.web.css.CSSProperty.FontWeight;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.TermURI;

/**
 * A visual context implementation based on the PDFBox library.
 * 
 * @author burgetr
 */
public class PDFVisualContext extends VisualContext
{
    private PDDocument doc;
    private PDFont font;
    private String fontFamily; //the original font family before mapping to postscript fonts
    private boolean fontItalic;
    private boolean fontBold;
    private float letterSpacing;
    private float ex; // 1ex length in points
    private float ch; // 1ch length in points
    private FontCache fontCache; //font cache to store already created fonts
    
    /** Used image loader instance */
    private ImageLoader imageLoader;
    
    public PDFVisualContext(PDDocument doc, VisualContext parent, BrowserConfig config, FontTable fontTable)
    {
        super(parent, config, fontTable);
        this.doc = doc;
        this.font = PDType1Font.TIMES_ROMAN;
        this.fontFamily = "Times"; //this should be mapped to a reasonable Times font by the mapper
        if (parent == null)
            fontCache = new FontCache();
        else
            fontCache = ((PDFVisualContext) parent).getFontCache();
        updateMetrics();
    }

    @Override
    public VisualContext create()
    {
        PDFVisualContext ret = new PDFVisualContext(this.doc, this, getConfig(), getFontTable());
        ret.copyVisualContext(this);
        ret.updateMetrics();
        return ret;
    }

    @Override
    public void copyVisualContext(VisualContext src)
    {
        super.copyVisualContext(src);
        if (src instanceof PDFVisualContext)
        {
            font = ((PDFVisualContext) src).font;
            fontFamily = new String(((PDFVisualContext) src).fontFamily);
            fontItalic = ((PDFVisualContext) src).fontItalic;
            fontBold = ((PDFVisualContext) src).fontBold;
            letterSpacing = ((PDFVisualContext) src).letterSpacing;
            ex = src.getEx();
            ch = src.getCh();
        }
    }
    
    public FontCache getFontCache()
    {
        return fontCache;
    }

    @Override
    public void update(NodeData style)
    {
        super.update(style);
        updateMetrics();
    }
    
    public PDFont getFont()
    {
        return font;
    }

    public float pxFontSize()
    {
        return CSSUnits.pixels(getFontSize());
    }
    
    @Override
    public FontInfo getFontInfo()
    {
        return new FontInfo(fontFamily, getFontSize(), fontBold, fontItalic);
    }

    @Override
    public float getEx()
    {
        return ex;
    }

    @Override
    public float getCh()
    {
        return ch;
    }

    @Override
    public String getFontFamily()
    {
        return fontFamily;
    }

    @Override
    public float stringWidth(String text)
    {
        try
        {
            if (text.length() > 0)
            {
                final float sp = text.length() * CSSUnits.pixels(letterSpacing); //width added by additional letter spacing
                return font.getStringWidth(text) / 1000.0f * pxFontSize() + sp + 0.01f; // 0.01f for some rounding issues
            }
            else
                return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void setCurrentFont(String family, float size, FontWeight weight, FontStyle style, float spacing)
    {
        fontFamily = new String(family);
        fontItalic = (style == FontStyle.ITALIC || style == FontStyle.OBLIQUE);
        fontBold = FontSpec.representsBold(weight);
        letterSpacing = spacing;
        font = fontCache.get(fontFamily, fontBold, fontItalic);
        if (font == null) //not available in the cache but available system font
        {
            font = createFont(fontFamily, fontItalic, fontBold);
            fontCache.store(fontFamily, fontBold, fontItalic, font);
        }
    }

    @Override
    public float getFontHeight()
    {
        return font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * pxFontSize();
    }

    @Override
    public float getBaselineOffset()
    {
        return font.getFontDescriptor().getAscent() / 1000 * pxFontSize();
    }

    @Override
    protected String fontAvailable(String family, boolean isBold, boolean isItalic)
    {
        final String psname = getPSName(family, isBold, isItalic);
        FontMapping<TrueTypeFont> ttf = FontMappers.instance().getTrueTypeFont(psname, null);
        if (ttf != null && !ttf.isFallback()) //fallback indicates that the font is not available
            return family; //use the original family when refering to this font
        else
            return null;
    }

    @Override
    protected String getFallbackFont()
    {
        return "Helvetica";
    }

    @Override
    protected String registerExternalFont(TermURI urlstring, String format) throws MalformedURLException, IOException
    {
        return null; //not implemented yet
    }

    //=============================================================================================
    
    /**
     * Creates a PDF font
     * 
     * @return the font object
     */
    private PDFont createFont(String fontFamily, boolean isItalic, boolean isBold)
    {
        //guess a postscript name
        final String psname = getPSName(fontFamily, isItalic, isBold);
        FontMapping<TrueTypeFont> trueTypeFont = FontMappers.instance().getTrueTypeFont(psname, null);
        PDFont font = null;
        if (trueTypeFont != null) {
            try {
                font = PDType0Font.load(doc, trueTypeFont.getFont(), true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return font;
    }

    /**
     * Tries to guess a PostScript name from the font specification
     * @param fontFamily
     * @param isItalic
     * @param isBold
     * @return the PS name
     */
    protected String getPSName(String fontFamily, boolean isItalic, boolean isBold)
    {
        String psname = toCamelCase(fontFamily);
        if (isBold && isItalic) psname += ",BoldItalic";
        else if (isBold) psname += ",Bold";
        else if (isItalic) psname += ",Italic";
        return psname;
    }
    
    /**
     * Tries to reconstruct letter case (matching is case-sensitive)
     * @param str the family name
     * @return a guess of the PS form of the name
     */
    private String toCamelCase(String str)
    {
        StringBuffer sb = new StringBuffer();
        for (String s : str.split(" "))
        {
            if (s.length() <= 2)
            {
                sb.append(s.toUpperCase()); //probably something like MS
            }
            else
            {
                sb.append(Character.toUpperCase(s.charAt(0)));
                sb.append(s.substring(1, s.length()).toLowerCase());
            }
        }
        return sb.toString();
    }
    
    /**
     * Updates the ex and ch metrics.
     */
    private void updateMetrics()
    {
        ex = font.getFontDescriptor().getXHeight() / 1000 * pxFontSize();
        try {
            ch = font.getStringWidth("0") / 1000 * pxFontSize();
        } catch (Exception e) {
            ch = pxFontSize() * 0.75f; //just a guess
        }
    }

    //=========================================================================
    
    @Override
    public ImageLoader getImageLoader()
    {
        if (imageLoader == null)
            imageLoader = new GraphicsImageLoader(this);
        return imageLoader;
    }
}
