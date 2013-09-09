package net.yacy.contentcontrol;

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

        this.sb = sb;

    }

	public final void run() {

		if (!this.locked) {

			this.locked = true;

			if (this.sb.getConfigBool("contentcontrol.enabled", false) == true) {

				if (SMWListSyncThread.dirty) {

					networkfilter = updateFilter();

					SMWListSyncThread.dirty = false;

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
			it = sb.tables.iterator(sb.getConfig("contentcontrol.bookmarklist",
					"contentcontrol"));

			while (it.hasNext()) {
				Row b = it.next();

				if (!b.get("filter", "").equals("")) {

					newfilter.add(b.get("filter", ""), null);
				}
			}

		} catch (final IOException e) {
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
