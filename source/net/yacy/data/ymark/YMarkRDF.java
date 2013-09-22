package net.yacy.data.ymark;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.lod.vocabulary.AnnoteaA;
import net.yacy.cora.lod.vocabulary.AnnoteaB;
import net.yacy.cora.lod.vocabulary.DCElements;
import net.yacy.cora.lod.vocabulary.Rdf;
import net.yacy.kelondro.blob.Tables;

import com.hp.hpl.jena.rdf.model.Bag;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

public class YMarkRDF {
	
	public final Model model;
	
	public final static String USER = "USER";
	public final static String TYPE = "TYPE";
	public final static String SUBTOPIC = "SUBTOPIC";

	private final Map<String, Property> property;
	
	public final static String BOOKMARK = "/YMarks.rdf?id=";
	private final StringBuilder resourceURI;
	private final int len;
	
	public YMarkRDF(final String peerURI) {
		this.model = ModelFactory.createDefaultModel();
		this.property = new HashMap<String, Property>();
		
		this.len = peerURI.length()+BOOKMARK.length();
		this.resourceURI = new StringBuilder(len+20);
		this.resourceURI.append(peerURI);
		this.resourceURI.append(BOOKMARK);
		
		model.setNsPrefix(Rdf.PREFIX, Rdf.IDENTIFIER);
		model.setNsPrefix(DCElements.PREFIX, DCElements.IDENTIFIER);
		model.setNsPrefix(AnnoteaA.PREFIX, AnnoteaA.NAMESPACE);
		model.setNsPrefix(AnnoteaB.PREFIX, AnnoteaB.NAMESPACE);
		
		this.property.put(YMarkEntry.BOOKMARK.URL.key(), this.model.createProperty(AnnoteaB.recalls.getNamespace(), AnnoteaB.recalls.name()));
		this.property.put(YMarkEntry.BOOKMARK.FOLDERS.key(), this.model.createProperty(AnnoteaB.hasTopic.getNamespace(), AnnoteaB.hasTopic.name()));
		this.property.put(YMarkEntry.BOOKMARK.TITLE.key(), this.model.createProperty(DCElements.title.getNamespace(), DCElements.title.name()));
		this.property.put(YMarkEntry.BOOKMARK.DESC.key(), this.model.createProperty(DCElements.description.getNamespace(), DCElements.description.name()));
		this.property.put(YMarkEntry.BOOKMARK.DATE_ADDED.key(), this.model.createProperty(AnnoteaA.created.getNamespace(), AnnoteaA.created.name()));
		this.property.put(YMarkEntry.BOOKMARK.DATE_MODIFIED.key(), this.model.createProperty(DCElements.date.getNamespace(), DCElements.date.name()));
		this.property.put(YMarkEntry.BOOKMARK.TAGS.key(), this.model.createProperty(DCElements.subject.getNamespace(), DCElements.subject.name()));
		
		this.property.put(USER, this.model.createProperty(DCElements.creator.getNamespace(), DCElements.creator.name()));
		this.property.put(TYPE, this.model.createProperty(Rdf.type.getNamespace(), Rdf.type.name()));
		this.property.put(SUBTOPIC, this.model.createProperty(AnnoteaB.subTopicOf.getNamespace(), AnnoteaB.subTopicOf.name()));
	}
	
	/**
	 * @param format {RDF/XML, RDF/XML-ABBREV, N-TRIPLE, N3, N3-PP, N3-PLAIN, N3-TRIPLE, TURTLE}
	 * @return RDF
	 */
	public String getRDF(final String format) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		this.model.write(baos, format);
		try {
			return baos.toString("UTF-8");
		} catch (final UnsupportedEncodingException e) {
			return new String();
		}
	}
	
	public void addTopic(final String bmk_user, final String folder) {
		this.resourceURI.append(bmk_user);
		this.resourceURI.append(":f:");
		this.resourceURI.append(UTF8.String(YMarkUtil.getKeyId(folder)));
		final Resource topic = this.model.createResource(this.resourceURI.toString());
		this.resourceURI.setLength(this.len);
		
		topic.addProperty(this.property.get(YMarkEntry.BOOKMARK.DATE_MODIFIED.key()), YMarkUtil.EMPTY_STRING);
		topic.addProperty(this.property.get(YMarkEntry.BOOKMARK.DATE_ADDED.key()), YMarkUtil.EMPTY_STRING);
		topic.addProperty(this.property.get(USER), bmk_user);
		topic.addProperty(this.property.get(YMarkEntry.BOOKMARK.DESC.key()), YMarkUtil.EMPTY_STRING);
		final int i = folder.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR);
		if(i>0)
			topic.addProperty(this.property.get(SUBTOPIC), folder.substring(0, i));
		topic.addProperty(this.property.get(YMarkEntry.BOOKMARK.TITLE.key()), folder);
		topic.addProperty(this.property.get(TYPE), AnnoteaB.Topic.getPredicate());
	}
	
	public void addBookmark (final String bmk_user, final Tables.Row bmk_row) {
		if(bmk_row == null || bmk_row.get(YMarkEntry.BOOKMARK.PUBLIC.key(), YMarkEntry.BOOKMARK.PUBLIC.deflt()).equals("false"))
			return;				
		// create an annotea bookmark resource
		this.resourceURI.append(bmk_user);
		this.resourceURI.append(":b:");
		this.resourceURI.append(UTF8.String(bmk_row.getPK()));
		final Resource bmk = this.model.createResource(this.resourceURI.toString());
		this.resourceURI.setLength(this.len);

		// add properties			
		bmk.addProperty(this.property.get(USER), bmk_user);
		for (final YMarkEntry.BOOKMARK b : YMarkEntry.BOOKMARK.values()) {        	
			switch(b) {
        		case FOLDERS:    					
					final String[] folders = bmk_row.get(b.key(), b.deflt()).split(b.seperator());
					if(folders.length > 1) {
						Bag topics = this.model.createBag();		
						for(String folder : folders) {
							topics.add(folder);
							this.addTopic(bmk_user, folder);
						}
						bmk.addProperty(this.property.get(b.key()), topics);					
					} else {
						bmk.addProperty(this.property.get(b.key()), folders[0]);
						this.addTopic(bmk_user, folders[0]);
					}
					break;
				case TAGS:
					final String[] tags = bmk_row.get(b.key(), b.deflt()).split(b.seperator());
					if(tags.length > 1) {
						Bag subjects = this.model.createBag();					
						for(String tag : tags) {
							subjects.add(tag);
						}					
						bmk.addProperty(this.property.get(b.key()), subjects);
					} else {
						bmk.addProperty(this.property.get(b.key()), tags[0]);	
					}
					break;
					
				case DATE_ADDED:
				case DATE_MODIFIED:
					final YMarkDate date = new YMarkDate(bmk_row.get(b.key()));
					bmk.addProperty(this.property.get(b.key()), date.toISO8601());
					break;
				// these cases are inserted for better readable RDF output
				case DESC:
				case URL:
	        	case TITLE:
	        		bmk.addProperty(this.property.get(b.key()), bmk_row.get(b.key(), b.deflt()));
	        		break;
				default:
					if(this.property.containsKey(b.key())) {
						bmk.addProperty(this.property.get(b.key()), bmk_row.get(b.key(), b.deflt()));
					}
        	}
        }
		bmk.addProperty(this.property.get(TYPE), AnnoteaB.Bookmark.getPredicate());		
	}

	public void addBookmarks(final String bmk_user, final Iterator<Tables.Row> riter) {
		while(riter.hasNext()) {
			this.addBookmark(bmk_user, riter.next());
		}
	}
}
