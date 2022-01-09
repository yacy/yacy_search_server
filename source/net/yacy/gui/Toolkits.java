/**
 *  Toolkits
 *  Copyright 2015 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.01.2015 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.gui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.yacy.cora.util.ConcurrentLog;

public class Toolkits {

    public static Object applicationInstance;
    public static Method setDockIconImage, setQuitHandler;

    static {
        Class<?> applicationClass = null;
        try {
            applicationClass = Class.forName("com.apple.eawt.Application");
            final Method applicationGetApplication = applicationClass.getMethod("getApplication");
            applicationInstance = applicationGetApplication.invoke(null);
            setDockIconImage = applicationClass.getMethod("setDockIconImage", Class.forName("java.awt.Image"));
        } catch (ClassNotFoundException|NoSuchMethodException|SecurityException|IllegalAccessException|IllegalArgumentException|InvocationTargetException e) {
            ConcurrentLog.logException(e);
        }
        if (applicationClass != null) try {
            final Class<?> quitHandlerClass = Class.forName("com.apple.eawt.QuitHandler");
            setQuitHandler = applicationClass.getMethod("setQuitHandler", quitHandlerClass);
        } catch (ClassNotFoundException|NoSuchMethodException|SecurityException|IllegalArgumentException e) {
            ConcurrentLog.logException(e);
        }
    }
}
