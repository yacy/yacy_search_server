package net.yacy.interaction.contentcontrol;

import java.io.IOException;
import java.util.Iterator;

import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.repository.FilterEngine;
import net.yacy.search.Switchboard;

public class ContentControlFilterUpdateThread {

	private final Switchboard sb;

	private Boolean locked = false;

	private static FilterEngine networkfilter;

	public ContentControlFilterUpdateThread(final Switchboard sb) {
        //final long time = System.currentTimeMillis();
        this.sb = sb;

		if (this.sb.getConfigBool("contentcontrol.smwimport.purgelistoninit",
				false)) {
			this.sb.tables.clear(this.sb.getConfig(
					"contentcontrol.smwimport.targetlist", "contentcontrol"));

		}
    }

	public final void run() {

		if (!this.locked) {

			this.locked = true;

			if (this.sb.getConfigBool("contentcontrol.enabled", false) == true) {

				if (!this.sb
						.getConfig("contentcontrol.mandatoryfilterlist", "")
						.equals("")) {

					if (this.sb.tables.bookmarks.dirty) {

						networkfilter = updateFilter();

						this.sb.tables.bookmarks.dirty = false;

					}

				}

			}

			this.locked = false;

		}


		return;
	}

	private static FilterEngine updateFilter () {

		FilterEngine newfilter = new FilterEngine();

		Switchboard sb = Switchboard.getSwitchboard();

		Iterator<Tables.Row> it;
		try {
			it = sb.tables.bookmarks.getBookmarksByTag(
					sb.getConfig(
							"contentcontrol.bookmarklist",
							"contentcontrol"),
					"^((?!sc:"
							+ sb
									.getConfig(
											"contentcontrol.mandatoryfilterlist",
											"") + ").*)$");
			while (it.hasNext()) {
				Row b = it.next();

				if (!b.get("filter", "").equals("")) {

					newfilter.add(b.get("filter", ""), null);
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return newfilter;
	}


	public static FilterEngine getNetworkFilter() {
		FilterEngine f = networkfilter;

		if (f != null && f.size() > 0)
			return f;

		return null;

	}

}
