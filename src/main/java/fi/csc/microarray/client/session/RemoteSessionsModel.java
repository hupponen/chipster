package fi.csc.microarray.client.session;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import fi.csc.microarray.filebroker.DbSession;


public class RemoteSessionsModel {

	private List<DbSession> sessions;
	private String currentDirectory = "";
	private RemoteSessionsController listener;

	public List<DbSession> getSessions() {
		
		ArrayList<DbSession> folder = new ArrayList<>();
		TreeSet<String> subdirectories = new TreeSet<>();
		for (DbSession session : sessions) {
			if (session.getName().startsWith(currentDirectory)) {
				String relativePath = session.getName().substring(currentDirectory.length());				
				if (relativePath.contains("/")) {
					String firstDir = relativePath.substring(0, relativePath.indexOf("/") + 1);
					subdirectories.add(firstDir);					
				} else {
					folder.add(session);
				}
			}
		}
		
		for (String subdir : subdirectories) {
			folder.add(0, new DbSession(null, subdir, null));
		}
		
		return folder;
	}

	public int getDiskUsage() {
		return 500_000_000;
	}

	public int getMaxQuota() {
		return 1_000_000_000;
	}

	public void setSessions(List<DbSession> sessions) {
		this.sessions = sessions;
		listener.modelChanged();
	}

	public void setListener(RemoteSessionsController listener) {
		this.listener = listener;
	}

	public void removeSession(DbSession session) {
		sessions.remove(session);
		listener.modelChanged();
	}

	public void setCurrentDirectory(String dir) {
		this.currentDirectory = dir;
		listener.modelChanged();
	}
}
