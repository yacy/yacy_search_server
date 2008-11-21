// kelondroChunkIterator.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.01.2008 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.kelondro;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class kelondroChunkIterator implements Iterator<byte[]> {

    private final int chunksize;
    
    /**
     * create a ChunkIterator
     * a ChunkIterator uses a BufferedInputStream to iterate through the file
     * and is therefore a fast option to get all elements in the file as a sequence
     * @param file: the file
     * @param recordsize: the size of the elements in the file
     * @param chunksize: the size of the chunks that are returned by next(). remaining bytes until the lenght of recordsize are skipped
     * @throws FileNotFoundException 
     */
    
    /*
    private final DataInputStream stream;
    private byte[] nextBytes;
    public kelondroChunkIterator(final File file, final int recordsize, final int chunksize) throws FileNotFoundException {
        assert (file.exists());
        assert file.length() % recordsize == 0;
        this.recordsize = recordsize;
        this.chunksize = chunksize;
        this.stream = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 64 * 1024));
        this.nextBytes = next0();
    }
    
    public boolean hasNext() {
        return nextBytes != null;
    }

    public byte[] next0() {
        final byte[] chunk = new byte[chunksize];
        int r, s;
        try {
            // read the chunk
            this.stream.readFully(chunk);
            // skip remaining bytes
            r = chunksize;
            while (r < recordsize) {
                s = (int) this.stream.skip(recordsize - r);
                assert s > 0;
                if (s <= 0) return null;
                r += s;
            }
            return chunk;
        } catch (final IOException e) {
            return null;
        }
    }

    public byte[] next() {
        final byte[] n = this.nextBytes;
        this.nextBytes = next0();
        return n;
    }
    
    public void remove() {
        throw new UnsupportedOperationException();
    }
    */
    
    
    ExecutorService service = Executors.newFixedThreadPool(2);
    filechunkProducer producer;
    filechunkSlicer slicer;
    Future<Integer> producerResult;
    Future<Integer> slicerResult;
    byte[] nextRecord;
    
    
    public kelondroChunkIterator(final File file, final int recordsize, final int chunksize) throws FileNotFoundException {
        assert (file.exists());
        assert file.length() % recordsize == 0;
        this.chunksize = chunksize;

        service = Executors.newFixedThreadPool(2);
        // buffer size and count calculation is critical, because wrong values
        // will cause blocking of the concurrent consumer/producer threads
        int filebuffersize = 1024 * 16;
        int chunkbuffercountmin = filebuffersize / recordsize + 1; // minimum
        int filebuffercount = 1024 * 1024 / filebuffersize; // max 1 MB
        int chunkbuffercount = chunkbuffercountmin * filebuffercount + 1;
        producer = new filechunkProducer(file, filebuffersize, filebuffercount);
        slicer = new filechunkSlicer(producer, recordsize, chunksize, chunkbuffercount);
        producerResult = service.submit(producer);
        slicerResult = service.submit(slicer);
        service.shutdown();
        nextRecord = slicer.consume();
    }
    
    public boolean hasNext() {
        return nextRecord != null;
    }

    public byte[] next() {
        if (nextRecord == null) return null;
        byte[] n = new byte[chunksize];
        System.arraycopy(nextRecord, 0, n, 0, chunksize);
        slicer.recycle(nextRecord);
        nextRecord = slicer.consume();
        return n;
    }
    
    public void remove() {
        throw new UnsupportedOperationException();
    }
    
    private static class filechunkSlicer implements Callable<Integer> {

        private filechunkProducer producer;
        private static byte[] poison = new byte[0];
        private BlockingQueue<byte[]> empty;
        private BlockingQueue<byte[]> slices;
        private int slicesize, head;
        
        public filechunkSlicer(filechunkProducer producer, final int slicesize, int head, int stacksize) throws FileNotFoundException {
            assert producer != null;
            this.producer = producer;
            this.slices = new ArrayBlockingQueue<byte[]>(stacksize);
            this.empty  = new ArrayBlockingQueue<byte[]>(stacksize);
            this.slicesize = slicesize;
            this.head = head;
            // fill the empty queue
            for (int i = 0; i < stacksize; i++) empty.add(new byte[head]);
        }

        public void recycle(byte[] c) {
            try {
                empty.put(c);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        public byte[] consume() {
            try {
                byte[] b = slices.take(); // leer
                if (b == poison) return null; else return b;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }
        
        private void slice(byte[] from, int startfrom, byte[] to, int startto, int len) {
            if (startto >= head) return;
            if (startto + len > head) len = head - startto;
            assert to.length == head;
            System.arraycopy(from, startfrom, to, startto, len);
        }
        
        public Integer call() {
            filechunk c;
            int p;
            try {
                byte[] slice = empty.take();
                int slicec = 0;
                consumer: while(true) {
                    c = producer.consume();
                    if (c == null) {
                        // finished. put poison into slices queue
                        slices.put(poison);
                        break consumer;
                    }
                    p = 0;
                    // copy as much as possible to the current slice
                    slicefiller: while (true) {
                        assert slicesize > slicec;
                        if (c.n - p >= slicesize - slicec) {
                            // a full slice can be produced
                            slice(c.b, p, slice, slicec, slicesize - slicec);
                            // the slice is now full
                            p += slicesize - slicec;
                            slices.put(slice);
                            slice = empty.take();
                            slicec = 0;
                            continue slicefiller;
                        } else {
                            // fill only a part of the slice and wait for next chunk
                            slice(c.b, p, slice, slicec, c.n - p);
                            // the chunk is now fully read
                            producer.recycle(c);
                            slicec += c.n - p;
                            continue consumer;
                        }
                    }
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            return new Integer(0);
        }
        
    }
    
    private static class filechunk {
        public byte[] b;
        public int n;
        public filechunk(int len) {
            b = new byte[len];
            n = 0;
        }
    }
    
    /**
     * the filechunkProducer reads an in put file and stores chunks of the results
     * into a buffer. All elements stored in the buffer must be recycled.
     * The class does not allocate more memory than a given chunk size multiplied with a
     * number of chunks that shall be stored in a queue for processing.
     */
    private static class filechunkProducer implements Callable<Integer> {

        private BlockingQueue<filechunk> empty;
        private BlockingQueue<filechunk> filed;
        private static filechunk poison = new filechunk(0);
        private FileInputStream fis;
        
        public filechunkProducer(File in, int bufferSize, int bufferCount) throws FileNotFoundException {
            empty = new ArrayBlockingQueue<filechunk>(bufferCount);
            filed = new ArrayBlockingQueue<filechunk>(bufferCount);
            fis = new FileInputStream(in);
            // fill the empty queue
            for (int i = 0; i < bufferCount; i++) empty.add(new filechunk(bufferSize));
        }
        
        public void recycle(filechunk c) {
            try {
                empty.put(c);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        public filechunk consume() {
            try {
                filechunk f = filed.take(); // leer
                if (f == poison) return null; else return f;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }

        public Integer call() {
            try {
                filechunk c;
                while(true) {
                    c = empty.take(); // leer
                    c.n = fis.read(c.b);
                    if (c.n <= 0) break;
                    filed.put(c);
                }
                // put poison into consumer queue so he can stop consuming
                filed.put(poison);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
            return new Integer(0);
        }
        
    }

}
