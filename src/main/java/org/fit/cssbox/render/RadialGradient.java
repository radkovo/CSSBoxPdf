
package org.fit.cssbox.render;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.util.List;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType3;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType3;
import org.fit.cssbox.css.CSSUnits;
import org.fit.cssbox.layout.CSSDecoder;

import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermIdent;
import cz.vutbr.web.css.TermLengthOrPercent;
import cz.vutbr.web.css.TermFunction.Gradient.ColorStop;

/**
 * This class creates the radial gradient background.
 * 
 * @author Nguyen Hoang Duong
 */
public class RadialGradient
{
    // coordinates of center point
    int cx;
    int cy;

    // radiuses of ending shape
    // radiuses of ellipse
    double radx;
    double rady;
    // radius of circle
    double radc;

    // ending shape
    String shape = "";

    // error flag
    boolean err = false;

    // colors and stops
    public Color[] trueColors;
    public float[] trueColorLen;

    /**
     * This method is used for setting shape of gradient from the parameter of
     * radial gradient function.
     * 
     * @param shapeIdent
     *            shape of the gradient.
     */
    public void setShape(TermIdent shapeIdent)
    {
        shape = shapeIdent.getValue();
    }

    /**
     * This method is used for setting center point of gradient from the
     * parameter of radial gradient function.
     * 
     * @param pos
     *            position of center point.
     * @param dec
     *            decoder, which is used for decoding radial-gradient()
     *            parameters to value
     * @param w
     *            width of the element
     * @param h
     *            height of the element
     */
    public void setGradientCenter(TermLengthOrPercent[] pos, CSSDecoder dec, int w, int h)
    {
        cx = dec.getLength(pos[0], false, w / 2, 0, w);
        cy = dec.getLength(pos[1], false, h / 2, 0, h);
        cy = h - cy; // due to coordinate system in PDF
    }

    /**
     * This method is used for setting radiuses of gradient from the parameter
     * of radial gradient function.
     * 
     * @param size
     *            size parameter of radial function.
     * @param dec
     *            decoder, which is used for decoding radial-gradient()
     *            parameters to value
     * @param x
     *            x-axis of the element
     * @param y
     *            y-axis of the element
     * @param w
     *            width of the element
     * @param h
     *            height of the element
     */
    public void setRadiusFromSizeValue(TermLengthOrPercent[] size, CSSDecoder dec, int x, int y, int w, int h)
    {
        if (size != null)
        {
            if (size.length == 2)
            { // if ending shape is ellipse
                if ((size[0] != null) && (size[1] != null))
                {
                    radx = dec.getLength(size[0], false, x, 0, w);
                    rady = dec.getLength(size[1], false, y, 0, h);
                    shape = "ellipse";
                }
                else if ((size[0] != null) && (size[1] == null))
                {
                    err = true; // ellipse must have 2 radiuses
                }
            }
            else if (size.length == 1)
            {
                radc = dec.getLength(size[0], false, x, 0, w);
                shape = "circle";
            }
        }
    }

    /**
     * This method is used for setting radiuses of gradient from the parameter
     * of radial gradient function.
     * 
     * @param sizeIdent
     *            size parameter of radial function, which contains one of the
     *            keywords.
     * @param dec
     *            decoder, which is used for decoding radial-gradient()
     *            parameters to value
     * @param x
     *            x-axis of the element
     * @param y
     *            y-axis of the element
     */
    public void setRadiusFromSizeIdent(TermIdent sizeIdent, int w, int h)
    {
        // vzdialenosti od centra gradientu do jednotlivej strany elementu
        double side1 = Math.abs(0 - cx);
        double side2 = Math.abs(0 - cy);
        double side3 = Math.abs(w - cx);
        double side4 = Math.abs(h - cy);

        double corner1 = Math.sqrt((side1 * side1) + (side2 * side2));
        double corner2 = Math.sqrt((side2 * side2) + (side3 * side3));
        double corner3 = Math.sqrt((side3 * side3) + (side4 * side4));
        double corner4 = Math.sqrt((side1 * side1) + (side4 * side4));
        // ratio of x/y of closest side
        double closestSideRatioX = Math.min(side1, side3) / Math.min(side2, side4);
        // ratio of x/y of farthest side
        double farthestSideRatioX = Math.max(side1, side3) / Math.max(side2, side4);

        if (sizeIdent.getValue().equalsIgnoreCase("farthest-corner"))
        {
            radc = Math.max(Math.max(corner1, corner2), Math.max(corner3, corner4));
            if (farthestSideRatioX > 1)
            {
                rady = radc / farthestSideRatioX;
                radx = radc;

            }
            else
            {
                radx = radc * farthestSideRatioX;
                rady = radc;
            }
        }
        else if (sizeIdent.getValue().equalsIgnoreCase("closest-corner"))
        {
            radc = Math.min(Math.min(corner1, corner2), Math.min(corner3, corner4));
            if (closestSideRatioX > 1)
            {
                rady = radc / closestSideRatioX;
                radx = radc;
            }
            else
            {
                radx = radc * closestSideRatioX;
                rady = radc;
            }
        }
        else if (sizeIdent.getValue().equalsIgnoreCase("farthest-side"))
        {
            radc = Math.max(Math.max(side1, side2), Math.max(side3, side4));
            rady = Math.max(side2, side4);
            radx = Math.max(side1, side3);
        }
        else if (sizeIdent.getValue().equalsIgnoreCase("closest-side"))
        {
            radc = Math.min(Math.min(side1, side2), Math.min(side3, side4));
            rady = Math.min(side2, side4);
            radx = Math.min(side1, side3);
        }

    }

    /**
     * This method is used for creating missing colour-stop lengths.
     * 
     * @param colorstops
     *            list of all colours in radial function.
     * @param dec
     *            decoder, which is used for decoding radial-gradient()
     *            parameters to value
     */
    public void createColorStopsLength(List<ColorStop> colorstops, CSSDecoder dec)
    {
        Color[] colors = new Color[colorstops.size() + 2]; // 2 reserved places for duplicate color if users didnt set first color stop with 0 and last with 1
        float[] colorLen = new float[colorstops.size() + 2];
        boolean[] isLen = new boolean[colorstops.size() + 2];
        int p = 0;
        for (int n = 1; n <= colorstops.size(); n++)
        {
            TermColor col = colorstops.get(p).getColor();
            colors[n] = CSSUnits.convertColor(col.getValue());
            colorLen[n] = dec.getLength(colorstops.get(p).getLength(), false, 0, 0, 100);
            colorLen[n] /= 100;
            if (colorLen[n] == 0)
                isLen[n] = false;
            else
                isLen[n] = true;
            p++;
        }
        colors[0] = colors[1];
        colorLen[0] = 0;
        isLen[0] = true; // first and last color will always have length (default 1 or 0 or by user)
        isLen[colorLen.length - 2] = true;
        isLen[1] = true;
        int trueSize = 0;
        if ((colorLen[colors.length - 2] == 0) || (colorLen[colors.length - 2] == 1))
        {
            colorLen[colors.length - 2] = 1; // if last color doesnt have set length then it has default value 1
            trueSize = colorstops.size() + 1;
        }
        else if (colorLen[colors.length - 2] != 1)
        { // if last length 1 is not set
            colorLen[colors.length - 1] = 1;
            colors[colors.length - 1] = colors[colors.length - 2];
            isLen[colors.length - 1] = true;
            trueSize = colorstops.size() + 2;
        }

        trueColors = new Color[trueSize]; // 1 reserve place if user didnt declared last stop 100% so we must duplicate last color and set it length to 1
        trueColorLen = new float[trueSize];
        boolean[] trueIsLen = new boolean[trueSize];

        for (int n = 0; n < trueSize; n++)
        {

            trueColors[n] = colors[n];
            trueColorLen[n] = colorLen[n];
            trueIsLen[n] = isLen[n];
        }

        int n = 0;
        int m = 0;
        while (n < trueIsLen.length)
        {
            while (trueIsLen[n])
            { // colors with stop is skipped
                n++;
                if (n >= trueIsLen.length) break;
            }
            if (n >= trueIsLen.length) break;
            while ((!trueIsLen[n]) && (n < trueIsLen.length))
            { // calculating how many colors dont have declared stops
                n++;
                m++;
            }
            if (m != 0)
            { // if it is not the end
                float gap = trueColorLen[n] - trueColorLen[n - m - 1];
                int coef = 1;
                for (int o = n - m; o < n; o++)
                {
                    trueColorLen[o] = trueColorLen[n - m - 1] + ((gap / (m + 1)) * coef);
                    coef++;
                }
            }
            m = 0;
        }
    }

    /**
     * This method is used for creating ellipse shape from circular shape of
     * radial function
     * 
     * @return matrix for shape transforming
     * @param x
     *            x-axis of the element
     * @param y
     *            y-axis of the element
     */
    public AffineTransform createTransformForEllipseGradient(float x, float y)
    {
        double scaleX = 1, scaleY = 1;
        AffineTransform moveToCenter = new AffineTransform();

        if (radx > rady)
        {

            radc = radx;
            scaleX = 1;
            scaleY = rady / radx;
            // we need to translate center so that after scaling it will be in the old place		
            moveToCenter = AffineTransform.getTranslateInstance(0, y - (y * scaleY));
        }
        else if (radx < rady)
        {
            radc = rady;
            scaleX = radx / rady;
            scaleY = 1;
            moveToCenter = AffineTransform.getTranslateInstance(x - (x * scaleX), 0);
        }
        else // if ending shape is circle
            radc = radx;

        AffineTransform at = AffineTransform.getScaleInstance(scaleX, scaleY);
        moveToCenter.concatenate(at);
        return moveToCenter;

    }

    /**
     * This method is used for creating radial gradient with its components.
     * 
     * @return shading for rendering radial gradient in PDF
     * @param cx
     *            x-axis of the centre point of the element
     * @param cy
     *            y-axis of the centre point of the element
     * @param radius
     *            radius of the gradient
     */
    public PDShadingType3 createRadialGrad(float cx, float cy, float radius)
    {
        float[] components = new float[] { trueColors[0].getRed() / 255f, trueColors[0].getGreen() / 255f,
                trueColors[0].getBlue() / 255f };
        PDColor fcolor = new PDColor(components, PDDeviceRGB.INSTANCE);
        PDShadingType3 shading = new PDShadingType3(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE3);
        shading.setColorSpace(fcolor.getColorSpace());
        COSArray coords = new COSArray();
        /* center point */
        coords.add(new COSFloat((float) cx));
        coords.add(new COSFloat((float) cy));
        coords.add(new COSFloat(0));
        /* focal point - is is not set in CSS so we use center point instead */
        coords.add(new COSFloat((float) cx));
        coords.add(new COSFloat((float) cy));
        coords.add(new COSFloat(radius));
        shading.setCoords(coords);

        PDFunctionType3 type3 = buildType3Function(trueColors, trueColorLen);

        COSArray extend = new COSArray();
        extend.add(COSBoolean.TRUE);
        extend.add(COSBoolean.TRUE);
        shading.setFunction(type3);
        shading.setExtend(extend);
        return shading;
    }

    /**
     * This method is used for setting colour lengths to radial gradient.
     * 
     * @return the function, which is an important parameter for setting radial
     *         gradient.
     * @param colors
     *            colours of radial gradient.
     * @param fractions
     *            length of each colour in gradient line.
     */
    /* vycerpano z verejneho projektu pdfbox-graphics2d na internete */
    private PDFunctionType3 buildType3Function(Color[] colors, float[] fractions)
    {
        COSDictionary function = new COSDictionary();
        function.setInt(COSName.FUNCTION_TYPE, 3);

        COSArray domain = new COSArray();
        domain.add(new COSFloat(0));
        domain.add(new COSFloat(1));

        COSArray encode = new COSArray();

        COSArray range = new COSArray();
        range.add(new COSFloat(0));
        range.add(new COSFloat(1));
        COSArray bounds = new COSArray();
        for (int i = 1; i < colors.length - 1; i++)
            bounds.add(new COSFloat(fractions[i]));

        COSArray functions = buildType2Functions(colors, domain, encode);

        function.setItem(COSName.FUNCTIONS, functions);
        function.setItem(COSName.BOUNDS, bounds);
        function.setItem(COSName.ENCODE, encode);
        PDFunctionType3 type3 = new PDFunctionType3(function);
        type3.setDomainValues(domain);
        return type3;
    }

    /**
     * This method is used for setting colours to radial gradient.
     * 
     * @return the COSArray, which is an important parameter for setting radial
     *         gradient.
     * @param colors
     *            colours to use.
     * @param domain
     *            parameter for setting functiontype2
     * @param encode
     *            encoding COSArray
     */
    private COSArray buildType2Functions(Color[] colors, COSArray domain, COSArray encode)
    {
        Color prevColor = colors[0];

        COSArray functions = new COSArray();
        for (int i = 1; i < colors.length; i++)
        {

            Color color = colors[i];
            // calculate transparency if its set
            float alpha = prevColor.getAlpha() / 255f;
            float r = prevColor.getRed() * alpha + (1 - alpha) * 255;
            float g = prevColor.getGreen() * alpha + (1 - alpha) * 255;
            float b = prevColor.getBlue() * alpha + (1 - alpha) * 255;
            float[] component = new float[] { r / 255f, g / 255f, b / 255f };
            PDColor prevPdColor = new PDColor(component, PDDeviceRGB.INSTANCE);
            alpha = color.getAlpha() / 255f;
            r = color.getRed() * alpha + (1 - alpha) * 255;
            g = color.getGreen() * alpha + (1 - alpha) * 255;
            b = color.getBlue() * alpha + (1 - alpha) * 255;
            float[] component1 = new float[] { r / 255f, g / 255f, b / 255f };

            PDColor pdColor = new PDColor(component1, PDDeviceRGB.INSTANCE);
            COSArray c0 = new COSArray();
            COSArray c1 = new COSArray();
            for (float component2 : prevPdColor.getComponents())
                c0.add(new COSFloat(component2));
            for (float component3 : pdColor.getComponents())
                c1.add(new COSFloat(component3));

            COSDictionary type2Function = new COSDictionary();
            type2Function.setInt(COSName.FUNCTION_TYPE, 2);
            type2Function.setItem(COSName.C0, c0);
            type2Function.setItem(COSName.C1, c1);
            type2Function.setInt(COSName.N, 1);
            type2Function.setItem(COSName.DOMAIN, domain);
            functions.add(type2Function);

            encode.add(new COSFloat(0));
            encode.add(new COSFloat(1));
            prevColor = color;
        }
        return functions;
    }
}
