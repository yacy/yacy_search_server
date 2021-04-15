/**
 *  Memory
 *  Copyright 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 22.09.2005 on http://yacy.net
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

package net.yacy.cora.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import net.yacy.http.YaCyHttpServer;
import net.yacy.search.Switchboard;

public class Memory {

    private static final Runtime runtime = Runtime.getRuntime();

    /**
     * memory that is free without increasing of total memory taken from os
     * @return bytes
     */
    public static final long free() {
        return runtime.freeMemory();
    }

    /**
     * memory that is available including increasing total memory up to maximum
     * @return bytes
     */
    public static final long available() {
        return maxMemory() - total() + free();
    }

    /**
     * maximum memory the Java virtual will allocate machine; may vary over time in some cases
     * @return bytes
     */
    public static final long maxMemory() {
        return runtime.maxMemory(); // can be Long.MAX_VALUE if unlimited
    }

    /**
     * currently allocated memory in the Java virtual machine; may vary over time
     * @return bytes
     */
    public static final long total() {
        return runtime.totalMemory();
    }

    /**
     * memory that is currently bound in objects
     * @return used bytes
     */
    public static final long used() {
        return total() - free();
    }

    /**
     * get number of CPU cores
     * @return number of CPU cores
     */
    public static final long cores() {
        return runtime.availableProcessors();
    }

    /**
     * get the system load within the last minute
     * @return the system load or a negative number if the load is not available
     */
    public static double getSystemLoadAverage() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    public static double getSystemCpuLoad() {
        return getOSBean("getSystemCpuLoad");
    }

    public static double getProcessCpuLoad() {
        return getOSBean("getProcessCpuLoad");
    }

    private static double getOSBean(String name) {
        try {
            Method m = ManagementFactory.getOperatingSystemMXBean().getClass().getMethod(name);
            m.setAccessible(true);
            Object o = m.invoke(ManagementFactory.getOperatingSystemMXBean());
            if (o instanceof Double) return ((Double) o).doubleValue();
        } catch (Throwable e) {}
        return 0.0d;
    }

    public static Map<String, Object> status() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "Peer");
        status.put("assigned_memory", runtime.maxMemory());
        status.put("used_memory", runtime.totalMemory() - runtime.freeMemory());
        status.put("available_memory", runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory());
        status.put("cores", runtime.availableProcessors());
        status.put("threads", Thread.activeCount());
        status.put("deadlocks", deadlocks());
        status.put("load_system_average", Memory.getSystemLoadAverage());
        status.put("load_process_cpu", Memory.getProcessCpuLoad());
        YaCyHttpServer server = Switchboard.getSwitchboard().getHttpServer();
        status.put("server_threads", server == null ? 0 : server.getServerThreads());
        return status;
    }

    /**
     * find out the number of thread deadlocks. WARNING: this is a time-consuming task
     * @return the number of deadlocked threads
     */
    public static long deadlocks() {
        long[] deadlockIDs = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        if (deadlockIDs == null) return 0;
        return deadlockIDs.length;
    }

    /**
     * write deadlocked threads as to the log as warning
     */
    public static void logDeadlocks() {
        long[] deadlockIDs = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        if (deadlockIDs == null) return;
        ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(deadlockIDs, true, true);
        for (ThreadInfo ti : infos) {
            ConcurrentLog.warn("DEADLOCKREPORT", ti.toString());
        }
    }
}
