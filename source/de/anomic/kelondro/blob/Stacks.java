// Stacks.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.07.2009 on http://yacy.net
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
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

package de.anomic.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class Stacks {

    private File stacksLocation;
    private String stacksPrefix;
    private ConcurrentHashMap<String, StackInstance> stacks;
    
    /**
     * create a stack organizing object.
     * Stacks can be created on-the-fly in the given stacksLocation directory
     * using simple push operations that create first entries in the stack
     * Stacks that do not contain any element upon the close() operation are removed
     * @param stackFile
     * @throws IOException
     */
    public Stacks(final File stacksLocation, String stacksPrefix) {
        if (!stacksLocation.exists()) stacksLocation.mkdirs();
        assert stacksLocation.isDirectory();
        this.stacksLocation = stacksLocation;
        this.stacksPrefix = stacksPrefix;
        
        // initialize the stacks map
        this.stacks = new ConcurrentHashMap<String, StackInstance>();
        String[] sl = this.stacksLocation.list();
        for (String s: sl) {
            if (!s.startsWith(this.stacksPrefix + "_")) continue;
            StackInstance si;
            try {
                si = new StackInstance(new File(this.stacksLocation, s));
                this.stacks.put(si.name, si);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private class StackInstance {
        private String name;
        private File location;
        private Stack stack;
        
        public StackInstance(File location) throws IOException {
            String filename = location.getName();
            assert filename.startsWith(stacksPrefix + "_");
            assert filename.endsWith(".bstack");
            this.name = filename.substring(stacksPrefix.length() + 1, filename.length() - 7);
            this.location = location;
            this.stack = new Stack(location);
        }
        
        public StackInstance(String stack) throws IOException {
            this.location = new File(stacksLocation, stacksPrefix + "_" + stack + ".bstack");
            this.name = stack;
            this.stack = new Stack(location);
        }
        
        public String name() {
            return this.name;
        }
        
        public Stack stack() {
            return this.stack;
        }
        
        public File location() {
            return this.location;
        }
    }
    
    private Stack getStack(String stack) {
        StackInstance si = this.stacks.get(stack);
        if (si == null) {
            // create a new Stack on the fly
            try {
                si = new StackInstance(stack);
                this.stacks.put(stack, si);
                return si.stack();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return si.stack();
    }
    
    /**
     * get the number of stacks organized by this object
     * @return the number of stack objects
     */
    public int size() {
        return this.stacks.size();
    }
    
    /**
     * iterate all stack names
     * @return an iterator for the stack names
     */
    public Iterator<String> stacks() {
        return this.stacks.keySet().iterator();
    }
    
    /**
     * get the size of a stack
     * @param stack the name of the stack
     * @return the number of entries on the stack
     */
    public int size(String stack) {
        Stack s = getStack(stack);
        if (s == null) return -1;
        return s.size();
    }
    
    /**
     * Iterate all handles from a stack as Long numbers
     * @param stack the name of the stack
     * @return an iterator of all handles of the stack
     * @throws IOException
     */
    public synchronized Iterator<Long> handles(String stack) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.handles();
    }
    
    /**
     * push a new element on the top of the stack
     * @param stack the name of the stack
     * @param b the new stack element
     * @return the handle used to store the new element
     * @throws IOException
     */
    public long push(String stack, byte[] b) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return -1;
        return s.push(b);
    }
    
    /**
     * push a new element on the top of the stack using a entry object
     * this is only useful for internal processes where a special handle
     * is created
     * @param stack the name of the stack
     * @param b the new stack element
     * @return the handle used to store the new element
     * @throws IOException
     */
    protected void push(String stack, Stack.Entry e) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return;
        s.push(e);
    }
    
    /**
     * get an element from the stack using the handle
     * @param stack the name of the stack
     * @param handle
     * @return the object that belongs to the handle
     *         or null if no such element exists
     * @throws IOException
     */
    public byte[] get(String stack, long handle) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.get(handle);
    }
    
    /**
     * remove an element from the stack using the entry handle
     * @param stack the name of the stack
     * @param handle
     * @return the removed element
     * @throws IOException
     */
    public byte[] remove(String stack, long handle) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.remove(handle);
    }
    
    /**
     * remove the top element from the stack
     * @param stack the name of the stack
     * @return the top element or null if the stack is empty
     * @throws IOException
     */
    public Stack.Entry pop(String stack) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.pop();
    }
    
    /**
     * return the top element of the stack.
     * The element is not removed from the stack.
     * Successive calls to this method will always return the same element
     * @param stack the name of the stack
     * @return the element on the top of the stack or null, if stack is empty
     * @throws IOException
     */
    public Stack.Entry top(String stack) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.top();
    }
    
    /**
     * remove the bottom element from the stack
     * @param stack the name of the stack
     * @return the bottom element or null if the stack is empty
     * @throws IOException
     */
    public Stack.Entry pot(String stack) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.pot();
    }
    
    /**
     * return the bottom element of the stack.
     * The element is not removed from the stack.
     * Successive calls to this method will always return the same element
     * @param stack the name of the stack
     * @return the element on the bottom of the stack or null, if stack is empty
     * @throws IOException
     */
    public Stack.Entry bot(String stack) throws IOException {
        Stack s = getStack(stack);
        if (s == null) return null;
        return s.bot();
    }
    
    /**
     * close all stack files
     */
    public synchronized void close() {
        for (StackInstance se: this.stacks.values()) {
            se.stack.close();
        }
    }
    
    public void finalize() {
        this.close();
    }
}
