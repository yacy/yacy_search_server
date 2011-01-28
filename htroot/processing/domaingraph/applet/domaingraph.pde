// Domain visualization graph for YaCy
// by Michael Christen
//
// this applet uses code and the physics engine from
// http://www.cs.princeton.edu/~traer/physics/
// from Jeffrey Traer Bernstein
// redistribution is granted according to forum posting in
// http://processing.org/discourse/yabb_beta/YaBB.cgi?board=os_libraries_tools;action=display;num=1139193613

import traer.physics.*;
import traer.animation.*;
import processing.net.*;

final float NODE_SIZE       = 6;
final float EDGE_LENGTH     = 30;
final float EDGE_STRENGTH   = 0.001;
final float SPACER_STRENGTH = 250;

ParticleSystem physics;
Smoother3D centroid;
PFont font;
float x = 0.0;
float y = 0.0;
float z = 1.0;
Client myClient; 
Particle center0, center1; // two gravity centers to support object ordering for non-quadratic fields
String parsingHostName = "";
String parsingHostID = "";
HashMap nodes = new HashMap(); // map that holds host objects
String host;
int port;
float a = 0.0;
long lastUpdate = Long.MAX_VALUE;
boolean initTime = true;

void setup() {
  
  String[] fontList = PFont.list();
  //println(fontList);
  font = createFont(fontList[0], 32); //just take any, should be mostly Arial
  textFont(font, 9); 
  
  size(660, 400);
  smooth();
  frameRate( 6 );
  strokeWeight( 1 );
  ellipseMode( CENTER );       
  
  physics = new ParticleSystem( 0, 0.25 );
  centroid = new Smoother3D( 0.8 );

  initializePhysics();
  URL url = null;
  try {
    url = getDocumentBase();
  } catch (NullPointerException e) {}
  if (url == null) {
    host="localhost";
    port=8090;
  } else {
    host=url.getHost();
    port=url.getPort();
  }
  //println("CodeBase: " + url);
  //println("host: " + host);
  //println("port: " + port);
  
  initRequest(false);
}

void initializePhysics() {
  physics.clear();
  center0 = physics.makeParticle(1.0, -EDGE_LENGTH * 10, 0, 0);
  center0.makeFixed();
  center1 = physics.makeParticle(1.0, EDGE_LENGTH * 10, 0, 0);
  center1.makeFixed();
  centroid.setValue( 0, 0, 1.0 );
}

void draw() {
  processRequestResponse(50);
  
  physics.tick( 1.0 ); 
  HashSet invisible = invisibleParticles();
  deleteParticles(invisible);
  if (physics.numberOfParticles() > 1) updateCentroid(invisible);
  centroid.tick();

  background( 0 );
  translate( width/2 , height/2 );
  scale( centroid.z() );
  translate( -centroid.x(), -centroid.y() );
 
  drawNetwork();
}

void initRequest(boolean update) {
  myClient = new Client(this, host, port);
  myClient.write((update) ? "GET /api/webstructure.xml?latest= HTTP/1.1\n" : "GET /xml/webstructure.xml HTTP/1.1\n");
  myClient.write("Host: localhost\n\n");
}

void processRequestResponse(int steps) {
  if (((myClient == null) || (myClient.available() <= 0)) && (System.currentTimeMillis() - lastUpdate > 10000)) {
    initRequest(true);
    lastUpdate = Long.MAX_VALUE;
    return;
  }
  for (int i = 0; i < steps; i++) {
    if (myClient.available() > 0) {
      String line = myClient.readStringUntil((byte) 10);
      //println("Line: " + line);
      if (line == null) line = ""; else line = line.trim();
      /*
      <domain host="www.oreilly.com" id="-1po3Y" date="20090510">
        <reference id="uU2r5Q" count="2">www.catb.org</reference>
        <reference id="zG43kY" count="516">oreilly.com</reference>
        <reference id="QEf_LZ" count="44">www.oreillynet.com</reference>
        
      </domain>
      */
      int p = line.indexOf("<domain");
      if (p >= 0) {
        //println("domain   :" + line.substring(p + 8, line.length() - 1).trim());
        processDomain(parseProps(line.substring(p + 8, line.length() - 1).trim()));
      }
      p = line.indexOf("<reference");
      if (p >= 0) {
        int q = line.indexOf("</reference>");
        if (q > 0) {
            int r = line.lastIndexOf('>', q);
            if (r > 0) {
              String refhost = line.substring(r, q);
              //println("reference:" + line.substring(p + 11, r).trim());
              processCitation(refhost, parseProps(line.substring(p + 11, r).trim()));
            }
        }
      }
      lastUpdate = System.currentTimeMillis();
    } else {
      initTime = false;
    }
  }
}

void processDomain(HashMap props) {
  //println("Domain: " + props.toString());
  parsingHostName = (String) props.get("host"); if (parsingHostName == null) parsingHostName = "";
  parsingHostID = (String) props.get("id"); if (parsingHostID == null) parsingHostID = "";
  host h = (host) nodes.get(parsingHostID);
  if (h != null) {
    h.time = System.currentTimeMillis();
    return;
  }
  h = new host(parsingHostName, physics.makeParticle(1.0, EDGE_LENGTH * 20 * cos(a), -EDGE_LENGTH * 10 * sin(a), 0));
  a += TWO_PI/256.0 + TWO_PI / 2; if (a > TWO_PI) a -= TWO_PI;
  nodes.put(parsingHostID, h);
  addAttraction(h.node);
}

void processCitation(String host, HashMap props) {
  //println("Citation: " + props.toString());
  String id = (String) props.get("id"); if (id == null) id = "";
  int count = 0;
  try {
  String counts = (String) props.get("count"); if (counts != null) count = Integer.parseInt(counts);
  } catch (NumberFormatException e) {}
  // find the two nodes that have a relation
  host h = (host) nodes.get(id);
  if (h == null) {
    return; /*
    h = new host(host, physics.makeParticle(1.0, EDGE_LENGTH * 20 * cos(a), -EDGE_LENGTH * 10 * sin(a), 0));
    a += TWO_PI/256.0 + TWO_PI / 2; if (a > TWO_PI) a -= TWO_PI;
    nodes.put(id, h);
    addAttraction(h.node);*/
  }
  h.time = System.currentTimeMillis();
  host p = (host) nodes.get(parsingHostID); // this should be successful
  if (p == null) return;
  // prevent that a spring is made twice
  for ( int i = 0; i < physics.numberOfSprings(); ++i ) {
    Spring e = physics.getSpring(i);
    Particle a = e.getOneEnd();
    Particle b = e.getTheOtherEnd();
    if (((a == h.node) && (b == p.node)) || ((b == h.node) && (a == p.node))) return;
  }
  physics.makeSpring(h.node, p.node, EDGE_STRENGTH, EDGE_STRENGTH, EDGE_LENGTH );
}

HashMap parseProps(String s) {
  String[] l = s.split(" ");
  HashMap map = new HashMap();
  int p;
  String z;
  for (int i = 0; i < l.length; i++) {
    p = l[i].indexOf("=");
    if (p > 0) {
      z = l[i].substring(p + 1).trim();
      if (z.charAt(0) == '"') z = z.substring(1);
      if (z.charAt(z.length() - 1) == '"') z = z.substring(0, z.length() - 1);
      map.put(l[i].substring(0, p), z);
    }
  }
  return map;
}

HashSet invisibleParticles() {
  // get nodes that have no edges
  HashSet particles = new HashSet();
  Iterator j = nodes.values().iterator();
  host h;
  long t = 0, n = System.currentTimeMillis();
  while (j.hasNext()) {
    h = (host) j.next();
    t += n - h.time;
    particles.add(h.node);
  }
  t = t / (nodes.size() + 1);
  for ( int i = 0; i < physics.numberOfSprings(); ++i ) {
    Spring e = physics.getSpring(i);
    particles.remove(e.getOneEnd());
    particles.remove(e.getTheOtherEnd());
  }
  // add more nodes if the number is too large
  if (nodes.size() > 80) {
    j = nodes.values().iterator();
    while (j.hasNext()) {
      h = (host) j.next();
      if (n - h.time > 15000) particles.add(h.node);
      if (nodes.size() - particles.size() < 80) break;
    }
  }
  return particles;
}

void deleteParticles(HashSet particles) {
  Iterator j = nodes.values().iterator();
  host h;
  while (j.hasNext()) {
    h = (host) j.next();
    if (particles.contains(h.node)) {
      h.node.kill();
      j.remove();
    }
  }
}

void drawNetwork() {
  
  // draw vertices
  fill( 120, 255, 120 );
  noStroke();
  String name;
  host h;
  Iterator j = nodes.values().iterator();
  while (j.hasNext()) {
    h = (host) j.next();
    Particle v = h.node;
    ellipse(v.position().x(), v.position().y(), NODE_SIZE, NODE_SIZE);
    name = h.name;
    text(name, v.position().x() - (name.length() * 26 / 10), v.position().y() + 14);
  }

  // draw center
  //fill( 255, 0, 0 );
  //ellipse( center0.position().x(), center0.position().y(), NODE_SIZE * 2, NODE_SIZE * 2 );
  //ellipse( center1.position().x(), center1.position().y(), NODE_SIZE * 2, NODE_SIZE * 2 );

  // draw edges 
  stroke( 200 );
  for ( int i = 0; i < physics.numberOfSprings(); ++i ) {
    Spring e = physics.getSpring( i );
    Particle a = e.getOneEnd();
    Particle b = e.getTheOtherEnd();
    line(a.position().x(), a.position().y(), b.position().x(), b.position().y());
  }

}

void keyPressed() {
  if ( key == 'c' ) initializePhysics();
  if ( key == 'a' ) x = Math.max(-1.0, x - 0.1);
  if ( key == 'd' ) x = Math.min( 1.0, x + 0.1);
  if ( key == 'w' ) y = Math.max(-1.0, y - 0.1);
  if ( key == 's' ) y = Math.min( 1.0, y + 0.1);
  if ( key == '-' ) z = Math.max( 1.0, z - 1.0);
  if ( key == '+' ) z = Math.min(10.0, z + 1.0);
  if ( key == '0' ) { x = 0.0; y = 0.0; z = 1.0; }
  if ( key == 't' ) {
    HashSet hs = new HashSet();
    for (int i = 0; i < physics.numberOfParticles(); ++i ) {
      hs.add(physics.getParticle(i));
    }
    for (int i = 0; i < physics.numberOfSprings(); ++i ) {
      hs.remove(physics.getSpring(i).getOneEnd());
      hs.remove(physics.getSpring(i).getTheOtherEnd());
    }
    Iterator i = hs.iterator();
    while (i.hasNext()) {
      ((Particle) i.next()).kill();
    }
    return;
  }
}

void updateCentroid(HashSet invisible) {
  float 
    xMax = Float.NEGATIVE_INFINITY, 
    xMin = Float.POSITIVE_INFINITY, 
    yMin = Float.POSITIVE_INFINITY, 
    yMax = Float.NEGATIVE_INFINITY;

  for (int i = 0; i < physics.numberOfParticles(); ++i) {
    Particle p = physics.getParticle(i);
    if ((i >= 2) && ((p == center0) || (p == center1) || (invisible.contains(p)))) continue;
    xMax = max( xMax, p.position().x() );
    xMin = min( xMin, p.position().x() );
    yMin = min( yMin, p.position().y() );
    yMax = max( yMax, p.position().y() );
  }
  
  float deltaX = xMax-xMin;
  float deltaY = yMax-yMin;
  centroid.setTarget(
    xMin + (x + 1) * 0.5 * deltaX,
    yMin + (y + 1) * 0.5 * deltaY,
    z * ((deltaY > deltaX) ? height / (deltaY + 50) : width / (deltaX + 50))
  );
}

void addAttraction(Particle p) {
  physics.makeAttraction(center0, p, SPACER_STRENGTH * 10000.0, 100 * EDGE_LENGTH);
  physics.makeAttraction(center1, p, SPACER_STRENGTH * 10000.0, 100 * EDGE_LENGTH);

  // spacers
  for ( int i = 0; i < physics.numberOfParticles(); ++i ) {
    Particle q = physics.getParticle( i );
    if (p != q) physics.makeAttraction( p, q, -SPACER_STRENGTH, 20);
  }
}

static class host {
  String name;
  Particle node;
  long time;
  public host(String name, Particle node) {
    this.name = name;
    this.node = node;
    this.time = System.currentTimeMillis();
  }
}
