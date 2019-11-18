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
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.css.FontSpec;
import org.fit.cssbox.css.FontTable;
import org.fit.cssbox.layout.BrowserConfig;
import org.fit.cssbox.layout.FontInfo;
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
    private boolean fontItalic;
    private boolean fontBold;
    private float ex; // 1ex length in points
    private float ch; // 1ch length in points

    
    public PDFVisualContext(PDDocument doc, VisualContext parent, BrowserConfig config, FontTable fontTable)
    {
        super(parent, config, fontTable);
        this.doc = doc;
        this.font = PDType1Font.HELVETICA;
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
            fontItalic = ((PDFVisualContext) src).fontItalic;
            fontBold = ((PDFVisualContext) src).fontBold;
            ex = src.getEx();
            ch = src.getCh();
        }
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
        return new FontInfo(font.getName(), getFontSize(), fontBold, fontItalic);
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
        return font.getName();
    }

    @Override
    public float stringWidth(String text)
    {
        try
        {
            return font.getStringWidth(text) / 1000 * pxFontSize();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void setCurrentFont(String family, float size, FontWeight weight, FontStyle style, float spacing)
    {
        fontItalic = (style == FontStyle.ITALIC || style == FontStyle.OBLIQUE);
        fontBold = FontSpec.representsBold(weight);
        font = createFont(family, fontItalic, fontWeight(weight));
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
    protected String fontAvailable(String family)
    {
        PDFont f = createFont(family, false, 400.0f);
        if (f != null)
            return f.getName();
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
    private PDFont createFont(String fontFamily, boolean isItalic, float weight)
    {
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, COSName.FONT_DESC);
        PDFontDescriptor desc = new PDFontDescriptor(dictionary);
        desc.setItalic(isItalic);
        desc.setFontWeight(weight);
        desc.setFontFamily(fontFamily);
        FontMapping<TrueTypeFont> trueTypeFont = FontMappers.instance().getTrueTypeFont(fontFamily, desc);

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
    
    private float fontWeight(FontWeight weight)
    {
        switch (weight)
        {
            case BOLD:
                return 700;
            case BOLDER:
                return 600;
            case LIGHTER:
                return 300;
            case NORMAL:
                return 400;
            case numeric_100:
                return 100;
            case numeric_200:
                return 200;
            case numeric_300:
                return 300;
            case numeric_400:
                return 400;
            case numeric_500:
                return 500;
            case numeric_600:
                return 600;
            case numeric_700:
                return 700;
            case numeric_800:
                return 800;
            case numeric_900:
                return 900;
            default:
                return 400;
        }
    }
    
    private void updateMetrics()
    {
        ex = font.getFontDescriptor().getXHeight() / 1000 * pxFontSize();
        try {
            ch = font.getStringWidth("0") / 1000 * pxFontSize();
        } catch (IOException e) {
            ch = pxFontSize() * 0.75f; //just a guess
        }
    }
    
}
