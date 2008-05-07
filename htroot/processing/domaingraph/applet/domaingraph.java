import processing.core.*; import traer.physics.*; import traer.animation.*; import processing.net.*; import java.applet.*; import java.awt.*; import java.awt.image.*; import java.awt.event.*; import java.io.*; import java.net.*; import java.text.*; import java.util.*; import java.util.zip.*; import javax.sound.midi.*; import javax.sound.midi.spi.*; import javax.sound.sampled.*; import javax.sound.sampled.spi.*; import java.util.regex.*; import javax.xml.parsers.*; import javax.xml.transform.*; import javax.xml.transform.dom.*; import javax.xml.transform.sax.*; import javax.xml.transform.stream.*; import org.xml.sax.*; import org.xml.sax.ext.*; import org.xml.sax.helpers.*; public class domaingraph extends PApplet {// Domain visualization graph for YaCy
// by Michael Christen
//
// this applet uses code and the physics engine from
// http://www.cs.princeton.edu/~traer/physics/
// from Jeffrey Traer Bernstein
// redistribution is granted according to forum posting in
// http://processing.org/discourse/yabb_beta/YaBB.cgi?board=os_libraries_tools;action=display;num=1139193613





final float NODE_SIZE       = 6;
final float EDGE_LENGTH     = 50;
final float EDGE_STRENGTH   = 0.01f;
final float SPACER_STRENGTH = 10;

ParticleSystem physics;
Smoother3D centroid;
PFont font;
Client myClient; 
Particle center0, center1; // two gravity centers to support object ordering for non-quadratic fields
String parsingHostName = "";
String parsingHostID = "";
HashMap nodes = new HashMap(); // map that holds host objects

public void setup() {
  
  String[] fontList = PFont.list();
  //println(fontList);
  font = createFont(fontList[0], 32); //just take any, should be mostly Arial
  textFont(font, 12); 
  
  size(660, 400);
  smooth();
  frameRate( 24 );
  strokeWeight( 2 );
  ellipseMode( CENTER );       
  
  physics = new ParticleSystem( 0, 0.25f );
  centroid = new Smoother3D( 0.8f );

  initializePhysics();
  initRequest();
}

public void initRequest() {
  myClient = new Client(this, "localhost", 8080);
  myClient.write("GET /xml/webstructure.xml HTTP/1.1\n");
  myClient.write("Host: localhost\n\n");
}

public void processRequestResponse() {
  if (myClient.available() > 0) {
    String line = myClient.readStringUntil((byte) 10);
    //println("Line: " + line);
    if (line == null) line = ""; else line = line.trim();
    if (line.startsWith("<domain")) processDomain(parseProps(line.substring(7, line.length() - 1).trim()));
    if (line.startsWith("<citation")) processCitation(parseProps(line.substring(9, line.length() - 2).trim()));
  }
}

public HashMap parseProps(String s) {
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

public void processDomain(HashMap props) {
  //println("Domain: " + props.toString());
  parsingHostName = (String) props.get("host"); if (parsingHostName == null) parsingHostName = "";
  parsingHostID = (String) props.get("id"); if (parsingHostID == null) parsingHostID = "";
  host h = new host(parsingHostName, physics.makeParticle(1.0f, random(0, EDGE_LENGTH * 30), random(- EDGE_LENGTH * 2, EDGE_LENGTH * 2), 0));
  nodes.put(parsingHostID, h);
  addAttraction(h.node);
}

public void processCitation(HashMap props) {
  //println("Citation: " + props.toString());
  String host = (String) props.get("host"); if (host == null) host = "";
  String id = (String) props.get("id"); if (id == null) id = "";
  int count = 0;
  try {
  String counts = (String) props.get("count"); if (counts != null) count = Integer.parseInt(counts);
  } catch (NumberFormatException e) {}
  // find the two nodes that have a relation
  host h = (host) nodes.get(id);
  if (h == null) return; // host is not known TODO: store these and create relation later
  host p = (host) nodes.get(parsingHostID); // this should be successful
  addRelation(h.node, p.node);
}

public void draw() {
  processRequestResponse();
  
  physics.tick( 1.0f ); 
  if (physics.numberOfParticles() > 1) updateCentroid();
  centroid.tick();

  background( 0 );
  translate( width/2 , height/2 );
  scale( centroid.z() );
  translate( -centroid.x(), -centroid.y() );
 
  drawNetwork();
}

public void drawNetwork() {      
  fill( 100, 255, 100 );
  
  // draw vertices
  noStroke();
  String name;
  Iterator j = nodes.values().iterator();
  host h;
  while (j.hasNext()) {
    h = (host) j.next();
    Particle v = h.node;
    ellipse(v.position().x(), v.position().y(), NODE_SIZE, NODE_SIZE);
    name = h.name;
    text(name, v.position().x() - (name.length() * 26 / 10), v.position().y() + 14);
  }
  
  // draw center
  /*
  ellipse( center0.position().x(), center0.position().y(), NODE_SIZE * 2, NODE_SIZE * 2 );
  name = "Center0";
  text(name, center0.position().x() - (name.length() * 26 / 10), center0.position().y() + 14);
  ellipse( center1.position().x(), center1.position().y(), NODE_SIZE * 2, NODE_SIZE * 2 );
  name = "Center1";
  text(name, center1.position().x() - (name.length() * 26 / 10), center1.position().y() + 14);
  */
  
  // draw edges 
  stroke( 160 );
  beginShape( LINES );
  for ( int i = 0; i < physics.numberOfSprings(); ++i ) {
    Spring e = physics.getSpring( i );
    Particle a = e.getOneEnd();
    Particle b = e.getTheOtherEnd();
    vertex( a.position().x(), a.position().y() );
    vertex( b.position().x(), b.position().y() );
  }
  endShape();
}

public void keyPressed() {
  if ( key == 'c' ) {
    initializePhysics();
    return;
  }
  
  if ( key == ' ' ) {
    Particle p = physics.makeParticle();
    addRelation(p, physics.getParticle( (int) random( 0, physics.numberOfParticles()-1) ));
    addAttraction(p);
    return;
  }
}

public void updateCentroid() {
  float 
    xMax = Float.NEGATIVE_INFINITY, 
    xMin = Float.POSITIVE_INFINITY, 
    yMin = Float.POSITIVE_INFINITY, 
    yMax = Float.NEGATIVE_INFINITY;

  for ( int i = 0; i < physics.numberOfParticles(); ++i ) {
    Particle p = physics.getParticle( i );
    xMax = max( xMax, p.position().x() );
    xMin = min( xMin, p.position().x() );
    yMin = min( yMin, p.position().y() );
    yMax = max( yMax, p.position().y() );
  }
  float deltaX = xMax-xMin;
  float deltaY = yMax-yMin;
  if ( deltaY > deltaX )
    centroid.setTarget( xMin + 0.5f*deltaX, yMin +0.5f*deltaY, height/(deltaY+50) );
  else
    centroid.setTarget( xMin + 0.5f*deltaX, yMin +0.5f*deltaY, width/(deltaX+50) );
}

public void initializePhysics() {
  physics.clear();
  center0 = physics.makeParticle(1.0f, 0, 0, 0);
  center0.makeFixed();
  center1 = physics.makeParticle(1.0f, EDGE_LENGTH * 30, 0, 0);
  center1.makeFixed();
  centroid.setValue( 0, 0, 1.0f );
}

public void addAttraction(Particle p) {
  physics.makeAttraction(center0, p, 5000.0f, 3 * EDGE_LENGTH);
  physics.makeAttraction(center1, p, 5000.0f, 3 * EDGE_LENGTH);

  // spacers
  for ( int i = 0; i < physics.numberOfParticles(); ++i ) {
    Particle q = physics.getParticle( i );
    if (p != q) physics.makeAttraction( p, q, -SPACER_STRENGTH, 20);
  }
}

public void addRelation(Particle p, Particle other) {
  physics.makeSpring( p, other, EDGE_STRENGTH, EDGE_STRENGTH, EDGE_LENGTH );
  //p.moveTo( q.position().x() + random( -1, 1 ), q.position().y() + random( -1, 1 ), 0 );
}

static class host {
  String name;
  Particle node;
  public host(String name, Particle node) {
    this.name = name;
    this.node = node;
  }
}

  static public void main(String args[]) {     PApplet.main(new String[] { "domaingraph" });  }}