
package de.anomic.server;

import java.lang.reflect.*;

public final class serverInstantThread extends serverAbstractThread implements serverThread {
    
    private Method jobExecMethod, jobCountMethod;
    private Object environment;
    
    public serverInstantThread(Object env, String jobExec, String jobCount) {
        // job is the name of a method of the object 'env'
        try {
            this.jobExecMethod = env.getClass().getMethod(jobExec, new Class[0]);
            if (jobCount == null)
                this.jobCountMethod = null;
            else
                this.jobCountMethod = env.getClass().getMethod(jobCount, new Class[0]);
            this.environment = env;
            this.setName(env.getClass().getName() + "." + jobExec);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Internal Error in serverInstantThread, wrong declaration: " + e.getMessage());
        }
    }
    
    public int getJobCount() {
        if (this.jobCountMethod == null) return Integer.MAX_VALUE;
        try {
            Object result = jobCountMethod.invoke(environment, new Object[0]);
            if (result instanceof Integer)
                return ((Integer) result).intValue();
            else
                return -1;
        } catch (IllegalAccessException e) {
            return -1;
        } catch (IllegalArgumentException e) {
            return -1;
        } catch (InvocationTargetException e) {
            System.out.println("Runtime Error in serverInstantThread, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
        
    public boolean job() throws Exception {
        boolean jobHasDoneSomething = false;
        try {
            Object result = jobExecMethod.invoke(environment, new Object[0]);
            if (result == null) jobHasDoneSomething = true;
            else if (result instanceof Boolean) jobHasDoneSomething = ((Boolean) result).booleanValue();
        } catch (IllegalAccessException e) {
            System.out.println("Internal Error in serverInstantThread: " + e.getMessage());
            System.out.println("shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (IllegalArgumentException e) {
            System.out.println("Internal Error in serverInstantThread: " + e.getMessage());
            System.out.println("shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (InvocationTargetException e) {
            System.out.println("Runtime Error in serverInstantThread, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
        }
        return jobHasDoneSomething;
    }
    
    public static serverThread oneTimeJob(Object env, String jobExec, serverLog log, long startupDelay) {
        // start the job and execute it once as background process
        serverThread thread = new serverInstantThread(env, jobExec, null);
        thread.setStartupSleep(startupDelay);
        thread.setIdleSleep(-1);
        thread.setBusySleep(-1);
        thread.setLog(log);
        thread.start();
        return thread;
    }
    
}
