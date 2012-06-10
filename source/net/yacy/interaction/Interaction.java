package net.yacy.interaction;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;

import org.apache.http.entity.mime.content.ContentBody;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;


public class Interaction {

//	public static String GetInteractionData (String url) {
//
//
//		// Fetch information from external sciencenet server
//
//		// TODO: Use internal database
//
//        Log.logInfo("INTERACTION", "GetInteractionData: "+url);
//		try {
//			return (UTF8.String(new HTTPClient().GETbytes("http://sciencenet.kit.edu/GetDomainInfoJSON?DomainURL="+url)));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			return "";
//		}
//
//	}


	public static String GetDomain (String url) {

		String domain = url;

		try {
			DigestURI uri = new DigestURI (url);

			domain = uri.getHost();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return domain;

	}

//	public static boolean IsInBookmarks (String domain) {
//
//
//		// TODO: Check if this bookmark exists
//
//		Boolean result = false;
//
//		DigestURI uri;
//		try {
//			uri = new DigestURI (domain);
//
//			Bookmark b = Switchboard.getSwitchboard().bookmarksDB.getBookmark(UTF8.String(uri.hash()));
//
//			if (!(b == null)) {
//				result = true;
//			}
//
//
//		} catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		return result;
//
//	}

//	public static boolean SaveDomainVote (String domain, String vote) {
//
//
//		// TODO: Check if this bookmark exists
//
//		Boolean result = false;
//
//		DigestURI uri;
//		try {
//			uri = new DigestURI (domain);
//
//			Bookmark b = Switchboard.getSwitchboard().bookmarksDB.getBookmark(UTF8.String(uri.hash()));
//
//			if (!(b == null)) {
//				b.addTag(vote);
//				Switchboard.getSwitchboard().bookmarksDB.saveBookmark(b);
//			} else {
//				Bookmark b2 = Switchboard.getSwitchboard().bookmarksDB.createBookmark(domain, "admin");
//
//				b2.addTag(vote);
//
//				Switchboard.getSwitchboard().bookmarksDB.saveBookmark(b2);
//			}
//
//
//		} catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		return result;
//
//	}

//	public static boolean DomainWhite (String domain, String username) {
//
//
//		// Add userinteraction
//
//		Boolean result = false;
//
////		Bookmark b = Interaction.Suggest(domain, username);
////
////		//
//
//		return result;
//
//	}

//	public static Usercontribution DoUsercontribution(String domain, String uc,
//			String username) {
//
//		final Switchboard sb = Switchboard.getSwitchboard();
//
//		Boolean existing = false;
//
//		if (username == "") {
//			username = "anonymous";
//		}
//
//		Usercontribution result = null;
//
//		if (!existing) {
//
//			Boolean reject = false;
//
//
//
//
//			// count elements
//			Iterator<String> it = sb.usercontributionsDB.getFeedbackitemsIterator(true);
//
//			int count = 0;
//			while(it.hasNext()) {
//				it.next();
//				count++;
//			}
//
//
//			if (count > 500) {
//				if (username.equals("crawlbot")) {
//					reject = true;
//				}
//
//			}
//
//			if (!reject) {
//
//			try {
//
//				Usercontribution new_uc = Switchboard.getSwitchboard().usercontributionsDB
//						.createUsercontribution(UUID.randomUUID().toString(),
//								username);
//				if (username.equals("anonymous")) {
//					new_uc.setPublic(true);
//				} else {
//					new_uc.setPublic(false);
//				}
//
//				new_uc.setProperty(Usercontribution.feedbackitem_TITLE, username);
//				new_uc.setProperty(Usercontribution.feedbackitem_DESCRIPTION, uc);
//				new_uc.setProperty(Usercontribution.feedbackitem_TARGET, new DigestURI(domain).toNormalform(false, false));
//
//				Switchboard.getSwitchboard().usercontributionsDB
//						.saveUsercontribution(new_uc);
//
//				result = new_uc;
//
//			} catch (MalformedURLException e) {
//
//			}
//			}
//
//		}
//
//		return result;
//
//	}



//public static String GetURLHash (String url) {
//
//
//		// TODO: Check if this bookmark exists
//
//		String result = "";
//
//		DigestURI uri;
//		try {
//			uri = new DigestURI (url);
//
//			result = UTF8.String(uri.hash());
//
//
//
//		} catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		return result;
//
//	}

	public static String Suggest (String url, String username) {

		final Switchboard sb = Switchboard.getSwitchboard();

		if (username == "") {
			username = "anonymous";
		}

		Boolean processlocal = false;

		if (!sb.getConfig("interaction.suggest.accumulationpeer", "").equals("")) {
			if (sb.getConfig("interaction.suggest.accumulationpeer", "").equals(sb.peers.myName())) {

				// Our peer is meant to process the suggestion.
				processlocal = true;


			} else {

				// Forward suggestion to other peer
				Log.logInfo("INTERACTION", "Forwarding suggestion to "+sb.getConfig("interaction.suggest.accumulationpeer", "")+": " + url);
				try {

					Seed host = sb.peers.lookupByName(sb.getConfig("interaction.suggest.accumulationpeer", ""));

					return (UTF8.String(new HTTPClient().POSTbytes(
							"http://"+host.getPublicAddress()+"/interaction/Suggest.json"
									+ "?url=" + url + "&username=" + username,
							new HashMap<String, ContentBody>(), false)));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		if (processlocal) {

			final String date = String.valueOf(System.currentTimeMillis());

            final Map<String, byte[]> map = new HashMap<String, byte[]>();

            map.put("url", url.getBytes());
            map.put("username", username.getBytes());
            map.put("status", "new".getBytes());
            map.put("timestamp_creation", date.getBytes());

            try {
                sb.tables.insert("suggestion", map);
            } catch (final IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            // TODO: Remove the following part for future use

			Log.logInfo("INTERACTION", "Forwarding suggestion to bk: " + url);

			try {

				String reply = (UTF8.String(new HTTPClient().POSTbytes(
						"http://hwiki.fzk.de/jss/SiteBookmark?suggestUrl=" + url+"&username="+username,
						new HashMap<String, ContentBody>(), false)));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return "";

	}


public static String Feedback(String url, String comment, String from, String peer) {

		final Switchboard sb = Switchboard.getSwitchboard();

		if (peer == "") {
			peer = sb.peers.myName();
		}

		Boolean processlocal = false;

		if (!sb.getConfig("interaction.feedback.accumulationpeer", "").equals("")) {
			if (sb.getConfig("interaction.feedback.accumulationpeer", "").equals(sb.peers.myName())) {

				// Our peer is meant to process the feedback.
				processlocal = true;

			} else {

				// Forward feedback to other peer
				Log.logInfo("INTERACTION", "Forwarding feedback to "+sb.getConfig("interaction.feedback.accumulationpeer", "")+": " + url + ": "
						+ comment);
				try {

					Seed host = sb.peers.lookupByName(sb.getConfig("interaction.feedback.accumulationpeer", ""));

					return (UTF8.String(new HTTPClient().POSTbytes(
							"http://"+host.getPublicAddress()+"/interaction/Feedback.json"
									+ "?url=" + url + "&comment=" + comment
									+ "&from=" + from + "&peer=" + peer,
							new HashMap<String, ContentBody>(), false)));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return "";
				}
			}
		}

		if (processlocal) {

			final String date = String.valueOf(System.currentTimeMillis());

			final Map<String, byte[]> map = new HashMap<String, byte[]>();

            map.put("url", url.getBytes());
            map.put("username", from.getBytes());
            map.put("peer", peer.getBytes());
            map.put("status", "new".getBytes());
            map.put("comment", comment.getBytes());
            map.put("timestamp_creation", date.getBytes());

            try {
                sb.tables.insert("feedback", map);
            } catch (final IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            // TODO: Remove the following part for future use

			try {
				return (UTF8.String(new HTTPClient().POSTbytes(
						"http://sciencenet.kit.edu/Feedback?Url=" + url + "&Comment=" + comment
								+ "&From=" + from + "&Peer=" + peer,
						new HashMap<String, ContentBody>(), false)));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "";
			}
		}

		return "";
}

public static String Contribution(String url, String comment, String from, String peer) {

	final Switchboard sb = Switchboard.getSwitchboard();

	if (peer == "") {
		peer = sb.peers.myName();
	}

	Boolean processlocal = false;

	if (!sb.getConfig("interaction.contribution.accumulationpeer", "").equals("")) {
		if (sb.getConfig("interaction.contribution.accumulationpeer", "").equals(sb.peers.myName())) {

			// Our peer is meant to process the feedback.
			processlocal = true;

		} else {

			// Forward feedback to other peer
			Log.logInfo("INTERACTION", "Forwarding contribution to "+sb.getConfig("interaction.contribution.accumulationpeer", "")+": " + url + ": "
					+ comment);
			try {

				Seed host = sb.peers.lookupByName(sb.getConfig("interaction.contribution.accumulationpeer", ""));

				return (UTF8.String(new HTTPClient().POSTbytes(
						"http://"+host.getPublicAddress()+"/interaction/Contribution.json"
								+ "?url=" + url + "&comment=" + comment
								+ "&from=" + from + "&peer=" + peer,
						new HashMap<String, ContentBody>(), false)));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "";
			}
		}
	} else {
		// No forward defined
		processlocal = true;
	}

	if (processlocal) {

		final String date = String.valueOf(System.currentTimeMillis());

		final Map<String, byte[]> map = new HashMap<String, byte[]>();

        map.put("url", url.getBytes());
        map.put("username", from.getBytes());
        map.put("peer", peer.getBytes());
        map.put("status", "new".getBytes());
        map.put("comment", comment.getBytes());
        map.put("timestamp_creation", date.getBytes());

        try {
            sb.tables.insert("contribution", map);
        } catch (final IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	return "";
}

public static String Triple(String url, String s, String p, String o, String from) {

	final Switchboard sb = Switchboard.getSwitchboard();

	Resource r = TripleStore.model.getResource(s);
	Property pr = TripleStore.model.createProperty(p);

	r.addProperty(pr, o);

	Log.logInfo ("TRIPLESTORE", "PUT "+s+"-"+p+"-"+o);

	return "";
}

public static String TripleGet(String s, String p) {

	final Switchboard sb = Switchboard.getSwitchboard();

	Resource r = TripleStore.model.getResource(s);
	Property pr = TripleStore.model.getProperty(p);

	StmtIterator iter = TripleStore.model.listStatements(r, pr, (Resource) null);

	Log.logInfo ("TRIPLESTORE", "GET "+s+" - "+p);

	while (iter.hasNext()) {

		return (iter.nextStatement().getObject().toString());
	}

	return "";

}

public static String GetContribution(String url) {

	final Switchboard sb = Switchboard.getSwitchboard();


//	Boolean processlocal = false;
//
//	if (!sb.getConfig("interaction.contribution.accumulationpeer", "").equals("")) {
//
//		if (sb.getConfig("interaction.contribution.accumulationpeer", "").equals(sb.peers.myName())) {
//
//			// Our peer is meant to process the feedback.
//			processlocal = true;
//
//		} else {
//
//			// Forward feedback to other peer
//			Log.logInfo("INTERACTION", "Fetching contribution from "+sb.getConfig("interaction.contribution.accumulationpeer", "")+": " + url);
//
//			try {
//
//				Seed host = sb.peers.lookupByName(sb.getConfig("interaction.contribution.accumulationpeer", ""));
//
//				return (UTF8.String(new HTTPClient().POSTbytes(
//						"http://"+host.getPublicAddress()+"/interaction/Contribution.json"
//								+ "?url=" + url + "&comment=" + comment
//								+ "&from=" + from + "&peer=" + peer,
//						new HashMap<String, ContentBody>(), false)));
//
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//
//				return "";
//			}
//		}
//	} else {
//		// No forward defined
//		processlocal = true;
//	}
//
//	if (processlocal) {
//
//		final String date = String.valueOf(System.currentTimeMillis());
//
//		final Map<String, byte[]> map = new HashMap<String, byte[]>();
//
//        map.put("url", url.getBytes());
//        map.put("username", from.getBytes());
//        map.put("peer", peer.getBytes());
//        map.put("status", "new".getBytes());
//        map.put("comment", comment.getBytes());
//        map.put("timestamp_creation", date.getBytes());
//
//        try {
//            sb.tables.insert("contribution", map);
//        } catch (final IOException e) {
//            Log.logException(e);
//        } catch (RowSpaceExceededException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

	return "";
}


//public static void Usertracking(String url) {
//
//	final Switchboard sb = Switchboard.getSwitchboard();
//
//	Log.logInfo("INTERACTION", "Usertracking "+url);
//
//
//	// TOUCH: HIT +1 TO THE DOMAIN ENTRY
//
//	try {
//		sb.addToIndex(new DigestURI(new DigestURI(url).getHost()), null, null);
//		sb.addToIndex(new DigestURI(url), null, null);
//	} catch (MalformedURLException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	} catch (Failure e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//
//}


}

