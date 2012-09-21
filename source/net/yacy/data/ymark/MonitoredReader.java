package net.yacy.data.ymark;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * This class monitors the read progress
 *
 */
public class MonitoredReader extends FilterReader {
   private volatile long mark = 0;
   private volatile long location = 0;
   private final int threshold;
   private final long maxProgress;
   private long lastTriggeredLocation = 0;
   private ChangeListener listener = null;
   
   public MonitoredReader(Reader in, int threshold, long maxProgress) {
      super(in);
      this.threshold = threshold;
      this.maxProgress = maxProgress;
   }
   
   public void addChangeListener(ChangeListener l) {
	   this.listener = l;
   }
   
   protected void triggerChanged(final long location) {
	   if ( threshold > 0 && Math.abs( location-lastTriggeredLocation ) < threshold ) 
		   return;
	   lastTriggeredLocation = location;
	   if (listener == null)
		   return;
	   listener.stateChanged(new ChangeEvent(this));
   }
   
   public long getProgress() { 
	   return this.location; 
   }
   
   public long maxProgress() {
	   return this.maxProgress;
   }

   @Override 
   public int read() throws IOException {
      final int i = super.read();
      if ( i != -1 ) 
    	  triggerChanged(location++);
      return i;
   }
   
   @Override
   public int read(char[] cbuf, int off, int len) throws IOException {
		final int i = super.read(cbuf, off, len);
		if ( i != -1 ) 
			triggerChanged(location+=i);
	    return i;
   }
   
   @Override
   public int read(char[] cbuf) throws IOException {
		final int i = super.read(cbuf);
		if ( i != -1 ) 
			triggerChanged(location+=i);
	    return i;
   }
   
   @Override
   public int read(CharBuffer target) throws IOException {
		final int i = super.read(target);
		if ( i != -1 ) 
			triggerChanged(location+=i);
	    return i;
   }

   @Override 
   public long skip(long n) throws IOException {
      final long i = super.skip(n);
      if ( i != -1 ) 
    	  triggerChanged(location+=i);
      return i;
   }

   @Override 
   public synchronized void mark(int readlimit) throws IOException {
      super.mark(readlimit);
      mark = location;
   }

   @Override 
   public synchronized void reset() throws IOException {
      super.reset();
      if ( location != mark ) 
    	  triggerChanged(location = mark);
   }   
}
