package fi.csc.microarray.client.session;

import fi.csc.microarray.filebroker.DbSession;

public interface SessionChangeListener {
	public void sessionRemoved(DbSession session);
}
