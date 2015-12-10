/**
 * 
 */
package org.fit.cssbox.render;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
                fontTable.put(name.toLowerCase(), uri);
            }
        }
        
    }
    
    public URI findFontURI(String family, boolean bold, boolean italic)
    {
        String name = findFontName(family, bold, italic);
        if (name != null)
            return fontTable.get(name);
        else
            return null;
    }
    
    private String findFontName(String family, boolean bold, boolean italic)
    {
        List<String> list = findNamesByFamily(family);
        for (Iterator<String> it = list.iterator(); it.hasNext();)
        {
            String cand = it.next();
            if ((bold && !cand.contains("bold")) || (!bold && cand.contains("bold")))
                it.remove();
        }
        if (italic)
        {
            for (String cand : list)
                if (cand.contains("italic"))
                    return cand;
            for (String cand : list)
                if (cand.contains("oblique"))
                    return cand;
        }
        else
        {
            for (String cand : list)
                if (!cand.contains("italic") && !cand.contains("oblique"))
                    return cand;
        }
        return null;
    }
    
    private List<String> findNamesByFamily(String family)
    {
        List<String> ret = new ArrayList<String>();
        for (String name : fontTable.keySet())
        {
            if (name.startsWith(family.toLowerCase()))
            {
                ret.add(name);
            }
        }
        return ret;
    }
    
}
