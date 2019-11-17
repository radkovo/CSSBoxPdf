/**
 * 
 */

package org.fit.cssbox.pdf;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.fontbox.util.autodetect.FontFileFinder;

/**
 * An experimental implementation of the database of available system TTF fonts.
 * 
 * @author burgetr
 */
public class FontDB
{
    private Map<String, URI> fontTable;

    public FontDB()
    {
        buildFontTable();
    }

    private void buildFontTable()
    {
        fontTable = new HashMap<String, URI>();
        FontFileFinder fff = new FontFileFinder();
        for (URI uri : fff.find())
        {
            String path = uri.getPath().trim();
            if (path.toLowerCase().endsWith(".ttf"))
            {
                int ofs = path.lastIndexOf('/');
                String name = path.substring(ofs + 1, path.length() - 4);
                fontTable.put(normalizeName(name), uri);
                //System.out.println(name + " -> " + normalizeName(name));
            }
        }

    }

    public URI findFontURI(String family, boolean bold, boolean italic)
    {
        String[] search;
        if (bold && italic)
            search = new String[] { "bolditalic", "italicbold", "bi", "bd", "boldoblique", "obliquebold" };
        else if (bold)
            search = new String[] { "bold", "bd", "b" };
        else if (italic)
            search = new String[] { "italic", "i", "oblique" };
        else
            search = new String[] { "", "r" };

        String prefix = normalizeName(family);
        for (String cand : search)
        {
            URI uri = fontTable.get(prefix + cand);
            if (uri != null) return uri;
        }
        return null;
    }

    private String normalizeName(String name)
    {
        return name.trim().toLowerCase().replaceAll("[^a-z]", "");
    }

}
