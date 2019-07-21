
package org.fit.cssbox.render;

import java.awt.Color;
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
import cz.vutbr.web.css.TermFunction.Gradient.ColorStop;

/**
 * This class creates the linear gradient background.
 * 
 * @author Nguyen Hoang Duong
 */
public class LinearGradient
{

    public double x1;
    public double y1;
    public double x2;
    public double y2;

    public Color[] trueColors;
    public float[] trueColorLen;

    /**
     * This method is used for calculating coordinates of starting point and
     * ending point of the linear gradient function.
     * 
     * @param angle
     *            angle of the gradient line.
     * @param w
     *            width of the element
     * @param h
     *            height of the element
     */
    public void createGradLinePoints(double angle, int w, int h)
    {
        double procDeg = (angle) % 360;
        if (procDeg < 0)
        {
            procDeg = 360 - ((-1 * procDeg) % 360);
        }
        double normDeg = 90 + procDeg;
        double tan;
        double wRatio = 1;

        x1 = x2 = y1 = y2 = 0;

        if (w != h)
        {
            wRatio = (double) w / h;
        }
        int sx = w / 2;
        int sy = h / 2;

        // calculating coordinates of corners of the element
        int ax = 0;
        int ay = h;

        int bx = w;
        int by = h;

        int cx = w;
        int cy = 0;

        int dx = 0;
        int dy = 0;

        if (procDeg == 0)
        {
            x1 = w / 2;
            y1 = 0;
            x2 = w / 2;
            y2 = h;
        }
        else if (procDeg == 90)
        {
            x1 = 0;
            y1 = h / 2;
            x2 = w;
            y2 = h / 2;
        }
        else if (procDeg == 180)
        {
            x1 = w / 2;
            y1 = h;
            x2 = w / 2;
            y2 = 0;
        }
        else if (procDeg == 270)
        {
            x1 = w;
            y1 = h / 2;
            x2 = 0;
            y2 = h / 2;
        }
        else
        {

            tan = Math.tan((normDeg / 180) * Math.PI);

            double qqq, kkk;
            double qqq1, kkk1;
            double qqq2, kkk2;

            kkk = -tan / wRatio;
            qqq = sy - kkk * sx;

            kkk1 = 1 / (tan / wRatio);
            kkk2 = 1 / (tan / wRatio);

            if (procDeg > 0 && procDeg <= 90)
            {
                qqq1 = dy - kkk2 * dx;
                qqq2 = by - kkk1 * bx;

                x2 = (qqq2 - qqq) / (kkk - kkk2);
                y2 = kkk * x2 + qqq;
                x1 = (qqq1 - qqq) / (kkk - kkk1);
                y1 = kkk * x1 + qqq;

            }
            else if (procDeg > 90 && procDeg < 180)
            {
                qqq1 = ay - kkk2 * ax;
                qqq2 = cy - kkk1 * cx;

                x2 = (qqq2 - qqq) / (kkk - kkk2);
                y2 = kkk * x2 + qqq;

                x1 = (qqq1 - qqq) / (kkk - kkk1);
                y1 = kkk * x1 + qqq;

            }
            else if (procDeg > 180 && procDeg < 270)
            {
                qqq1 = by - kkk2 * bx;
                qqq2 = dy - kkk1 * dx;

                x2 = (qqq2 - qqq) / (kkk - kkk2);
                y2 = kkk * x2 + qqq;

                x1 = (qqq1 - qqq) / (kkk - kkk1);
                y1 = kkk * x1 + qqq;
            }
            else if (procDeg > 270 && procDeg < 360)
            {
                qqq1 = cy - kkk2 * cx;
                qqq2 = ay - kkk1 * ax;

                x2 = (qqq2 - qqq) / (kkk - kkk2);
                y2 = kkk * x2 + qqq;

                x1 = (qqq1 - qqq) / (kkk - kkk1);
                y1 = kkk * x1 + qqq;
            }
        }
    }

    /**
     * This method is used for creating missing colour-stop lengths.
     * 
     * @param colorstops
     *            list of all colours in linear function.
     * @param dec
     *            decoder, which is used for decoding radial-gradient()
     *            parameters to value
     * @param isRepeating
     *            flag of repeating-linear-gradient() function
     */
    public void createColorStopsLength(List<ColorStop> colorstops, CSSDecoder dec, boolean isRepeating)
    {
        if (colorstops != null)
        {
            float gradLine = (float) Math.hypot(Math.abs(y2 - y1), Math.abs(x2 - x1)); // calculating grad line
            Color[] colors = new Color[colorstops.size() + 1]; // 1 reserved place for duplicating last colour when it doesnt have set length 1  
            float[] colorLen = new float[colorstops.size() + 1];
            boolean[] isLen = new boolean[colorstops.size() + 1];
            for (int n = 0; n < colorstops.size(); n++)
            {
                boolean isLength = false;
                TermColor col = colorstops.get(n).getColor();
                colors[n] = CSSUnits.convertColor(col.getValue());
                if ((colorstops.get(n).getLength() != null))
                {
                    if (!colorstops.get(n).getLength().isPercentage()) isLength = true;
                }
                colorLen[n] = dec.getLength(colorstops.get(n).getLength(), false, 0, 0, 100);

                if (colorLen[n] == 0)
                    isLen[n] = false;
                else
                {
                    if (isLength)
                    {
                        colorLen[n] = colorLen[n] / (gradLine / 100);
                    }
                    isLen[n] = true;
                }
                colorLen[n] /= 100;

            }

            isLen[0] = true; // first and last colour will alwas have set length (default 0 or 1 or by user) 
            isLen[colorLen.length - 2] = true;

            int trueSize = 0;
            int repeatSize = 0;
            int repeatTime = 0;
            if ((colorLen[colors.length - 2] == 0) || (colorLen[colors.length - 2] == 1))
            {
                colorLen[colors.length - 2] = 1; // if last color doesnt have set length then default 1 is set  
                trueSize = colorstops.size();
            }
            else if (colorLen[colors.length - 2] != 1)
            { // if length 1 is not set in the end
                if (isRepeating)
                { // if repeating-linear-gradient() is set
                    float lastLen = colorLen[colorLen.length - 2]; // last color length
                    repeatTime = (int) (gradLine / ((gradLine / 100) * (lastLen * 100))); // how many time will be repeated
                    // calculating number of needed colors
                    repeatSize = (colorLen.length - 1) * repeatTime;
                    lastLen *= repeatTime;
                    int ix = 0;
                    while (lastLen < gradLine)
                    {
                        if (ix == colorLen.length - 2) break;
                        if (ix == 0)
                            lastLen += colorLen[ix];
                        else
                            lastLen += colorLen[ix] - colorLen[ix - 1];
                        ix++;
                        repeatSize++;
                    }
                    trueSize = repeatSize - 1; // zaloha pre default posledny color s length 1
                }
                else
                {
                    colorLen[colors.length - 1] = 1;
                    colors[colors.length - 1] = colors[colors.length - 2];
                    isLen[colors.length - 1] = true;
                    trueSize = colorstops.size() + 1;
                }
            }
            if (colorLen[0] != 0) trueSize++;
            trueColors = new Color[trueSize];
            trueColorLen = new float[trueSize];
            boolean[] trueIsLen = new boolean[trueSize];
            if (colorLen[0] != 0)
            {
                trueColors[0] = colors[0];
                trueColorLen[0] = 0;
                trueIsLen[0] = true;
            }
            float currentColorLen = 0;
            float[] prevColorLen = new float[colorLen.length - 1];
            float[] pomLen = new float[colorLen.length - 1];
            for (int n = 0; n < colorLen.length - 1; n++)
            {
                prevColorLen[n] = colorLen[n];

            }
            // setting needed colors and color lenghts for repeat
            if ((repeatTime != 0) && (repeatSize != 0))
            {
                int ix = 0;
                if (colorLen[0] != 0) ix++;
                for (int n = 0; n < repeatTime; n++)
                {

                    for (int m = 0; m < colorLen.length - 1; m++)
                    {
                        trueColors[ix] = colors[m];
                        if (!isLen[m] || (n == 0)) // ked je dlzka 0 alebo je prvy cyklus tak opiseme povodne dlzky 
                            trueColorLen[ix] = colorLen[m];
                        else
                        {
                            if (m == 0)
                                trueColorLen[ix] = currentColorLen;
                            else
                            {
                                trueColorLen[ix] = currentColorLen + prevColorLen[m] - prevColorLen[m - 1];
                                trueColorLen[ix] = Math.round(trueColorLen[ix] * 1000.0f) / 1000.0f;
                            }
                        }
                        pomLen[m] = currentColorLen;
                        currentColorLen = trueColorLen[ix];
                        trueIsLen[ix] = isLen[m];

                        ix++;
                    }
                    currentColorLen = trueColorLen[ix - 1];

                }

                for (int n = 0; n < ((repeatSize - 2) % (colors.length - 1)); n++)
                {
                    trueColors[ix] = colors[n];
                    if (!isLen[n])
                        trueColorLen[ix] = colorLen[n];
                    else
                    {
                        if (n == 0)
                            trueColorLen[ix] = currentColorLen;
                        else
                        {
                            trueColorLen[ix] = currentColorLen + prevColorLen[n] - prevColorLen[n - 1];
                            trueColorLen[ix] = Math.round(trueColorLen[ix] * 1000.0f) / 1000.0f;
                        }
                    }
                    currentColorLen = trueColorLen[ix];
                    trueIsLen[ix] = isLen[n];
                    ix++;

                }
                trueColors[trueSize - 1] = trueColors[trueSize - 2];
                trueColorLen[trueSize - 1] = 1;
                trueIsLen[trueSize - 1] = true;

            }
            else
            {
                int ix;
                if (colorLen[0] != 0)
                {
                    ix = 1;
                    trueSize--;
                }
                else
                    ix = 0;
                for (int n = 0; n < trueSize; n++)
                {
                    trueColors[ix] = colors[n];
                    trueColorLen[ix] = colorLen[n];
                    trueIsLen[ix] = isLen[n];
                    ix++;
                }
            }
            int n = 0;
            int m = 0;
            // calculating missed color lengths
            while (n < trueIsLen.length)
            {
                while (trueIsLen[n])
                { // skipping colors with length
                    n++;
                    if (n >= trueIsLen.length) break;
                }
                if (n >= trueIsLen.length) break;
                while ((!trueIsLen[n]) && (n < trueIsLen.length))
                { // calculating how many colors without lengths
                    n++;
                    m++;
                }
                if (m != 0)
                { // when is not end
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
    }

    /**
     * This method is used for creating linear gradient with its components.
     * 
     * @return shading for rendering linear gradient in PDF
     * @param startx
     *            x-axis of the starting point of the gradient line
     * @param starty
     *            y-axis of the starting point of the gradient line
     * @param endx
     *            x-axis of the ending point of the gradient line
     * @param endy
     *            y-axis of the ending point of the gradient line
     */
    public PDShadingType3 createLinearGrad(float startx, float starty, float endx, float endy)
    {
        float[] components = new float[] { trueColors[0].getRed() / 255f, trueColors[0].getGreen() / 255f,
                trueColors[0].getBlue() / 255f };
        PDColor fcolor = new PDColor(components, PDDeviceRGB.INSTANCE);

        PDShadingType3 shading = new PDShadingType3(new COSDictionary());
        shading.setShadingType(PDShading.SHADING_TYPE2);
        shading.setColorSpace(fcolor.getColorSpace());
        COSArray coords = new COSArray();
        coords.add(new COSFloat((float) startx));
        coords.add(new COSFloat((float) starty));
        coords.add(new COSFloat((float) endx));
        coords.add(new COSFloat((float) endy));
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
     * This method is used for setting colour lengths to linear gradient.
     * 
     * @return the function, which is an important parameter for setting linear
     *         gradient.
     * @param colors
     *            colours of linear gradient.
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
     * This method is used for setting colours to linear gradient.
     * 
     * @return the COSArray, which is an important parameter for setting linear
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
            float alpha = prevColor.getAlpha() / 255f;
            // calculating transparency if set
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
