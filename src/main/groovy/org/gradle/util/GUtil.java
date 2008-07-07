/*
 * Copyright 2007-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.util;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class GUtil {
    public static List flatten(Collection elements, List addTo) {
        Iterator iter = elements.iterator();
        while (iter.hasNext()) {
            Object element = iter.next();
            if (element instanceof Collection) {
                flatten((Collection) element, addTo);
            } else if (element instanceof Map) {
                flatten(((Map) element).values(), addTo);
            } else {
                addTo.add(element);
            }
        }
        return addTo;
    }

    public static List flatten(Collection elements) {
        return flatten(elements, new ArrayList());
    }

    public static String join(Collection self, String separator) {
        StringBuffer buffer = new StringBuffer();
        boolean first = true;

        if (separator == null) separator = "";

        for (Iterator iter = self.iterator(); iter.hasNext();) {
            Object value = iter.next();
            if (first) {
                first = false;
            } else {
                buffer.append(separator);
            }
            buffer.append(value.toString());
        }
        return buffer.toString();
    }

    public static boolean isTrue(Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Collection) {
            return ((Collection) object).size() > 0;
        } else if (object instanceof String) {
            return ((String) object).length() > 0;
        }
        return true;
    }

    public static Set addToSet(Set source, Collection setToAdd) {
        source.addAll(setToAdd);
        return source;
    }

    public static Set addSets(Set set1, Set set2) {
        HashSet set = new HashSet();
        set.addAll(set1);
        set.addAll(set2);
        return set;
    }

    public static List addLists(List list1, List list2) {
        ArrayList list = new ArrayList();
        list.addAll(list1);
        list.addAll(list2);
        return list;
    }

    public static Map addMaps(Map map1, Map map2) {
        HashMap map = new HashMap();
        map.putAll(map1);
        map.putAll(map2);
        return map;
    }

}