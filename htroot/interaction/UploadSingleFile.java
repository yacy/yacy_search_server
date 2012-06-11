package interaction;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.yacy.yacy;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.interaction.Interaction;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import de.anomic.data.UserDB;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;


public class UploadSingleFile {

	public static serverObjects respond(final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		final serverObjects prop = new serverObjects();




		if (post != null){


		if (post.containsKey("uploadfile") && !post.get("uploadfile").isEmpty()) {

			UserDB.Entry entry = sb.userDB.getEntry(Interaction.GetLoggedOnUser(header));

            if (entry != null) {

                    if (entry.hasRight(UserDB.AccessRight.UPLOAD_RIGHT)) {

                    	// the user has the upload right

                    }

            }

			String targetfilename = post.get("uploadfile", "target.file");

			String targetfolder = "/upload/"+Interaction.GetLoggedOnUser(header);

			if (post.containsKey("targetfilename")) {
				targetfilename = post.get("targetfilename");

			}

			if (post.containsKey("targetfolder")) {
				targetfolder = post.get("targetfolder");

				if (!targetfolder.startsWith("/")) {
					targetfolder = "/" + targetfolder;
				}

			}

			File f = new File(yacy.dataHome_g, "DATA/HTDOCS"+targetfolder+"/");

			yacy.mkdirsIfNeseccary (f);

			f = new File(f, targetfilename);

			Log.logInfo ("FILEUPLOAD", f.toString());



			try {

				ByteArrayInputStream stream = new ByteArrayInputStream(post
						.get("uploadfile$file").getBytes());


				if (stream != null) {

				OutputStream out;


				out = new FileOutputStream(f.toString());


				byte[] buf = new byte[1024];
				int len;
				while ((len = stream.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				stream.close();
				out.close();
				}

			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}



		}
		}

		// return rewrite properties
		return prop;
	}



}









