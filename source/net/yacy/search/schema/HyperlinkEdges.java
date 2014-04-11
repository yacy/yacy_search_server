package net.yacy.search.schema;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.id.MultiProtocolURL;

public class HyperlinkEdges implements Iterable<HyperlinkEdge> {

    public static class Targets {
        public Set<HyperlinkEdge.Target> targets;
        public int depth;

        public Targets(int depth) {
            this.targets = new LinkedHashSet<HyperlinkEdge.Target>();
            this.depth = depth;
        }
    }

    private final Map<MultiProtocolURL, Targets> edges;
    private final Map<MultiProtocolURL, Integer> singletonDepth;
    
    public HyperlinkEdges() {
        this.edges = new LinkedHashMap<MultiProtocolURL, Targets>();
        this.singletonDepth = new HashMap<MultiProtocolURL, Integer>();
    }

    public void add(final HyperlinkEdge edge) {
        addEdge(edge.source, edge.target);
    }
    
    public void addEdge(final MultiProtocolURL source, final HyperlinkEdge.Target target) {
        Targets targets = this.edges.get(source);
        Integer d = this.singletonDepth.get(source);
        if (d == null) d = -1; else this.singletonDepth.remove(source);
        if (target.type == HyperlinkType.Inbound) {
            Integer e = this.singletonDepth.remove(target);
            if (e != null && d.intValue() == -1) d = e.intValue() - 1;
        }
        if (targets == null) {
            targets = new Targets(d.intValue());
            this.edges.put(source, targets);
        }
        targets.targets.add(target);
    }
    
    public int size() {
        int s = 0;
        for (Targets t: edges.values()) s += t.targets.size();
        return s;
    }
    
    public void addAll(final HyperlinkEdges oe) {
        for (Map.Entry<MultiProtocolURL, Targets> edges: oe.edges.entrySet()) {
            for (HyperlinkEdge.Target t: edges.getValue().targets) {
                this.addEdge(edges.getKey(), t);
            }
        }
    }
    
    public void updateDepth(final MultiProtocolURL url, final int newdepth) {
        Targets targets = this.edges.get(url);
        if (targets == null) {
            singletonDepth.put(url, newdepth);
            return;
        }
        if (targets.depth == -1) {
            targets.depth = newdepth;
        } else {
            targets.depth = Math.min(targets.depth,  newdepth);
        }
    }
    
    public int getDepth(final MultiProtocolURL url) {
        Targets targets = this.edges.get(url);
        if (targets != null) return targets.depth;
        Integer d = this.singletonDepth.get(url);
        if (d != null) return d.intValue();
        // now search in targets
        String targetHost = url.getHost();
        for (Map.Entry<MultiProtocolURL, Targets> e: this.edges.entrySet()) {
            if (e.getValue().targets.contains(url)) {
                String sourceHost = e.getKey().getHost();
                // check if this is an inbound match
                if ((sourceHost == null && targetHost == null) || (sourceHost != null && targetHost != null && sourceHost.equals(targetHost))) return e.getValue().depth + 1;
            }
        }
        return -1;
    }

    @Override
    public Iterator<HyperlinkEdge> iterator() {
        final Iterator<Map.Entry<MultiProtocolURL, Targets>> i = this.edges.entrySet().iterator();
        @SuppressWarnings("unchecked")
        final Iterator<HyperlinkEdge.Target>[] tc = new Iterator[1];
        tc[0] = null;
        final MultiProtocolURL[] su = new MultiProtocolURL[1];
        su[0] = null;
        return new Iterator<HyperlinkEdge>() {

            @Override
            public boolean hasNext() {
                return i.hasNext() || (tc[0] != null && tc[0].hasNext());
            }

            @Override
            public HyperlinkEdge next() {
                while (tc[0] == null || !tc[0].hasNext()) {
                    Map.Entry<MultiProtocolURL, Targets> entry = i.next();
                    tc[0] = entry.getValue().targets.iterator();
                    su[0] = entry.getKey();
                }
                if (!tc[0].hasNext()) return null;
                return new HyperlinkEdge(su[0], tc[0].next());
            }

            @Override
            public void remove() {
                tc[0].remove();
            }
            
        };
    }
}
