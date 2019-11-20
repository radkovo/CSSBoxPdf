/*
 * FontCache.java
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
 * Created on 19. 11. 2019, 22:02:06 by burgetr
 */

package org.fit.cssbox.pdf;

import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * A cache of font to prevent repeated creation of the same PDF fonts during rendering.
 * 
 * @author burgetr
 */
public class FontCache
{
    private Map<CacheItem, PDFont> cache;
    
    public FontCache()
    {
        cache = new HashMap<>();
    }
    
    public PDFont get(String fontFamily, boolean isBold, boolean isItalic)
    {
        return cache.get(new CacheItem(fontFamily, isBold, isItalic));
    }
    
    public void store(String fontFamily, boolean isBold, boolean isItalic, PDFont font)
    {
        cache.put(new CacheItem(fontFamily, isBold, isItalic), font);
    }
    
    //================================================================================
    
    private static class CacheItem
    {
        private String fontFamily;
        private boolean isBold;
        private boolean isItalic;
        
        protected CacheItem(String fontFamily, boolean isBold, boolean isItalic)
        {
            this.fontFamily = fontFamily.toLowerCase();
            this.isBold = isBold;
            this.isItalic = isItalic;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fontFamily == null) ? 0 : fontFamily.hashCode());
            result = prime * result + (isBold ? 1231 : 1237);
            result = prime * result + (isItalic ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            CacheItem other = (CacheItem) obj;
            if (fontFamily == null)
            {
                if (other.fontFamily != null) return false;
            }
            else if (!fontFamily.equals(other.fontFamily)) return false;
            if (isBold != other.isBold) return false;
            if (isItalic != other.isItalic) return false;
            return true;
        }
    }
    
}
