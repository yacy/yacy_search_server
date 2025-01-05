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
     * Returns the system load average for the last minute.
     * The system load average is the sum of the number of runnable entities
     * queued to the {@linkplain #getAvailableProcessors available processors}
     * and the number of runnable entities running on the available processors
     * averaged over a period of time.
     * The way in which the load average is calculated is operating system
     * specific but is typically a damped time-dependent average.
     * <p>
     * If the load average is not available, a negative value is returned.
     * <p>
     * This method is designed to provide a hint about the system load
     * and may be queried frequently.
     * The load average may be unavailable on some platform where it is
     * expensive to implement this method.
     *
     * @return the system load average; or a negative value if not available.
     */
    public static double getSystemLoadAverage() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    /**
     * Returns the "recent cpu usage" for the operating environment. This value
     * is a double in the [0.0,1.0] interval. A value of 0.0 means that all CPUs
     * were idle during the recent period of time observed, while a value
     * of 1.0 means that all CPUs were actively running 100% of the time
     * during the recent period being observed. All values betweens 0.0 and
     * 1.0 are possible depending of the activities going on.
     * If the recent cpu usage is not available, the method returns a
     * negative value.
     *
     * @return the "recent cpu usage" for the whole operating environment;
     * a negative value if not available.
     */
    @SuppressWarnings("deprecation")
    public static double getSystemCpuLoad() {
        final com.sun.management.OperatingSystemMXBean operatingSystemMXBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return operatingSystemMXBean.getSystemCpuLoad();
    }

    /**
     * Returns the "recent cpu usage" for the Java Virtual Machine process.
     * This value is a double in the [0.0,1.0] interval. A value of 0.0 means
     * that none of the CPUs were running threads from the JVM process during
     * the recent period of time observed, while a value of 1.0 means that all
     * CPUs were actively running threads from the JVM 100% of the time
     * during the recent period being observed. Threads from the JVM include
     * the application threads as well as the JVM internal threads. All values
     * betweens 0.0 and 1.0 are possible depending of the activities going on
     * in the JVM process and the whole system. If the Java Virtual Machine
     * recent CPU usage is not available, the method returns a negative value.
     *
     * @return the "recent cpu usage" for the Java Virtual Machine process;
     * a negative value if not available.
     */
    public static double getProcessCpuLoad() {
        final com.sun.management.OperatingSystemMXBean operatingSystemMXBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return operatingSystemMXBean.getProcessCpuLoad();
    }

    public static Map<String, Object> status() {
        final Runtime runtime = Runtime.getRuntime();
        final Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "Peer");
        status.put("assigned_memory", runtime.maxMemory());
        status.put("used_memory", runtime.totalMemory() - runtime.freeMemory());
        status.put("available_memory", runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory());
        status.put("cores", runtime.availableProcessors());
        status.put("threads", Thread.activeCount());
        status.put("deadlocks", deadlocks());
        status.put("load_system_load_average", Memory.getSystemLoadAverage());
        status.put("load_system_cpu_load", Memory.getSystemCpuLoad());
        status.put("load_process_cpu_load", Memory.getProcessCpuLoad());
        final YaCyHttpServer server = Switchboard.getSwitchboard().getHttpServer();
        status.put("server_threads", server == null ? 0 : server.getServerThreads());
        return status;
    }

    /**
     * find out the number of thread deadlocks. WARNING: this is a time-consuming task
     * @return the number of deadlocked threads
     */
    public static long deadlocks() {
        final long[] deadlockIDs = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        if (deadlockIDs == null) return 0;
        return deadlockIDs.length;
    }

    /**
     * write deadlocked threads as to the log as warning
     */
    public static void logDeadlocks() {
        final long[] deadlockIDs = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        if (deadlockIDs == null) return;
        final ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(deadlockIDs, true, true);
        for (final ThreadInfo ti : infos) {
            ConcurrentLog.warn("DEADLOCKREPORT", ti.toString());
        }
    }
}
